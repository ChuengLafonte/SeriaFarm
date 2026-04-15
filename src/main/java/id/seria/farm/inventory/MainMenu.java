package id.seria.farm.inventory;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import id.seria.farm.managers.GuiManager;
import id.seria.farm.listeners.ChatInputListener;
import id.seria.farm.inventory.maintree.ToggleMenu;
import java.util.HashMap;
import java.util.Map;

public class MainMenu implements Listener {

    private final SeriaFarmPlugin plugin;

    public MainMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory mainmenu(Player player) {
        boolean enabled = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.enabled", true);
        String prefStr = plugin.getConfigManager().getConfig("messages.yml").getString("prefix", "");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%toggle_material%", enabled ? "LIME_WOOL" : "RED_WOOL");
        placeholders.put("%enabled_status%", enabled ? "&aEnabled" : "&cDisabled");
        placeholders.put("%prefix%", prefStr);
        placeholders.put("%player_name%", player.getName());

        return plugin.getGuiManager().createInventory("main-menu", placeholders);
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("main-menu")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        String action = id.seria.farm.inventory.utils.LocalizedName.get(clicked);
        if (action == null) return;
        
        switch (action) {
            case "info_player":
                break;
            case "open_toggle":
                player.openInventory(new ToggleMenu(plugin).togglemenu(player));
                break;
            case "change_prefix":
                player.closeInventory();
                ChatInputListener.requestInput(player, "Enter Prefix", null, input -> {
                    plugin.getConfigManager().getConfig("messages.yml").set("prefix", input);
                    plugin.getConfigManager().saveConfig("messages.yml");
                    player.openInventory(mainmenu(player));
                }, () -> player.openInventory(mainmenu(player)));
                break;
            case "open_add":
                player.openInventory(new id.seria.farm.inventory.addtree.AddMenu().addmenu(player, 1));
                break;
            case "get_wand":
                player.getInventory().addItem(id.seria.farm.utils.WandUtils.getWand());
                plugin.getConfigManager().sendPrefixedMessage(player, "&fYou have received a Regen Wand");
                player.closeInventory();
                break;
            case "reload_plugin":
                plugin.getConfigManager().reloadConfigs();
                plugin.getConfigManager().sendPrefixedMessage(player, "&fPlugin Reloaded");
                player.closeInventory();
                break;
            case "open_edit":
                player.openInventory(new id.seria.farm.inventory.edittree.RegionEdit.RegionSelectionMenu(plugin).reg_sel(player, 1));
                break;
            case "open_watering_tools":
                player.openInventory(new id.seria.farm.inventory.watering.WateringToolsMenu(plugin).open(player));
                break;
            case "close_menu":
                player.closeInventory();
                break;
        }
    }
}
