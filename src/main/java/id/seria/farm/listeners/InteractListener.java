package id.seria.farm.listeners;
 
import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.models.RegenBlock;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class InteractListener implements Listener {

    private final SeriaFarmPlugin plugin;
    private final Random random = new Random();
 
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
  
        // REDIRECT TO ROOT FOR VERTICAL CROPS
        if (block.getType() == Material.BAMBOO || block.getType() == Material.SUGAR_CANE || block.getType() == Material.CACTUS || block.getType() == Material.WHEAT) {
            block = plugin.getRegenManager().getVerticalRoot(block);
        }
 
        // 1. REGENERATION CHECK (Notification Bug Fix)
        // Check this FIRST so growth info works for both global and regional blocks
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            RegenBlock regen = plugin.getRegenManager().getRegenBlock(block.getLocation());
            
            // ADMIN DEBUG & BYPASS
            if (player.hasPermission("seriafarm.admin") || player.isOp()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.BONE_MEAL) {
                    // Allow admin to use bonemeal (bypass cancel)
                    return; 
                }
            }
 
            if (regen != null && regen.isGrowth()) {
                plugin.getVisualManager().showGrowthInfo(player, regen);
            }
            event.setCancelled(true);
            return;
        }
 
        // 1.5 ADMIN HARVEST DEBUG
        
        
        // 2. Check if this is a managed crop/block
        String blockKey = findBlockKey(player, block);
        
        // 2.0. AD-HOC TRACKING (Handle growing blocks not in map)
        if (blockKey != null && !isFullyGrown(block)) {
            // For vertical crops, we only track the ROOT.
            Block root = block;
            if (isVerticalCrop(block.getType())) {
                root = plugin.getRegenManager().getVerticalRoot(block);
            }
            
            if (!plugin.getRegenManager().isRegenerating(root.getLocation())) {
                plugin.getRegenManager().startAdHocTracking(root, blockKey);
            }
            
            // Show growth info from either the block or its root
            RegenBlock regen = plugin.getRegenManager().getRegenBlock(root.getLocation());
            if (regen != null && regen.isGrowth()) {
                plugin.getVisualManager().showGrowthInfo(player, regen);
                event.setCancelled(true);
                return;
            }
        }
 
        if (blockKey == null) return;
        
        // 2.1 Regional blocks: Disable right-click harvesting
        // Only left-click is allowed for regional blocks.
        if (!blockKey.startsWith("global.")) return;
        
        // 2.2 Global blocks: Check if harvest is enabled via toggle
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.global-right-click-harvest", true)) {
            return;
        }
 
        // 2.3 Check growth stage
        ConfigurationSection config = getBlockConfig(block, blockKey);
        if (config == null) return;
        
        if (!isFullyGrown(block)) {
            event.setCancelled(true);
            return;
        }
 
        // 3. Right-Click Harvest
        event.setCancelled(true);
        handleHarvest(player, block, config, blockKey);
    }
 
    private boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        if (block.getType().name().contains("AMETHYST")) {
            return block.getType() == Material.AMETHYST_CLUSTER;
        }
        if (block.getType() == Material.BAMBOO || block.getType() == Material.SUGAR_CANE || block.getType() == Material.CACTUS) {
            String key = plugin.getRegenManager().findBlockKey(block, null);
            if (key != null) {
                Block root = plugin.getRegenManager().getVerticalRoot(block);
                int current = plugin.getRegenManager().getVerticalHeight(root, block.getType());
                
                String configKey = block.getType() == Material.BAMBOO ? "bamboo-max-height" : "growth-max-height";
                int def = block.getType() == Material.BAMBOO ? 12 : 3;
                int max = plugin.getConfigManager().getConfig("crops.yml").getInt("crops." + key + "." + configKey, def);
                
                return current >= max;
            }
        }
        return true;
    }
 
    private void handleHarvest(Player player, Block block, ConfigurationSection config, String blockKey) {
        distributeRewards(player, block, config);
        int delay = config.getInt("regen-delay", 10);
        List<String> replaceBlocks = config.getStringList("replace-blocks");
        List<String> delayBlocks = config.getStringList("delay-blocks");
        Material fallbackMat = block.getType();
        plugin.getRegenManager().scheduleRegeneration(block, delay, replaceBlocks, delayBlocks, fallbackMat, blockKey);
    }
 
    private void distributeRewards(Player player, Block block, ConfigurationSection config) {
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards == null) return;
 
        int xp = rewards.getInt("xp", 0);
        if (xp > 0) player.giveExp(xp);
 
        for (String cmd : rewards.getStringList("commands")) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }
 
        List<?> drops = rewards.getList("drops");
        if (drops != null) {
            boolean dropToInv = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.drop-to-inventory", false);
            int playerLevel = plugin.getAuraSkillsManager().getFarmingLevel(player);
            double globalScaling = plugin.getConfigManager().getConfig("config.yml").getDouble("settings.global-level-scaling", 0.1);

            boolean hasCommonDrop = false;
            java.util.List<java.util.Map<?, ?>> weightedPool = new java.util.ArrayList<>();

            for (Object obj : drops) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    try {
                        ItemStack item = (ItemStack) map.get("item");
                        if (item == null) continue;

                        // 1. Level Requirement Check
                        int reqLevel = map.containsKey("farming-level") ? ((Number) map.get("farming-level")).intValue() : 0;
                        if (playerLevel < reqLevel) continue;

                        // 2. Chance Scaling
                        double baseChance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                        double scaling = map.containsKey("level-scaling") ? ((Number) map.get("level-scaling")).doubleValue() : globalScaling;
                        double finalChance = baseChance + (playerLevel - reqLevel) * scaling;

                        if ((random.nextDouble() * 100.0) > finalChance) continue;

                        if (baseChance >= 10.0) hasCommonDrop = true;

                        if (map.containsKey("weight")) {
                            weightedPool.add(map);
                        } else {
                            giveReward(player, block, item.clone(), map, dropToInv);
                        }
                    } catch (Exception e) {}
                }
            }

            // 3. Automatic Vanilla Drop (Trash Item)
            if (!hasCommonDrop && !config.getBoolean("suppress-vanilla-drop", false)) {
                Material vanillaMat = block.getType();
                // Ensure we drop the item form
                vanillaMat = id.seria.farm.inventory.utils.InvUtils.getSingleMaterial(vanillaMat);
                giveReward(player, block, new ItemStack(vanillaMat), new java.util.HashMap<>(), dropToInv);
            }

            if (!weightedPool.isEmpty()) {
                double totalWeight = 0;
                for (java.util.Map<?, ?> map : weightedPool) {
                    totalWeight += getSelectionWeight(map);
                }

                if (totalWeight > 0) {
                    double roll = random.nextDouble() * totalWeight;
                    double count = 0;
                    for (java.util.Map<?, ?> map : weightedPool) {
                        count += getSelectionWeight(map);
                        if (roll <= count) {
                            ItemStack item = (ItemStack) map.get("item");
                            giveReward(player, block, item.clone(), map, dropToInv);
                            break;
                        }
                    }
                }
            }
        }
    }

    private double getSelectionWeight(java.util.Map<?, ?> map) {
        Object w = map.get("weight");
        if (w instanceof Number n) return n.doubleValue();
        String ws = String.valueOf(w);
        if (ws.contains("-")) {
            try {
                String[] parts = ws.split("-");
                return (Double.parseDouble(parts[0]) + Double.parseDouble(parts[1])) / 2.0;
            } catch (Exception e) { return 1.0; }
        }
        try { return Double.parseDouble(ws); } catch (Exception e) { return 1.0; }
    }

    private void giveReward(Player player, Block block, ItemStack dropItem, java.util.Map<?, ?> map, boolean dropToInv) {
        InvUtils.stripTechnicalLore(dropItem);
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
 
    private String findBlockKey(Player player, Block block) {
        return plugin.getRegenManager().findBlockKey(block, player);
    }

    private ConfigurationSection getBlockConfig(Block block, String blockKey) {
        return plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops." + blockKey);
    }

    private boolean isVerticalCrop(Material mat) {
        return mat == Material.BAMBOO || mat == Material.BAMBOO_SAPLING || 
               mat == Material.SUGAR_CANE || mat == Material.WHEAT || 
               mat == Material.CACTUS;
    }
}
