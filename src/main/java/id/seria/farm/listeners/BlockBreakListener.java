package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

public class BlockBreakListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public BlockBreakListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if this block is managed by SeriaFarm
        ConfigurationSection config = findBlockConfig(block);
        if (config == null) return;

        // WORLDGUARD BYPASS LOGIC
        // If the event was cancelled by another plugin (like WG), we uncancel it if it's our block.
        if (event.isCancelled()) {
            event.setCancelled(false);
        }

        // REQUIREMENT CHECK
        if (!plugin.getRequirementEngine().canBreak(player, config)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission-to-break"));
            return;
        }

        // HANDLE REGENERATION (UBR STYLE)
        int delay = config.getInt("regen-delay", 10);
        java.util.List<String> replaceBlocks = config.getStringList("replace-blocks");
        plugin.getRegenManager().scheduleRegeneration(block, delay, replaceBlocks);
        
        // Disable natural drops if we want custom drops
        event.setDropItems(false);
        distributeRewards(player, config);
    }

    private ConfigurationSection findBlockConfig(Block block) {
        String matName = block.getType().name();
        String regionName = plugin.getRegenManager().getRegionAt(block.getLocation());
        ConfigurationSection rootBlocks = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks");
        if (rootBlocks == null) return null;

        // 1. Check current region
        if (regionName != null) {
            ConfigurationSection regionSection = rootBlocks.getConfigurationSection(regionName);
            if (regionSection != null) {
                for (String key : regionSection.getKeys(false)) {
                    if (regionSection.getString(key + ".material", "").equalsIgnoreCase(matName)) {
                        return regionSection.getConfigurationSection(key);
                    }
                }
            }
        }

        // 2. Check global section
        ConfigurationSection globalSection = rootBlocks.getConfigurationSection("global");
        if (globalSection != null) {
            for (String key : globalSection.getKeys(false)) {
                if (globalSection.getString(key + ".material", "").equalsIgnoreCase(matName)) {
                    return globalSection.getConfigurationSection(key);
                }
            }
        }

        // 3. Fallback to legacy root-level blocks
        for (String key : rootBlocks.getKeys(false)) {
            if (rootBlocks.isConfigurationSection(key)) {
                ConfigurationSection sec = rootBlocks.getConfigurationSection(key);
                if (sec.getString("material", "").equalsIgnoreCase(matName)) {
                    return sec;
                }
            }
        }
        return null;
    }

    private void distributeRewards(Player player, ConfigurationSection config) {
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards != null) {
            // XP distribution
            int xp = rewards.getInt("xp", 0);
            if (xp > 0) player.giveExp(xp);
            
            // Command execution
            for (String cmd : rewards.getStringList("commands")) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }
        }
    }
}
