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

        // 1. Prevent breaking blocks that are already regenerating
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        // 2. Check if this block is managed by SeriaFarm
        ConfigurationSection config = findBlockConfig(block);
        if (config == null) return;

        // 2.1 GROWTH STAGE VALIDATION
        if (!isFullyGrown(block)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-fully-grown"));
            event.setCancelled(true);
            return;
        }

        // WORLDGUARD BYPASS LOGIC (if event cancelled by other plugin, we uncancel if it's our block)
        if (event.isCancelled()) {
            event.setCancelled(false);
        }

        // 3. REQUIREMENT CHECK
        if (!plugin.getRequirementEngine().canBreak(player, config)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission-to-break"));
            return;
        }

        // 4. CANCEL EVENT to prevent AIR replacement and handle manually
        event.setCancelled(true);

        // 5. HANDLE REGENERATION
        int delay = config.getInt("regen-delay", 10);
        java.util.List<String> replaceBlocks = config.getStringList("replace-blocks");
        java.util.List<String> delayBlocks = config.getStringList("delay-blocks");
        
        // Get fallback material from config (material awal)
        String matStr = config.getString("material", block.getType().name());
        org.bukkit.Material fallbackMat = org.bukkit.Material.matchMaterial(matStr);
        if (fallbackMat == null) fallbackMat = block.getType();
        
        plugin.getRegenManager().scheduleRegeneration(block, delay, replaceBlocks, delayBlocks, fallbackMat);
        
        // 6. MANAGE TOOL DURABILITY
        handleDurability(player);
        
        // 7. DISTRIBUTE REWARDS
        distributeRewards(player, block, config);
        
        // 8. AURASKILLS XP SYNC
        plugin.getAuraSkillsManager().giveXP(player, block.getType());
    }

    private void handleDurability(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == org.bukkit.Material.AIR) return;
        
        org.bukkit.inventory.meta.ItemMeta meta = tool.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
            // Handle Unbreaking enchantment (random chance to not take damage)
            int unbreakingLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
            if (unbreakingLevel > 0) {
                if (new Random().nextInt(unbreakingLevel + 1) != 0) return;
            }
            
            damageable.setDamage(damageable.getDamage() + 1);
            tool.setItemMeta(meta);
            
            // Handle tool breakage
            if (damageable.getDamage() >= tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }

    private void distributeRewards(Player player, Block block, ConfigurationSection config) {
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards != null) {
            // 1. XP distribution
            int xp = rewards.getInt("xp", 0);
            if (xp > 0) player.giveExp(xp);
            
            // 2. Command execution
            for (String cmd : rewards.getStringList("commands")) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }

            // 3. Custom Item Drops
            List<?> drops = rewards.getList("drops");
            if (drops != null) {
                boolean dropToInv = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.drop-to-inventory", false);
                Random random = new Random();
                
                for (Object obj : drops) {
                    if (obj instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
                        ItemStack item = (ItemStack) map.get("item");
                        double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                        
                        if (item != null && (random.nextDouble() * 100.0) <= chance) {
                            ItemStack dropItem = item.clone();
                            if (dropToInv) {
                                // Try adding to inventory first
                                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(dropItem);
                                // If inventory is full, drop the remaining on the block
                                if (!remaining.isEmpty()) {
                                    for (ItemStack left : remaining.values()) {
                                        block.getWorld().dropItemNaturally(block.getLocation(), left);
                                    }
                                }
                            } else {
                                // Drop at block location
                                block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
                            }
                        }
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
