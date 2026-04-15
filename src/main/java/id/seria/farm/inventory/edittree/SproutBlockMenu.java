package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.StaticColors;
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
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class SproutBlockMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public SproutBlockMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String configKey, String fullPath) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%title%", "Sprout Type Editor");

        Inventory inv = plugin.getGuiManager().createInventory("sprout-block-menu", placeholders);
        
        // Metadata (PAPER in slot 26)
        ItemStack info = inv.getItem(26);
        if (info != null) LocalizedName.set(info, matName + "|" + regionName + "|" + configKey + "|" + fullPath);

        // Load existing data
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        List<String> current = config.getStringList(fullPath + "." + configKey);
        
        if (current != null && !current.isEmpty()) {
            try {
                String[] parts = current.get(0).split(";");
                Material mat = Material.matchMaterial(parts[0].trim());
                if (mat != null && mat != Material.BLACK_STAINED_GLASS_PANE) {
                    ItemStack item = new ItemStack(mat);
                    updateLore(item);
                    inv.setItem(13, item);
                }
            } catch (Exception ignored) {}
        }

        player.openInventory(inv);
    }

    private void updateLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&7Right Click: &cRemove"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof id.seria.farm.managers.GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("sprout-block-menu")) return;
        
        Inventory topInv = event.getInventory();
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();

        // Standard interaction for menu items
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
            // Protect background fillers
            if (clicked != null && clicked.getType() == Material.BLACK_STAINED_GLASS_PANE && LocalizedName.get(clicked) == null) {
                event.setCancelled(true);
                return;
            }

            // Exactly slot 13 is the single active slot
            if (slot == 13) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        ItemStack inSlot = topInv.getItem(13);
                        if (inSlot != null && inSlot.getType() != Material.AIR && LocalizedName.get(inSlot) == null) {
                            inSlot.setAmount(1); // Force 1 amount
                            updateLore(inSlot);
                        }
                    }, 1L);
                    event.setCancelled(false);
                } else if (clicked != null && clicked.getType() != Material.AIR && LocalizedName.get(clicked) == null) {
                    if (event.isRightClick()) {
                        topInv.setItem(13, null);
                        event.setCancelled(true);
                    } else {
                        event.setCancelled(false);
                    }
                }
            } else {
                event.setCancelled(true); // Disable any other slot clicks
            }
        }
    }

    private void refreshEditMenu(Player player) {
        ItemStack info = player.getOpenInventory().getTopInventory().getItem(26);
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
        ItemStack info = inv.getItem(26);
        if (info == null) return;
        String data = LocalizedName.get(info);
        String[] parts = data.split("\\|");
        String configKey = parts[2], fullPath = parts[3];
        
        List<String> list = new ArrayList<>();
        ItemStack item = inv.getItem(13);
        if (item != null && item.getType() != Material.AIR && LocalizedName.get(item) == null) {
            // Implicit 100.0 chance
            list.add(item.getType().name() + " ; 100.0");
        }
        
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        config.set(fullPath + "." + configKey, list);
        plugin.getConfigManager().saveConfig("crops.yml");
        plugin.getConfigManager().sendPrefixedMessage(player, "&aSprout type saved!");
    }
}
