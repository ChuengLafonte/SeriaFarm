package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class InteractListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public InteractListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 0. Check if plugin is globally enabled
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.enabled", true)) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        Player player = event.getPlayer();
        
        // 1. Check if this is a managed crop/block
        ConfigurationSection config = findBlockConfig(block);
        if (config == null) return;

        // 2. GROWTH STAGE VALIDATION
        // 1. Prevent harvesting blocks that are already regenerating
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            id.seria.farm.models.RegenBlock regen = plugin.getRegenManager().getRegenBlock(block.getLocation());
            if (regen != null && regen.isGrowth()) {
                plugin.getVisualManager().showGrowthInfo(player, regen);
            }
            event.setCancelled(true);
            return;
        }

        if (!isFullyGrown(block)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-fully-grown"));
            event.setCancelled(true);
            return;
        }

        // 3. REQUIREMENT CHECK
        if (!plugin.getRequirementEngine().canBreak(player, config)) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission-to-break"));
            return;
        }

        // 4. TRIGGER HARVEST
        handleHarvest(player, block, config);
        
        // 5. AURASKILLS XP SYNC
        plugin.getAuraSkillsManager().giveXP(player, block.getType());
        
        event.setCancelled(true);
    }

    private boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();
        
        // Handle standard crops (Wheat, Carrots, etc.)
        if (data instanceof Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        
        // Handle Amethyst
        if (block.getType().name().contains("AMETHYST")) {
            return block.getType() == Material.AMETHYST_CLUSTER;
        }
        
        // Default to true for other blocks (like melons/pumpkins which are single-stage once placed)
        return true;
    }

    private void handleHarvest(Player player, Block block, ConfigurationSection config) {
        // 1. Distribute Rewards
        distributeRewards(player, block, config);

        // 2. Schedule Regeneration
        int delay = config.getInt("regen-delay", 10);
        List<String> replaceBlocks = config.getStringList("replace-blocks");
        List<String> delayBlocks = config.getStringList("delay-blocks");
        
        // Determine fallback material based on Growth Mode
        String growthMode = plugin.getConfigManager().getConfig("config.yml").getString("settings.crop-growth-mode", "INSTANT");
        Material fallbackMat = block.getType(); // Default to mature
        
        if (growthMode.equalsIgnoreCase("VANILLA")) {
            // For vanilla mode, we want it to reappear as a seedling (Age 0)
            // But scheduleRegeneration just takes a Material.
            // We need to handle block data properly in RegenManager later.
            // For now, if it's vanilla, we'll try to find the seedling material or handled by RegenManager.
        }

        plugin.getRegenManager().scheduleRegeneration(block, delay, replaceBlocks, delayBlocks, fallbackMat);
    }

    private void distributeRewards(Player player, Block block, ConfigurationSection config) {
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards == null) return;

        // XP
        int xp = rewards.getInt("xp", 0);
        if (xp > 0) player.giveExp(xp);

        // Commands
        for (String cmd : rewards.getStringList("commands")) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }

        // Item Drops
        List<?> drops = rewards.getList("drops");
        if (drops != null) {
            boolean dropToInv = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.drop-to-inventory", false);
            Random random = new Random();
            
            for (Object obj : drops) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    ItemStack item = (ItemStack) map.get("item");
                    double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                    
                    if (item != null && (random.nextDouble() * 100.0) <= chance) {
                        ItemStack dropItem = item.clone();
                        if (dropToInv) {
                            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(dropItem);
                            if (!remaining.isEmpty()) {
                                for (ItemStack left : remaining.values()) {
                                    block.getWorld().dropItemNaturally(block.getLocation(), left);
                                }
                            }
                        } else {
                            block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
                        }
                    }
                }
            }
        }
    }

    private ConfigurationSection findBlockConfig(Block block) {
        String matName = block.getType().name();
        String regionName = plugin.getRegenManager().getRegionAt(block.getLocation());
        ConfigurationSection rootBlocks = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks");
        if (rootBlocks == null) return null;

        // 1. Check current region (Priority 1)
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

        // 2. Check global section (Priority 2)
        ConfigurationSection globalSection = rootBlocks.getConfigurationSection("global");
        if (globalSection != null) {
            for (String key : globalSection.getKeys(false)) {
                if (globalSection.getString(key + ".material", "").equalsIgnoreCase(matName)) {
                    return globalSection.getConfigurationSection(key);
                }
            }
        }
        return null;
    }
}
