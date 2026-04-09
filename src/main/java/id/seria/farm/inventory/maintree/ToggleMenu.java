package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ToggleMenu implements Listener {

    private final String name = StaticColors.getHexMsg("&#cf8ff7&lToggle Menu");

    public Inventory togglemenu(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, this.name);
        YamlConfiguration config = (YamlConfiguration) SeriaFarmPlugin.getInstance().getConfig();

        inventory.setItem(11, InvUtils.createItemStacks(Material.GREEN_WOOL, StaticColors.getHexMsg("&#cf8ff7Enable / Disable"), "&7Toggle plugin activation.", "&7Click to toggle plugin state.", "", "&eStatus: " + (config.getBoolean("settings.enabled") ? "&aEnabled" : "&cDisabled")));
        inventory.setItem(13, InvUtils.createItemStacks(Material.CHEST_MINECART, StaticColors.getHexMsg("&#cf8ff7Drop To Inventory"), "&7Drops go to inventory.", "&7Else, they drop on block location.", "", "&eStatus: " + (config.getBoolean("settings.drop-to-inventory") ? "&aEnabled" : "&cDisabled")));
        
        // Mocking other slots matching UBR
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, "&cClose | Exit", "", "&7Closes the current GUI"));

        ItemStack purpleGlass = InvUtils.createItemStacks(Material.PURPLE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 49, 50, 51, 52}) {
            inventory.setItem(n, purpleGlass);
        }
        
        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(this.name)) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        YamlConfiguration config = (YamlConfiguration) SeriaFarmPlugin.getInstance().getConfig();

        switch (event.getRawSlot()) {
            case 53:
                player.openInventory(new MainMenu(SeriaFarmPlugin.getInstance()).mainmenu(player));
                break;
            case 11:
                config.set("settings.enabled", !config.getBoolean("settings.enabled"));
                SeriaFarmPlugin.getInstance().saveConfig();
                player.openInventory(togglemenu(player));
                break;
            case 13:
                config.set("settings.drop-to-inventory", !config.getBoolean("settings.drop-to-inventory"));
                SeriaFarmPlugin.getInstance().saveConfig();
                player.openInventory(togglemenu(player));
                break;
        }
    }
}
