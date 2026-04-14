package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.listeners.ChatInputListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import static id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class DropsMenu implements Listener, InventoryHolder {

    private final SeriaFarmPlugin plugin;

    public DropsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String fullPath) {
        Inventory inv = Bukkit.createInventory(this, 54, StaticColors.getHexMsg("&#9370db&lCustom Drops Editor"));
        
        // Setup border and controls
        ItemStack glass = InvUtils.createItemStacks(Material.PURPLE_STAINED_GLASS_PANE, " ", "");
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53}) {
            inv.setItem(i, glass);
        }

        inv.setItem(48, InvUtils.createItemStacks(Material.LIME_STAINED_GLASS_PANE, "&a&lSAVE &f& EXIT", "&7Save all drops to config"));
        inv.setItem(50, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL", "&7Back to Edit Menu"));
        
        // Info item to store context
        ItemStack info = InvUtils.createItemStacks(Material.PAPER, "&eContext Info (Hidden)", "");
        LocalizedName.set(info, matName + "|" + regionName + "|" + fullPath);
        inv.setItem(49, info);

        // Load existing data
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        List<?> drops = config.getList(fullPath + ".rewards.drops");
        
        int slot = 10;
        if (drops != null) {
            for (Object obj : drops) {
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    ItemStack item = (ItemStack) map.get("item");
                    double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                    
                    if (item != null) {
                        while (isBorder(slot)) slot++;
                        if (slot >= 44) break;
                        
                        String weight = map.containsKey("weight") ? String.valueOf(map.get("weight")) : null;
                        updateLore(item, chance, weight);
                        inv.setItem(slot, item);
                        slot++;
                    }
                }
            }
        }

        player.openInventory(inv);
    }

    private boolean isBorder(int slot) {
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53}) {
            if (i == slot) return true;
        }
        return false;
    }

    private void updateLore(ItemStack item, double chance, String weight) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            // Clean up old chance/weight lore if exists
            lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Chance:"));
            lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Weight:"));
            lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Click to"));
            
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&eChance: &f" + chance + "%"));
            if (weight != null) {
                lore.add(StaticColors.getHexMsg("&bWeight: &f" + weight));
            }
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&7Left Click: &6Change Chance"));
            lore.add(StaticColors.getHexMsg("&7Shift-Left Click: &bSet Weight"));
            lore.add(StaticColors.getHexMsg("&7Right Click: &cRemove"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, chance);
            if (weight != null) {
                meta.getPersistentDataContainer().set(SeriaFarmPlugin.weightKey, PersistentDataType.STRING, weight);
            } else {
                meta.getPersistentDataContainer().remove(SeriaFarmPlugin.weightKey);
            }
            item.setItemMeta(meta);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54, StaticColors.getHexMsg("&#9370db&lDrops Editor"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DropsMenu)) return;
        
        Inventory topInv = event.getInventory();
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();

        // 1. Handle Shift Click (Bottom to Top)
        if (event.isShiftClick() && event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (clicked != null && clicked.getType() != Material.AIR) {
                int firstEmpty = -1;
                for (int i = 10; i < 44; i++) {
                    if (isBorder(i)) continue;
                    ItemStack existing = topInv.getItem(i);
                    if (existing == null || existing.getType() == Material.AIR) {
                        firstEmpty = i;
                        break;
                    }
                }
                if (firstEmpty != -1) {
                    ItemStack dropItem = clicked.clone();
                    updateLore(dropItem, 100.0, null);
                    topInv.setItem(firstEmpty, dropItem);
                    clicked.setAmount(clicked.getAmount() - 1);
                }
            }
            event.setCancelled(true);
            return;
        }

        // 2. Handle Player Inventory Click (Allow)
        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            return; // Standard behavior
        }

        // 3. Handle Top Inventory Click
        if (event.getClickedInventory() == topInv) {
            // Protect borders and buttons
            if (isBorder(slot) || slot == 49 || slot >= 54) {
                event.setCancelled(true);
                if (slot == 50) refreshEditMenu(player); // Cancel
                if (slot == 48) { saveData(player, topInv); refreshEditMenu(player); } // Save
                return;
            }

            // Handle Drag N Drop placement (from cursor)
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                // Allow placement, but format the item 1 tick later
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ItemStack inSlot = topInv.getItem(slot);
                    if (inSlot != null && inSlot.getType() != Material.AIR) {
                        double chance = 100.0;
                        if (inSlot.hasItemMeta()) {
                            chance = inSlot.getItemMeta().getPersistentDataContainer()
                                    .getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                        }
                        updateLore(inSlot, chance, null);
                    }
                }, 1L);
                event.setCancelled(false); // Let the placement happen
            } else {
                // Picking up or clicking existing items
                if (clicked != null && clicked.getType() != Material.AIR) {
                    if (event.isRightClick()) {
                        topInv.setItem(slot, null);
                        event.setCancelled(true);
                        return;
                    }
                    
                    // Strip lore when picking up
                    if (event.getAction().name().contains("PICKUP") || event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY")) {
                        InvUtils.stripTechnicalLore(clicked);
                        // Let it happen
                    } else {
                        event.setCancelled(true);
                    }
                    
                    if (event.isShiftClick() && event.isLeftClick()) {
                        event.setCancelled(true);
                        ChatInputListener.requestInput(player, "Set Weight Range (e.g. 0.1-1.0)", "Type 'none' to disable rarity", input -> {
                            String weight = input.equalsIgnoreCase("none") ? null : input;
                            double chance = clicked.getItemMeta().getPersistentDataContainer()
                                    .getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                            updateLore(clicked, chance, weight);
                            player.openInventory(topInv);
                        }, () -> player.openInventory(topInv));
                        return;
                    }
                    
                    if (event.isLeftClick()) {
                        event.setCancelled(true);
                        ChatInputListener.requestInput(player, "Set Drop Chance (%)", "Enter a number 0-100", input -> {
                            try {
                                double chance = Double.parseDouble(input);
                                String weight = clicked.getItemMeta().getPersistentDataContainer()
                                        .get(SeriaFarmPlugin.weightKey, PersistentDataType.STRING);
                                updateLore(clicked, chance, weight);
                                player.openInventory(topInv);
                            } catch (Exception e) {
                                plugin.getConfigManager().sendPrefixedMessage(player, "&cInvalid number.");
                                player.openInventory(topInv);
                            }
                        }, () -> player.openInventory(topInv));
                    }
                }
            }
        }
    }

    private void refreshEditMenu(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack info = inv.getItem(49);
        if (info == null) return;
        String data = LocalizedName.get(info);
        String[] parts = data.split("\\|");
        String matName = parts[0];
        String regionName = parts[1];
        
        if (regionName.equalsIgnoreCase("global")) {
            player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, matName));
        } else {
            YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
            File file = plugin.getConfigManager().getConfigFile("crops.yml");
            player.openInventory(new EditMenu(plugin).emenu(player, config, matName, file, regionName));
        }
    }

    private void saveData(Player player, Inventory inv) {
        ItemStack info = inv.getItem(49);
        String data = LocalizedName.get(info);
        String[] parts = data.split("\\|");
        String fullPath = parts[2];
        
        List<Map<String, Object>> dropsList = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (isBorder(i) || i == 49) continue;
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                ItemMeta meta = item.getItemMeta();
                double chance = 100.0;
                String weight = null;
                if (meta != null) {
                    chance = meta.getPersistentDataContainer()
                            .getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                    weight = meta.getPersistentDataContainer()
                            .get(SeriaFarmPlugin.weightKey, PersistentDataType.STRING);
                    
                    // Cleanup lore for storage
                    List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                    if (lore != null) {
                        lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Chance:"));
                        lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Weight:"));
                        lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Click to"));
                        lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Left Click:"));
                        lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Shift-Left Click:"));
                        lore.removeIf(line -> MINI_MESSAGE.serialize(line).contains("Right Click:"));
                        if (lore.size() > 0 && MINI_MESSAGE.serialize(lore.get(lore.size()-1)).isEmpty()) lore.remove(lore.size()-1);
                        meta.lore(lore);
                        item.setItemMeta(meta);
                    }
                }
                
                Map<String, Object> entry = new HashMap<>();
                entry.put("item", item);
                entry.put("chance", chance);
                if (weight != null) entry.put("weight", weight);
                dropsList.add(entry);
            }
        }
        
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        config.set(fullPath + ".rewards.drops", dropsList);
        plugin.getConfigManager().saveConfig("crops.yml");
        plugin.getConfigManager().sendPrefixedMessage(player, "&aCustom drops saved successfully!");
    }
}
