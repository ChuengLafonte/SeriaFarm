package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import id.seria.farm.managers.GuiManager;
import java.util.HashMap;
import java.util.Map;

public class ToggleMenu implements Listener {

    public static boolean isDropToInvEnabled() {
        return SeriaFarmPlugin.getInstance().getConfigManager().getConfig("config.yml")
                .getBoolean("settings.drop-to-inventory", false);
    }

    private final SeriaFarmPlugin plugin;

    public ToggleMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory togglemenu(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");

        Map<String, String> placeholders = new HashMap<>();

        boolean enabled = config.getBoolean("settings.enabled", true);
        placeholders.put("%enable_material%", enabled ? "LIME_WOOL" : "RED_WOOL");
        placeholders.put("%enable_status%", enabled ? "&aEnabled" : "&cDisabled");

        boolean dropToInv = config.getBoolean("settings.drop-to-inventory", false);
        placeholders.put("%drop_status%", dropToInv ? "&aEnabled" : "&cDisabled");

        String growthMode = config.getString("settings.crop-growth-mode", "INSTANT");
        placeholders.put("%growth_mode%", growthMode.equalsIgnoreCase("INSTANT") ? "&b&lINSTANT" : "&e&lVANILLA");

        boolean globalRightClick = config.getBoolean("settings.global-right-click-harvest", true);
        placeholders.put("%harvest_status%", globalRightClick ? "&aEnabled" : "&cDisabled");

        boolean globalReplant = config.getBoolean("settings.global-replant", true);
        placeholders.put("%replant_status%", globalReplant ? "&aEnabled" : "&cDisabled");
        placeholders.put("%player_name%", player.getName());

        return plugin.getGuiManager().createInventory("toggle-menu", placeholders);
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder))
            return;
        if (!holder.getMenuKey().equals("toggle-menu"))
            return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");

        String action = id.seria.farm.inventory.utils.LocalizedName.get(clicked);
        if (action == null) return;

        switch (action) {
            case "info_player":
                // Just a display item, no action needed but we consume it
                break;
            case "close_menu":
                player.openInventory(new MainMenu(plugin).mainmenu(player));
                break;
            case "toggle_enabled":
                config.set("settings.enabled", !config.getBoolean("settings.enabled", true));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case "toggle_drop":
                config.set("settings.drop-to-inventory", !config.getBoolean("settings.drop-to-inventory", false));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case "toggle_growth":
                String current = config.getString("settings.crop-growth-mode", "INSTANT");
                String next = current.equalsIgnoreCase("INSTANT") ? "VANILLA" : "INSTANT";
                config.set("settings.crop-growth-mode", next);
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case "toggle_harvest":
                config.set("settings.global-right-click-harvest",
                        !config.getBoolean("settings.global-right-click-harvest", true));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case "toggle_replant":
                config.set("settings.global-replant", !config.getBoolean("settings.global-replant", true));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
        }
    }
}
