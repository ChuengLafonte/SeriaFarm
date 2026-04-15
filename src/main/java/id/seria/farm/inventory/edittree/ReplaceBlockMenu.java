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
import java.util.List;
import java.util.HashMap;
import java.util.Map;


public class ReplaceBlockMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public ReplaceBlockMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String configKey, String fullPath) {
        String displayTitle = configKey.contains("delay") ? "Delay Block Editor" : "Replace Block Editor";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%title%", displayTitle);

        Inventory inv = plugin.getGuiManager().createInventory("replace-block-menu", placeholders);
        
        // Metadata (PAPER in slot 49)
        ItemStack info = inv.getItem(49);
        if (info != null) LocalizedName.set(info, matName + "|" + regionName + "|" + configKey + "|" + fullPath);

        // Load existing data
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        List<String> current = config.getStringList(fullPath + "." + configKey);
        
        int slot = 0;
        for (String line : current) {
            try {
                String[] parts = line.split(";");
                Material mat = Material.matchMaterial(parts[0].trim());
                if (mat == Material.BLACK_STAINED_GLASS_PANE) continue; // Skip accidentally saved panes
                double chance = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 100.0;
                
                if (mat != null) {
                    while (slot < inv.getSize() && (inv.getItem(slot) != null || isBorder(slot))) slot++;
                    if (slot >= 44) break;
                    
                    ItemStack item = new ItemStack(mat);
                    updateLore(item, chance);
                    inv.setItem(slot, item);
                    slot++;
                }
            } catch (Exception ignored) {}
        }

        player.openInventory(inv);
    }

    private void updateLore(ItemStack item, double chance) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&eChance: &f" + chance + "%"));
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&7Left Click: &6Change Chance"));
            lore.add(StaticColors.getHexMsg("&7Right Click: &cRemove"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, chance);
            item.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof id.seria.farm.managers.GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("replace-block-menu")) return;
        
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
                        updateLore(inSlot, 100.0);
                    }
                }, 1L);
                event.setCancelled(false);
            } else if (clicked != null && clicked.getType() != Material.AIR && LocalizedName.get(clicked) == null) {
                if (isBorder(slot)) {
                    event.setCancelled(true);
                    return;
                }
                if (event.isRightClick()) {
                    topInv.setItem(slot, null);
                    event.setCancelled(true);
                } else if (event.isLeftClick()) {
                    event.setCancelled(true);
                    ChatInputListener.requestInput(player, "Set Chance", "0-100", input -> {
                        try {
                            double chance = Double.parseDouble(input);
                            updateLore(clicked, chance);
                        } catch (Exception ignored) {}
                        player.openInventory(topInv);
                    }, () -> player.openInventory(topInv));
                }
            }
        }
    }

    private boolean isBorder(int slot) {
        int[] cancelledSlots = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int s : cancelledSlots) {
            if (s == slot) return true;
        }
        return false;
    }

    private void refreshEditMenu(Player player) {
        ItemStack info = player.getOpenInventory().getTopInventory().getItem(49);
        if (info == null) return;
        String data = LocalizedName.get(info);
        String[] parts = data.split("\\|");
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
        String data = LocalizedName.get(info);
        String[] parts = data.split("\\|");
        String configKey = parts[2], fullPath = parts[3];
        
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 44; i++) {
            if (isBorder(i)) continue;
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && LocalizedName.get(item) == null) {
                double chance = item.getItemMeta().getPersistentDataContainer()
                        .getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                list.add(item.getType().name() + " ; " + chance);
            }
        }
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        config.set(fullPath + "." + configKey, list);
        plugin.getConfigManager().saveConfig("crops.yml");
        plugin.getConfigManager().sendPrefixedMessage(player, "&aSettings saved!");
    }
}
