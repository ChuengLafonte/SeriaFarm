package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GuiManager {

    private final SeriaFarmPlugin plugin;

    public GuiManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates an inventory based on gui.yml configuration.
     * @param key The menu key in gui.yml
     * @param placeholders Map of placeholders to replace in title, name, and lore
     * @return The built inventory
     */
    public Inventory createInventory(String key, Map<String, String> placeholders) {
        ConfigurationSection config = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection(key);
        if (config == null) {
            plugin.getLogger().severe("GUI Configuration for '" + key + "' not found in gui.yml!");
            return Bukkit.createInventory(null, 27, Component.text("Error: Config Missing"));
        }

        String titleStr = config.getString("title", "Menu");
        int size = config.getInt("size", 27);
        
        Component title = StaticColors.getHexMsg(applyPlaceholders(titleStr, placeholders));
        
        // We use a custom holder to identify the menu in listeners
        MenuHolder holder = new MenuHolder(key);
        Inventory inventory = Bukkit.createInventory(holder, size, title);

        // Collect all items (fillers and main items)
        List<GuiItem> allItems = new ArrayList<>();
        
        // Load Fillers
        ConfigurationSection fillers = config.getConfigurationSection("fillers");
        if (fillers != null) {
            for (String fillerKey : fillers.getKeys(false)) {
                allItems.add(parseGuiItem(fillers.getConfigurationSection(fillerKey), placeholders));
            }
        }
        
        // Load Main Items
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String itemKey : items.getKeys(false)) {
                allItems.add(parseGuiItem(items.getConfigurationSection(itemKey), placeholders));
            }
        }

        // Sort by priority (ascending, so higher priority items are placed later and overwrite)
        allItems.sort(Comparator.comparingInt(GuiItem::priority));

        // Place items in inventory
        for (GuiItem item : allItems) {
            ItemStack stack = item.createStack();
            for (int slot : item.slots()) {
                if (slot >= 0 && slot < size) {
                    inventory.setItem(slot, stack);
                }
            }
        }

        return inventory;
    }

    private GuiItem parseGuiItem(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) return new GuiItem(new ArrayList<>(), 0, Material.AIR, " ", new ArrayList<>(), null, placeholders);
        
        List<String> slotStrings = section.getStringList("slots");
        List<Integer> slots = parseSlots(slotStrings);
        int priority = section.getInt("priority", 0);
        String matStr = applyPlaceholders(section.getString("material", "STONE"), placeholders);
        String name = applyPlaceholders(section.getString("name", " "), placeholders);
        List<String> lore = section.getStringList("lore");
        String action = section.getString("action"); // New: Read action key
        
        Material material = Material.matchMaterial(matStr.toUpperCase());
        if (material == null) material = Material.STONE;
        
        List<String> processedLore = new ArrayList<>();
        for (String line : lore) {
            processedLore.add(applyPlaceholders(line, placeholders));
        }
        
        return new GuiItem(slots, priority, material, name, processedLore, action, placeholders);
    }

    private List<Integer> parseSlots(List<String> slotStrings) {
        List<Integer> slots = new ArrayList<>();
        for (String s : slotStrings) {
            if (s.contains("-")) {
                String[] parts = s.split("-");
                try {
                    int start = Integer.parseInt(parts[0]);
                    int end = Integer.parseInt(parts[1]);
                    for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                        slots.add(i);
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                try {
                    slots.add(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {}
            }
        }
        return slots;
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return text;
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "null";
            text = text.replace(entry.getKey(), value);
        }
        return text;
    }

    private static record GuiItem(List<Integer> slots, int priority, Material material, String name, List<String> lore, String action, Map<String, String> placeholders) {
        public ItemStack createStack() {
            ItemStack item = new ItemStack(material);
            InvUtils.applyMeta(item, name, lore.toArray());
            
            // Apply action key if present
            if (action != null) {
                id.seria.farm.inventory.utils.LocalizedName.set(item, action);
            }

            // Special handling for Player Head if name placeholder is used
            if (material == Material.PLAYER_HEAD && placeholders.containsKey("%player_name%")) {
                if (item.getItemMeta() instanceof SkullMeta skull) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(placeholders.get("%player_name%")));
                    item.setItemMeta(skull);
                }
            }
            return item;
        }
    }

    /**
     * Custom InventoryHolder to identify menus by a unique key.
     */
    public static class MenuHolder implements InventoryHolder {
        private final String menuKey;

        public MenuHolder(String menuKey) {
            this.menuKey = menuKey;
        }

        public String getMenuKey() {
            return menuKey;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return null; // Not needed as we create fresh inventories
        }
    }
}
