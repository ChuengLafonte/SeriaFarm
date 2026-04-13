package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ToggleMenu implements Listener {
    
    public static boolean isDropToInvEnabled() {
        return SeriaFarmPlugin.getInstance().getConfigManager().getConfig("config.yml").getBoolean("settings.drop-to-inventory", false);
    }

    private final Component name = StaticColors.getHexMsg("&#cf8ff7&lToggle Menu");
    private final SeriaFarmPlugin plugin;

    public ToggleMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory togglemenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, this.name);
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");

        // Slot 11: Enable / Disable
        boolean enabled = config.getBoolean("settings.enabled", true);
        inventory.setItem(11, InvUtils.createItemStacks(enabled ? Material.LIME_WOOL : Material.RED_WOOL, 
            StaticColors.getHexMsg("&#cf8ff7Enable / Disable"), 
            "&7Toggle plugin activation.", 
            "&7Click to toggle plugin state.", 
            "", 
            "&eStatus: " + (enabled ? "&aEnabled" : "&cDisabled")));

        // Slot 13: Drop to Inventory
        boolean dropToInv = config.getBoolean("settings.drop-to-inventory", false);
        inventory.setItem(13, InvUtils.createItemStacks(Material.CHEST_MINECART, 
            StaticColors.getHexMsg("&#cf8ff7Drop To Inventory"), 
            "&7Drops go to inventory.", 
            "&7Else, they drop on block location.", 
            "", 
            "&eStatus: " + (dropToInv ? "&aEnabled" : "&cDisabled")));
        
        // Slot 15: Crop Growth Mode
        String growthMode = config.getString("settings.crop-growth-mode", "INSTANT");
        inventory.setItem(15, InvUtils.createItemStacks(Material.WHEAT, 
            StaticColors.getHexMsg("&#cf8ff7Crop Growth Mode"), 
            "&7VANILLA: Step-by-step growth.", 
            "&7INSTANT: Fully grown immediately.", 
            "", 
            "&eCurrent: " + (growthMode.equalsIgnoreCase("INSTANT") ? "&b&lINSTANT" : "&e&lVANILLA")));

        // Slot 19: Global Right-Click Harvest
        boolean globalRightClick = config.getBoolean("settings.global-right-click-harvest", true);
        inventory.setItem(19, InvUtils.createItemStacks(Material.WOODEN_HOE, 
            StaticColors.getHexMsg("&#cf8ff7Global Right-Click Harvest"), 
            "&7Enable harvesting by right-click.", 
            "&7&cONLY affects blocks outside regions.", 
            "&7Regions always follow their own rules.", 
            "", 
            "&eStatus: " + (globalRightClick ? "&aEnabled" : "&cDisabled")));

        // Slot 21: Global Replant
        boolean globalReplant = config.getBoolean("settings.global-replant", true);
        inventory.setItem(21, InvUtils.createItemStacks(Material.DIAMOND_HOE, 
            StaticColors.getHexMsg("&#cf8ff7Global Replant"), 
            "&7Automatically replant crops", 
            "&7after harvest in global areas.", 
            "&7&cHas NO EFFECT inside regions.", 
            "", 
            "&eStatus: " + (globalReplant ? "&aEnabled" : "&cDisabled")));

        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, "&cClose | Exit", "", "&7Closes the current GUI"));

        ItemStack purpleGlass = InvUtils.createItemStacks(Material.PURPLE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 20, 26, 27, 35, 36, 44, 46, 47, 48, 49, 50, 51, 52}) {
            inventory.setItem(n, purpleGlass);
        }
        
        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!event.getView().title().equals(this.name)) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        FileConfiguration config = plugin.getConfigManager().getConfig("config.yml");

        switch (event.getRawSlot()) {
            case 53:
                player.openInventory(new MainMenu(plugin).mainmenu(player));
                break;
            case 11:
                config.set("settings.enabled", !config.getBoolean("settings.enabled", true));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case 13:
                config.set("settings.drop-to-inventory", !config.getBoolean("settings.drop-to-inventory", false));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case 15:
                String current = config.getString("settings.crop-growth-mode", "INSTANT");
                String next = current.equalsIgnoreCase("INSTANT") ? "VANILLA" : "INSTANT";
                config.set("settings.crop-growth-mode", next);
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case 19:
                config.set("settings.global-right-click-harvest", !config.getBoolean("settings.global-right-click-harvest", true));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
            case 21:
                config.set("settings.global-replant", !config.getBoolean("settings.global-replant", true));
                plugin.getConfigManager().saveConfig("config.yml");
                player.openInventory(togglemenu(player));
                break;
        }
    }
}
