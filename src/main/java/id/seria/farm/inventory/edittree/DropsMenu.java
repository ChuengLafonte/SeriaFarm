package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DropsMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public DropsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String fullPath) {
        String materialKey = matName.contains(":") ? matName.split(":")[1] : matName;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%material%", materialKey);

        Inventory inv = plugin.getGuiManager().createInventory("drops-menu", placeholders);
        
        // Metadata (PAPER in slot 49)
        ItemStack info = inv.getItem(49);
        if (info != null) LocalizedName.set(info, matName + "|" + regionName + "|" + fullPath);

        // Load existing data
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        List<?> drops = config.getList(fullPath + ".rewards.drops");
        
        int slot = 0;
        if (drops != null) {
            for (Object obj : drops) {
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    ItemStack item = (ItemStack) map.get("item");
                    double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                    
                    if (item != null) {
                        while (slot < inv.getSize() && inv.getItem(slot) != null) slot++;
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

    private void updateLore(ItemStack item, double chance, String weight) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            lore.removeIf(line -> id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line).contains("Chance:"));
            lore.removeIf(line -> id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line).contains("Weight:"));
            lore.removeIf(line -> id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line).contains("Click to"));
            lore.removeIf(line -> id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line).contains("Click:"));
            
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&eChance: &f" + chance + "%"));
            if (weight != null) lore.add(StaticColors.getHexMsg("&bWeight: &f" + weight));
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof id.seria.farm.managers.GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("drops-menu")) return;
        
        Inventory topInv = event.getInventory();
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();

        // Standard interaction for "open" slots
        if (slot < topInv.getSize() && slot >= 0) {
            ItemStack metaItem = topInv.getItem(slot);
            String action = metaItem != null ? LocalizedName.get(metaItem) : null;
            
            if (action != null) {
                event.setCancelled(true);
                if (action.equals("cancel")) refreshEditMenu(player);
                if (action.equals("save")) { saveData(player, topInv); refreshEditMenu(player); }
                return;
            }
        }

        // Handle Shift Click (Bottom to Top)
        if (event.isShiftClick() && event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (clicked != null && clicked.getType() != Material.AIR) {
                int firstEmpty = -1;
                for (int i = 0; i < 44; i++) {
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

        if (event.getClickedInventory() == topInv) {
            // Protect background fillers if they are clicked
            if (clicked != null && clicked.getType() == Material.BLACK_STAINED_GLASS_PANE && LocalizedName.get(clicked) == null) {
                event.setCancelled(true);
                return;
            }

            // Drag N Drop logic or Editing
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ItemStack inSlot = topInv.getItem(slot);
                    if (inSlot != null && inSlot.getType() != Material.AIR && LocalizedName.get(inSlot) == null) {
                        updateLore(inSlot, 100.0, null);
                    }
                }, 1L);
                event.setCancelled(false);
            } else if (clicked != null && clicked.getType() != Material.AIR && LocalizedName.get(clicked) == null) {
                if (event.isRightClick()) {
                    topInv.setItem(slot, null);
                    event.setCancelled(true);
                } else if (event.isShiftClick() && event.isLeftClick()) {
                    event.setCancelled(true);
                    ChatInputListener.requestInput(player, "Set Weight", "none or range", input -> {
                        String weight = input.equalsIgnoreCase("none") ? null : input;
                        double chance = clicked.getItemMeta().getPersistentDataContainer()
                                .getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                        updateLore(clicked, chance, weight);
                        player.openInventory(topInv);
                    }, () -> player.openInventory(topInv));
                } else if (event.isLeftClick()) {
                    event.setCancelled(true);
                    ChatInputListener.requestInput(player, "Set Chance", "0-100", input -> {
                        try {
                            double chance = Double.parseDouble(input);
                            String weight = clicked.getItemMeta().getPersistentDataContainer()
                                    .get(SeriaFarmPlugin.weightKey, PersistentDataType.STRING);
                            updateLore(clicked, chance, weight);
                        } catch (Exception ignored) {}
                        player.openInventory(topInv);
                    }, () -> player.openInventory(topInv));
                }
            }
        }
    }

    private void refreshEditMenu(Player player) {
        ItemStack info = player.getOpenInventory().getTopInventory().getItem(49);
        if (info == null) return;
        String[] parts = LocalizedName.get(info).split("\\|");
        String matName = parts[0], regionName = parts[1];
        
        if (regionName.equalsIgnoreCase("global")) {
            player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, matName));
        } else {
            YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
            player.openInventory(new EditMenu(plugin).emenu(player, config, matName, null, regionName));
        }
    }

    private void saveData(Player player, Inventory inv) {
        ItemStack info = inv.getItem(49);
        String fullPath = LocalizedName.get(info).split("\\|")[2];
        List<Map<String, Object>> dropsList = new ArrayList<>();
        
        for (int i = 0; i < 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && LocalizedName.get(item) == null) {
                ItemMeta meta = item.getItemMeta();
                double chance = 100.0;
                String weight = null;
                if (meta != null) {
                    chance = meta.getPersistentDataContainer().getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                    weight = meta.getPersistentDataContainer().get(SeriaFarmPlugin.weightKey, PersistentDataType.STRING);
                    // Cleanup lore
                    List<Component> lore = meta.lore();
                    if (lore != null) {
                        lore.removeIf(line -> id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line).contains("Chance:"));
                        lore.removeIf(line -> id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line).contains("Weight:"));
                        lore.removeIf(line -> id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line).contains("Click:"));
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
        plugin.getConfigManager().sendPrefixedMessage(player, "&aDrops saved!");
    }
}
