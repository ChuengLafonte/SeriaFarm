package id.seria.farm.listeners;
 
import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.models.RegenBlock;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.meta.ItemMeta;
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
 
import static id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE;
 
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
  
        // REDIRECT TO ROOT FOR BAMBOO
        if (block.getType() == Material.BAMBOO || block.getType() == Material.BAMBOO_SAPLING) {
            block = plugin.getRegenManager().getBambooRoot(block);
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
                player.sendMessage(StaticColors.getHexMsg("&8[&bDebug&8] &7Blok ditemukan di map (Akar/Block). isGrowth: &f" + (regen != null ? regen.isGrowth() : "NULL") + " &7| Key: &f" + plugin.getRegenManager().toKey(regen != null ? regen.getLocation() : block.getLocation())));
            }
 
            if (regen != null && regen.isGrowth()) {
                plugin.getVisualManager().showGrowthInfo(player, regen);
            }
            event.setCancelled(true);
            return;
        }
 
        // 1.5 ADMIN HARVEST DEBUG
        if (player.hasPermission("seriafarm.admin") || player.isOp()) {
             player.sendMessage(StaticColors.getHexMsg("&8[&bDebug&8] &7Blok &fTIDAK &7ditemukan di map. Key: &f" + plugin.getRegenManager().toKey(block.getLocation())));
        }
        
        // 2. Check if this is a managed crop/block
        String blockKey = findBlockKey(player, block);
        
        // 2.0. AD-HOC TRACKING (Handle growing blocks not in map)
        if (blockKey != null && !isFullyGrown(block)) {
            plugin.getRegenManager().startAdHocTracking(block, blockKey);
            // Now that it's in the map, recursive call or just show info
            RegenBlock regen = plugin.getRegenManager().getRegenBlock(block.getLocation());
            if (regen != null && regen.isGrowth()) {
                plugin.getVisualManager().showGrowthInfo(player, regen);
                event.setCancelled(true);
                return;
            }
        }
 
        if (blockKey == null) return;
        
        // 2.1 Regional blocks: Disable right-click harvesting
        if (!blockKey.startsWith("global.")) return;
        
        // 2.2 Global blocks: Check if harvest is enabled
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.global-right-click-harvest", true)) {
            return;
        }
 
        // 2.3 Check growth stage
        ConfigurationSection config = getBlockConfig(block, blockKey);
        if (config == null) return;
        
        if (!isFullyGrown(block)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-fully-grown"));
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
        if (block.getType() == Material.BAMBOO) {
            String key = plugin.getRegenManager().findBlockKey(block, null);
            if (key != null) {
                int max = plugin.getConfigManager().getConfig("materials.yml").getInt("blocks." + key + ".bamboo-max-height", 12);
                int current = plugin.getRegenManager().getBambooHeight(block);
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
            Random random = new Random();
            for (Object obj : drops) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    ItemStack item = (ItemStack) map.get("item");
                    double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                    if (item != null && (random.nextDouble() * 100.0) <= chance) {
                        ItemStack dropItem = item.clone();
                        ItemMeta meta = dropItem.getItemMeta();
                        if (meta != null) {
                            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                            if (lore != null) {
                                lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Chance:"));
                                lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Click to"));
                                if (lore.size() > 0 && MINI_MESSAGE.serialize(lore.get(lore.size()-1)).isEmpty()) lore.remove(lore.size()-1);
                                meta.lore(lore);
                                dropItem.setItemMeta(meta);
                            }
                        }
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
 
    private String findBlockKey(Player player, Block block) {
        return plugin.getRegenManager().findBlockKey(block, player);
    }

    private ConfigurationSection getBlockConfig(Block block, String blockKey) {
        return plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks." + blockKey);
    }
}
