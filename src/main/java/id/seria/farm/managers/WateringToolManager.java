package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all registered watering tools and their NBT-based capacity state.
 *
 * Each tool is identified by an item identifier (e.g. "WATER_BUCKET" or "mi:MATERIAL:WATERING_CAN")
 * and carries two config properties:
 *   - per-use:   how much watering_level is added to a plant per click
 *   - capacity:  max charges the tool can hold (tracked in item NBT)
 */
public class WateringToolManager {

    private static final String NBT_CAPACITY = "seriafarm_capacity";

    private final SeriaFarmPlugin plugin;
    private final NamespacedKey capacityKey;

    public WateringToolManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        this.capacityKey = new NamespacedKey(plugin, NBT_CAPACITY);
    }

    // ─── Tool Config ──────────────────────────────────────────────────────

    public record WateringTool(String identifier, String displayName, int perUse, int capacity, String emptyItem) {}

    /**
     * Load all registered tools from config.yml.
     */
    public List<WateringTool> getAllTools() {
        List<WateringTool> tools = new ArrayList<>();
        List<?> list = plugin.getConfigManager().getConfig("config.yml").getList("watering-tools.tools");
        if (list == null) return tools;
        for (Object obj : list) {
            if (!(obj instanceof java.util.Map<?, ?> rawMap)) continue;
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) rawMap;
                String identifier = (String) map.get("identifier");
                String displayName = (String) map.getOrDefault("display-name", identifier);
                int perUse = ((Number) map.getOrDefault("per-use", 3)).intValue();
                int capacity = ((Number) map.getOrDefault("capacity", 1)).intValue();
                String emptyItem = (String) map.getOrDefault("empty-item", "BUCKET");
                tools.add(new WateringTool(identifier, displayName, perUse, capacity, emptyItem));
            } catch (Exception ignored) {}
        }
        return tools;
    }

    /**
     * Returns the WateringTool config for the given ItemStack, or null if not a watering tool.
     */
    public WateringTool getToolConfig(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        String identifier = plugin.getHookManager().getItemIdentifier(item);
        for (WateringTool tool : getAllTools()) {
            if (tool.identifier().equalsIgnoreCase(identifier)) return tool;
        }
        return null;
    }

    // ─── Capacity NBT ─────────────────────────────────────────────────────

    /**
     * Returns the current capacity left in this item.
     * If item has no NBT capacity set, initializes it to the tool's max capacity.
     */
    public int getCapacity(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;
        if (item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(capacityKey, PersistentDataType.INTEGER)) {
                return pdc.get(capacityKey, PersistentDataType.INTEGER);
            }
        }
        // First time: return max capacity from config
        WateringTool config = getToolConfig(item);
        return config != null ? config.capacity() : 0;
    }

    /**
     * Sets the capacity in item NBT. Mutates the ItemStack.
     */
    public void setCapacity(ItemStack item, int amount) {
        if (item == null || item.getType() == Material.AIR) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(capacityKey, PersistentDataType.INTEGER, Math.max(0, amount));
        item.setItemMeta(meta);
    }

    /**
     * Consume 1 charge from the item.
     * If capacity reaches 0, replaces the item with the configured empty-item.
     * Returns the ItemStack to set back in the player's hand (may be different item).
     */
    public ItemStack consume(ItemStack item) {
        WateringTool config = getToolConfig(item);
        if (config == null) return item;

        int current = getCapacity(item);
        int next = current - 1;

        if (next <= 0) {
            // Replace with empty item
            return plugin.getHookManager().getItem(config.emptyItem());
        } else {
            setCapacity(item, next);
            // Update lore to show remaining charges
            updateCapacityLore(item, next, config.capacity());
            return item;
        }
    }

    /**
     * Refill a tool to max capacity (e.g. when player right-clicks a water source).
     */
    public ItemStack refill(ItemStack item) {
        WateringTool config = getToolConfig(item);
        if (config == null) return item;
        setCapacity(item, config.capacity());
        updateCapacityLore(item, config.capacity(), config.capacity());
        return item;
    }

    private void updateCapacityLore(ItemStack item, int current, int max) {
        if (item == null || item.getType() == Material.AIR) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        // Find and replace capacity line, or add if missing
        String capacityTag = "§7Capacity: ";
        net.kyori.adventure.text.Component capacityLine = net.kyori.adventure.text.Component.text(
                capacityTag + current + "/" + max
        );

        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            if (plain.startsWith("Capacity:")) {
                lore.set(i, capacityLine);
                replaced = true;
                break;
            }
        }
        if (!replaced) lore.add(capacityLine);
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Saves the full list of tools back to config.yml.
     * Used by the admin GUI WateringToolsMenu.
     */
    public void saveTools(List<WateringTool> tools) {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        for (WateringTool t : tools) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("identifier", t.identifier());
            map.put("display-name", t.displayName());
            map.put("per-use", t.perUse());
            map.put("capacity", t.capacity());
            map.put("empty-item", t.emptyItem());
            list.add(map);
        }
        plugin.getConfigManager().getConfig("config.yml").set("watering-tools.tools", list);
        plugin.getConfigManager().saveConfig("config.yml");
    }
}
