package id.seria.farm.listeners;
 
import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.HashMap;
import java.util.Random;
 
public class BlockBreakListener implements Listener {
 
    private final SeriaFarmPlugin plugin;
 
    public BlockBreakListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }
 
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        // 0. Check if plugin is globally enabled
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.enabled", true)) return;
 
        Block block = event.getBlock();
        Player player = event.getPlayer();
 
        // 1. Check if this block is managed by SeriaFarm (Global or Regional)
        String blockKey = findBlockKey(block);
        if (blockKey == null) return;
        
        ConfigurationSection config = getBlockConfig(block, blockKey);
        if (config == null) return;
 
        // 2. REFINED GLOBAL HANDLING (Left-Click / Break)
        if (blockKey.startsWith("global.")) {
            // Cancel any active growth timer at this location upon break
            plugin.getRegenManager().cancelRegeneration(block.getLocation());
            
            String matStr = config.getString("material", "");
            
            // If it's a Hooked/Custom material (MMOItems, ItemsAdder, etc.)
            if (isCustomMaterial(matStr)) {
                // Intercept and drop the custom source item
                event.setCancelled(true);
                ItemStack source = plugin.getHookManager().getItem(matStr);
                if (source != null && source.getType() != org.bukkit.Material.STONE) {
                    block.getWorld().dropItemNaturally(block.getLocation(), source);
                }
                
                // RESPECT GLOBAL REPLANT
                boolean replant = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.global-replant", true);
                if (replant) {
                    int delay = config.getInt("regen-delay", 10);
                    plugin.getRegenManager().scheduleRegeneration(block, delay, null, null, org.bukkit.Material.AIR, blockKey);
                } else {
                    block.setType(org.bukkit.Material.AIR);
                }
                return;
            } else {
                // Standard vanilla block break
                // If it's a crop and we want replant:
                boolean replant = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.global-replant", true);
                if (replant && isGrowthCapable(block)) {
                    event.setCancelled(true);
                    distributeRewards(player, block, config);
                    int delay = config.getInt("regen-delay", 10);
                    plugin.getRegenManager().scheduleRegeneration(block, delay, null, null, block.getType(), blockKey);
                }
                return;
            }
        }
 
        // --- REGIONAL BLOCK LOGIC ONLY FROM HERE ---
 
        // 1. CANCEL IF REGENERATING (Protection)
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        // AGGRESSIVE PROTECTION FOR BAMBOO STALKS
        // If child block of bamboo is broken, check if root is regenerating
        if (block.getType() == org.bukkit.Material.BAMBOO) {
            org.bukkit.block.Block root = block;
            while (root.getRelative(0, -1, 0).getType() == org.bukkit.Material.BAMBOO) {
                root = root.getRelative(0, -1, 0);
            }
            if (plugin.getRegenManager().isRegenerating(root.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
 
        // 2.2 GROWTH STAGE VALIDATION
        if (!isFullyGrown(block)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-fully-grown"));
            event.setCancelled(true);
            return;
        }
 
        // 3. REQUIREMENT CHECK
        if (!plugin.getRequirementEngine().canBreak(player, config)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission-to-break"));
            return;
        }
 
        // 4. CANCEL EVENT - Plugin takes full control for Regional blocks
        event.setCancelled(true);
 
        // 5. DISTRIBUTE REWARDS & XP
        distributeRewards(player, block, config);
        plugin.getAuraSkillsManager().giveXP(player, block.getType());
        
        // 6. MANAGE TOOL DURABILITY
        handleDurability(player);
        
        // 7. HANDLE DROPS (Smart logic for custom materials in regions)
        String matStr = config.getString("material", "");
        if (isCustomMaterial(matStr)) {
            ItemStack source = plugin.getHookManager().getItem(matStr);
            if (source != null && source.getType() != org.bukkit.Material.STONE) {
                block.getWorld().dropItemNaturally(block.getLocation(), source);
            }
        }
        
        // 8. REGENERATION PARAMETERS
        int delay = config.getInt("regen-delay", 10);
        java.util.List<String> replaceBlocks = config.getStringList("replace-blocks");
        java.util.List<String> delayBlocks = config.getStringList("delay-blocks");
        
        String fallbackMatStr = config.getString("material", block.getType().name());
        org.bukkit.Material fallbackMat = org.bukkit.Material.matchMaterial(fallbackMatStr);
        if (fallbackMat == null) fallbackMat = org.bukkit.Material.WHEAT;

        // 9. BAMBOO SPECIAL HANDLING
        if (block.getType() == org.bukkit.Material.BAMBOO || block.getType() == org.bukkit.Material.BAMBOO_SAPLING) {
            org.bukkit.block.Block root = plugin.getRegenManager().getBambooRoot(block);
            
            if (plugin.getRegenManager().isRegenerating(root.getLocation())) {
                event.setCancelled(true);
                return;
            }

            // Clear stalk upwards (excluding root for a moment to preserve its identity during scheduling)
            org.bukkit.block.Block stalk = root.getRelative(0, 1, 0);
            while (stalk.getType() == org.bukkit.Material.BAMBOO || stalk.getType() == org.bukkit.Material.BAMBOO_SAPLING) {
                stalk.getWorld().dropItemNaturally(stalk.getLocation(), new ItemStack(org.bukkit.Material.BAMBOO));
                stalk.setType(org.bukkit.Material.AIR);
                stalk = stalk.getRelative(0, 1, 0);
            }
            
            // Re-fetch config and key for the ROOT (Important for Regional isolation)
            String rootKey = plugin.getRegenManager().findBlockKey(root, player);
            if (rootKey == null) rootKey = blockKey; // Fallback
            
            plugin.getRegenManager().scheduleRegeneration(root, delay, replaceBlocks, delayBlocks, org.bukkit.Material.BAMBOO, rootKey);
            return;
        }

        // 10. REGIONAL REGENERATION
        plugin.getRegenManager().scheduleRegeneration(block, delay, replaceBlocks, delayBlocks, fallbackMat, blockKey);
    }
 
    private boolean isCustomMaterial(String matStr) {
        if (matStr == null) return false;
        String lower = matStr.toLowerCase();
        return lower.startsWith("mi:") || lower.startsWith("ia:") || lower.startsWith("ox:") || lower.startsWith("nx:");
    }
 
    private void handleDurability(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == org.bukkit.Material.AIR) return;
        
        if (tool.getType().getMaxDurability() <= 0) return;
 
        org.bukkit.inventory.meta.ItemMeta meta = tool.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
            int unbreakingLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
            if (unbreakingLevel > 0) {
                if (new Random().nextInt(unbreakingLevel + 1) != 0) return;
            }
            
            damageable.setDamage(damageable.getDamage() + 1);
            tool.setItemMeta(meta);
            
            if (damageable.getDamage() >= tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }
 
    private void distributeRewards(Player player, Block block, ConfigurationSection config) {
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards != null) {
            int xp = rewards.getInt("xp", 0);
            if (xp > 0) player.giveExp(xp);
            
            for (String cmd : rewards.getStringList("commands")) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }
  
            List<?> drops = rewards.getList("drops");
            if (drops != null) {
                boolean dropToInv = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.drop-to-inventory", false);
                Random random = new Random();
                for (Object obj : drops) {
                    if (obj instanceof java.util.Map<?, ?> map) {
                        try {
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
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }
 
    private boolean isFullyGrown(Block block) {
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        if (block.getType().name().contains("AMETHYST")) {
            return block.getType() == org.bukkit.Material.AMETHYST_CLUSTER;
        }
        return true;
    }
 
    private boolean isGrowthCapable(Block block) {
        org.bukkit.block.data.BlockData data = block.getBlockData();
        return data instanceof org.bukkit.block.data.Ageable || block.getType().name().contains("AMETHYST");
    }
 
    private String findBlockKey(Block block) {
        return plugin.getRegenManager().findBlockKey(block, null);
    }
 
    private ConfigurationSection getBlockConfig(Block block, String blockKey) {
        return plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks." + blockKey);
    }
}
