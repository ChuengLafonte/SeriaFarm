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
import java.util.List;

public class ReplaceBlockMenu implements Listener {

    private final SeriaFarmPlugin plugin;

    public ReplaceBlockMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String configKey, String fullPath) {
        String displayTitle = configKey.contains("delay") ? "Delay Block Editor" : "Replace Block Editor";
        Inventory inv = Bukkit.createInventory(null, 54, StaticColors.getHexMsg("&#9370db&l" + displayTitle));
        
        // Setup border and controls
        ItemStack glass = InvUtils.createItemStacks(Material.PURPLE_STAINED_GLASS_PANE, " ", "");
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53}) {
            inv.setItem(i, glass);
        }

        inv.setItem(48, InvUtils.createItemStacks(Material.LIME_STAINED_GLASS_PANE, "&a&lSAVE &f& EXIT", "&7Save all changes to config"));
        inv.setItem(50, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL", "&7Back to Edit Menu"));
        
        // Info item to store context
        ItemStack info = InvUtils.createItemStacks(Material.PAPER, "&eContext Info (Hidden)", "");
        LocalizedName.set(info, matName + "|" + regionName + "|" + configKey + "|" + fullPath);
        inv.setItem(49, info);

        // Load existing data
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        List<String> current = config.getStringList(fullPath + "." + configKey);
        
        int slot = 10;
        for (String line : current) {
            try {
                String[] parts = line.split(";");
                Material mat = Material.matchMaterial(parts[0].trim());
                double chance = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 100.0;
                
                if (mat != null) {
                    while (isBorder(slot)) slot++;
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

    private boolean isBorder(int slot) {
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53}) {
            if (i == slot) return true;
        }
        return false;
    }

    private void updateLore(ItemStack item, double chance) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&eChance: &f" + chance + "%"));
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&7Click to &6Change Chance"));
            lore.add(StaticColors.getHexMsg("&7Right Click to &cRemove"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, chance);
            item.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!MINI_MESSAGE.serialize(event.getView().title()).contains("Editor")) return;
        
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();

        // Handle buttons
        if (slot == 50) { // Cancel
            event.setCancelled(true);
            refreshEditMenu(player);
            return;
        }
        
        if (slot == 48) { // Save
            event.setCancelled(true);
            saveData(player, event.getInventory());
            refreshEditMenu(player);
            return;
        }

        if (isBorder(slot)) {
            event.setCancelled(true);
            return;
        }

        // Interaction logic
        if (clicked != null && clicked.getType() != Material.AIR && slot < 54) {
            if (event.isRightClick()) {
                event.getInventory().setItem(slot, null);
                event.setCancelled(true);
                return;
            }
            
            // Left click to change chance
            event.setCancelled(true);
            
            ChatInputListener.requestInput(player, "Set Chance (%)", "Enter a number 0-100", input -> {
                try {
                    double chance = Double.parseDouble(input);
                    updateLore(clicked, chance);
                    player.openInventory(event.getInventory());
                } catch (Exception e) {
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cInvalid number."));
                    player.openInventory(event.getInventory());
                }
            }, () -> player.openInventory(event.getInventory()));
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
            YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
            File file = plugin.getConfigManager().getConfigFile("materials.yml");
            player.openInventory(new EditMenu(plugin).emenu(player, config, matName, file, regionName));
        }
    }

    private void saveData(Player player, Inventory inv) {
        ItemStack info = inv.getItem(49);
        String data = LocalizedName.get(info);
        String[] parts = data.split("\\|");
        String configKey = parts[2];
        String fullPath = parts[3];
        
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (isBorder(i) || i == 49) continue;
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                double chance = item.getItemMeta().getPersistentDataContainer()
                        .getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                list.add(item.getType().name() + " ; " + chance);
            }
        }
        
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        config.set(fullPath + "." + configKey, list);
        plugin.getConfigManager().saveConfig("materials.yml");
        player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aSettings saved successfully!"));
    }
}
