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
        plugin.getRegenManager().scheduleRegeneration(block, delay);
        
        // Disable natural drops if we want custom drops
        event.setDropItems(false);
        distributeRewards(player, config);
    }

    private ConfigurationSection findBlockConfig(Block block) {
        String matName = block.getType().name();
        ConfigurationSection section = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (section.getString(key + ".material", "").equalsIgnoreCase(matName)) {
                    return section.getConfigurationSection(key);
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
