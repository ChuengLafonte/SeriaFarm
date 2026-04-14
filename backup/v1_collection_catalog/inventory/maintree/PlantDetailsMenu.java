package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.utils.RarityUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlantDetailsMenu implements Listener {

    private final SeriaFarmPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final String region;
    private final String plantKey;

    public PlantDetailsMenu(SeriaFarmPlugin plugin, Player player, String region, String plantKey, ConfigurationSection config) {
        this.plugin = plugin;
        this.player = player;
        this.region = region;
        this.plantKey = plantKey;
        this.inventory = Bukkit.createInventory(null, 36, StaticColors.getHexMsg("&#FFB47ECatalog » " + capitalize(plantKey)));

        setupInventory(config);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    private void setupInventory(ConfigurationSection config) {
        ItemStack border = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(StaticColors.getHexMsg(" "));
        border.setItemMeta(borderMeta);

        // Borders
        for (int i = 0; i < 9; i++) inventory.setItem(i, border);
        for (int i = 27; i < 36; i++) inventory.setItem(i, border);
        inventory.setItem(9, border);
        inventory.setItem(17, border);
        inventory.setItem(18, border);
        inventory.setItem(26, border);

        // Back Button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(StaticColors.getHexMsg("&#FF4E49« Back to Catalog"));
        back.setItemMeta(backMeta);
        inventory.setItem(31, back);

        // Drops
        List<?> drops = config.getList("rewards.drops");
        java.util.List<java.util.Map<String, Object>> displayDrops = new ArrayList<>();

        if (drops != null) {
            for (Object obj : drops) {
                if (obj instanceof Map<?, ?> map) {
                    // Safe cast to Map<String, Object>
                    java.util.Map<String, Object> safeMap = new java.util.HashMap<>();
                    map.forEach((k, v) -> safeMap.put(String.valueOf(k), v));
                    displayDrops.add(safeMap);
                }
            }
        }

        // Logic check for Automatic Vanilla Drop (Mirroring the Listeners)
        boolean hasCommonDrop = false;
        for (java.util.Map<String, Object> map : displayDrops) {
            double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
            if (chance >= 10.0) {
                hasCommonDrop = true;
                break;
            }
        }

        if (!hasCommonDrop && !config.getBoolean("suppress-vanilla-drop", false)) {
            java.util.Map<String, Object> vanillaDrop = new java.util.HashMap<>();
            Material vanillaMat = Material.matchMaterial(config.getString("material", "WHEAT"));
            if (vanillaMat == null) vanillaMat = Material.WHEAT;
            vanillaDrop.put("item", new ItemStack(id.seria.farm.inventory.utils.InvUtils.getSingleMaterial(vanillaMat)));
            vanillaDrop.put("chance", 100.0);
            displayDrops.add(0, vanillaDrop); // Add to the front as the primary "trash" item
        }

        if (displayDrops.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.displayName(StaticColors.getHexMsg("&#FF4E49No drops configured"));
            empty.setItemMeta(emptyMeta);
            inventory.setItem(13, empty);
            return;
        }

        int slot = 10;
        for (java.util.Map<String, Object> map : displayDrops) {
            if (slot % 9 == 8) slot += 2;
            if (slot > 25) break;

            inventory.setItem(slot++, createDropItem(map));
        }
    }

    private ItemStack createDropItem(Map<?, ?> map) {
        ItemStack originalItem = ((ItemStack) map.get("item")).clone();
        
        int playerLevel = plugin.getAuraSkillsManager().getFarmingLevel(player);
        double globalScaling = plugin.getConfigManager().getConfig("config.yml").getDouble("settings.global-level-scaling", 0.1);
        
        int reqLevel = map.containsKey("farming-level") ? ((Number) map.get("farming-level")).intValue() : 0;
        double baseChance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
        double scaling = map.containsKey("level-scaling") ? ((Number) map.get("level-scaling")).doubleValue() : globalScaling;

        boolean locked = playerLevel < reqLevel;
        ItemStack item = new ItemStack(locked ? Material.BARRIER : InvUtils.getSingleMaterial(originalItem.getType()));
        
        // Strip technical lore and PDC to show a clean item
        InvUtils.stripTechnicalLore(item);
        
        // Visual Rarity (based on base chance)
        RarityUtils.Rarity rarity = RarityUtils.getRarity(baseChance);
        
        ItemMeta meta = item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        
        lore.add(rarity.getStarComponent());
        lore.add(StaticColors.getHexMsg(" "));

        if (locked) {
            meta.displayName(StaticColors.getHexMsg("&7&l[LOCKED] ???"));
            lore.add(StaticColors.getHexMsg("&#FF4E49Status: &nLOCKED"));
            lore.add(StaticColors.getHexMsg("&#FF4E49Butuh Farming Level: " + reqLevel));
            lore.add(StaticColors.getHexMsg(" "));
            
            // Still show the potential chance
            lore.add(StaticColors.getHexMsg("&#FFB47EBase Chance: &#FFFFFF" + String.format("%.2f", baseChance) + "%"));
        } else {
            // Use original item name if unlocked
            meta.displayName(originalItem.getItemMeta().displayName());
            
            double finalChance = baseChance + (playerLevel - reqLevel) * scaling;
            lore.add(StaticColors.getHexMsg("&#FFB47EChance Anda: &#FFFFFF" + String.format("%.2f", finalChance) + "%"));
            if (playerLevel > reqLevel && scaling > 0) {
                lore.add(StaticColors.getHexMsg("&7(+ bonus level: &a" + String.format("%.1f", (playerLevel - reqLevel) * scaling) + "%&7)"));
            }
        }

        if (map.containsKey("weight")) {
            Object w = map.get("weight");
            lore.add(StaticColors.getHexMsg("&#FFB47EWeight: &#93cfec" + w));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("_", " ");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (event.getSlot() == 31) {
            new CatalogMenu(plugin, player);
            InventoryClickEvent.getHandlerList().unregister(this);
        }
    }
}
