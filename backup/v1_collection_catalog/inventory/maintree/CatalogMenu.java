package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
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
import java.util.Set;

public class CatalogMenu implements Listener {

    private final SeriaFarmPlugin plugin;
    private final Player player;
    private final Inventory inventory;

    public CatalogMenu(SeriaFarmPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54, StaticColors.getHexMsg("&#FFB47EPlant Catalog"));
        
        setupInventory();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    private void setupInventory() {
        ItemStack border = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(StaticColors.getHexMsg(" "));
        border.setItemMeta(borderMeta);

        // Standard LiteFarm-style Borders
        for (int i = 0; i < 9; i++) inventory.setItem(i, border);
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);
        for (int i = 9; i < 45; i += 9) inventory.setItem(i, border);
        for (int i = 17; i < 54; i += 9) inventory.setItem(i, border);

        // Fetch Categories (Global ONLY)
        ConfigurationSection global = plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops.global");
        if (global == null) {
            plugin.getLogger().warning("CatalogMenu: crops.global section not found in crops.yml!");
            return;
        }

        int playerLevel = plugin.getAuraSkillsManager().getFarmingLevel(player);

        int slot = 10;
        for (String plantKey : global.getKeys(false)) {
            if (slot % 9 == 8) slot += 2; // Skip borders
            if (slot > 43) break;

            ConfigurationSection plantConfig = global.getConfigurationSection(plantKey);
            inventory.setItem(slot++, createCategoryItem(plantKey, plantConfig, playerLevel));
        }
    }

    private ItemStack createCategoryItem(String key, ConfigurationSection config, int playerLevel) {
        Material mat = Material.matchMaterial(config.getString("material", "WHEAT"));
        if (mat == null) mat = Material.WHEAT;

        int requiredLevel = config.getInt("farming-level", 0);
        boolean locked = playerLevel < requiredLevel;

        ItemStack item = new ItemStack(locked ? Material.GRAY_STAINED_GLASS_PANE : InvUtils.getSingleMaterial(mat));
        ItemMeta meta = item.getItemMeta();

        String displayName = (locked ? "&7&l[LOCKED] " : "&#FFB47E") + capitalize(key);
        meta.displayName(StaticColors.getHexMsg(displayName));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(StaticColors.getHexMsg(" "));
        if (locked) {
            lore.add(StaticColors.getHexMsg("&#FF4E49Requires Farming Level " + requiredLevel));
            lore.add(StaticColors.getHexMsg("&7Keep farming to unlock this crop!"));
        } else {
            lore.add(StaticColors.getHexMsg("&#E1A5E9• Click to view drops"));
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

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.ORANGE_STAINED_GLASS_PANE) return;

        // Extract Region and Plant Key from Display Name or similar logic
        // For simplicity, we'll re-scan or use a better way. 
        // Let's use PDC for reliability.
        // Extract Plant Key from Display Name
        String plantName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        if (plantName.contains("[LOCKED]")) {
            player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cTanaman ini masih terkunci!"));
            return;
        }

        ConfigurationSection global = plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops.global");
        for (String key : global.getKeys(false)) {
            if (plantName.contains(capitalize(key))) {
                new PlantDetailsMenu(plugin, player, "global", key, global.getConfigurationSection(key));
                InventoryClickEvent.getHandlerList().unregister(this);
                return;
            }
        }
    }
}
