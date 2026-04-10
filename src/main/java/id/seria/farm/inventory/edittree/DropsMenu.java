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

import java.io.File;
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
        Inventory inv = Bukkit.createInventory(player, 54, StaticColors.getHexMsg("&#9370db&lCustom Drops Editor"));
        
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
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
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
                        
                        updateLore(item, chance);
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

    private void updateLore(ItemStack item, double chance) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            // Clean up old chance lore if exists
            lore.removeIf(line -> line.contains("Chance:"));
            lore.removeIf(line -> line.contains("Click to"));
            
            lore.add("");
            lore.add(StaticColors.getHexMsg("&eChance: &f" + chance + "%"));
            lore.add("");
            lore.add(StaticColors.getHexMsg("&7Click to &6Change Chance"));
            lore.add(StaticColors.getHexMsg("&7Right Click to &cRemove"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, chance);
            item.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Drops Editor")) return;
        
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
            // Allow clicking on player inventory to drag N drop (handled by default but we check slot)
            if (slot == 49) event.setCancelled(true);
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
            
            ChatInputListener.requestInput(player, "Set Drop Chance (%)", "Enter a number 0-100", input -> {
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
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        File file = plugin.getConfigManager().getConfigFile("materials.yml");
        player.openInventory(new EditMenu(plugin).emenu(player, config, matName, file, regionName));
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
                if (meta != null) {
                    chance = meta.getPersistentDataContainer()
                            .getOrDefault(SeriaFarmPlugin.chanceKey, PersistentDataType.DOUBLE, 100.0);
                    
                    // Cleanup lore for storage
                    List<String> lore = meta.getLore();
                    if (lore != null) {
                        lore.removeIf(line -> line.contains("Chance:"));
                        lore.removeIf(line -> line.contains("Click to"));
                        if (lore.size() > 0 && lore.get(lore.size()-1).isEmpty()) lore.remove(lore.size()-1);
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                }
                
                Map<String, Object> entry = new HashMap<>();
                entry.put("item", item);
                entry.put("chance", chance);
                dropsList.add(entry);
            }
        }
        
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        config.set(fullPath + ".rewards.drops", dropsList);
        plugin.getConfigManager().saveConfig("materials.yml");
        player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aCustom drops saved successfully!"));
    }
}
