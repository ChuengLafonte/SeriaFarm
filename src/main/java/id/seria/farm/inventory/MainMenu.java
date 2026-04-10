package id.seria.farm.inventory;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import id.seria.farm.listeners.ChatInputListener;
import id.seria.farm.inventory.addtree.AddMenu;
import id.seria.farm.inventory.edittree.RegionEdit.RegionSelectionMenu;
import id.seria.farm.inventory.maintree.ToggleMenu;

public class MainMenu implements Listener {

    private final String name = StaticColors.getHexMsg("&#c8a100&lSeriaFarm Menu");
    private final SeriaFarmPlugin plugin;

    public MainMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory mainmenu(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 36, this.name);
        
        // Toggle (Slot 11) - Mocking config check
        inventory.setItem(11, InvUtils.createItemStacks(Material.GREEN_WOOL, StaticColors.getHexMsg("&#fbca00Toggle Menu"), "&7Enable or Disable Plugin Features", "", "&eStatus: &aEnabled"));
        
        // Prefix (Slot 13)
        inventory.setItem(13, InvUtils.createItemStacks(Material.NAME_TAG, StaticColors.getHexMsg("&#fbca00Plugin Prefix"), "&7Set the prefix for messages.", "&7You can use color codes.", "", "&eCurrent: " + StaticColors.getHexMsg("&6&lSeriaFarm &8»")));
        
        // Add (Slot 15)
        inventory.setItem(15, InvUtils.createItemStacks(Material.NETHER_STAR, StaticColors.getHexMsg("&#fbca00Add More Blocks"), "&7Add more blocks to regeneration.", "&7Choose which region blocks should be added."));
        
        // Wand (Slot 20)
        inventory.setItem(20, InvUtils.createItemStacks(Material.IRON_AXE, StaticColors.getHexMsg("&#fbca00Regen Wand"), "&7Receive a regen wand.", "&7Used to select Pos1 and Pos2."));
        
        // Reload (Slot 22)
        inventory.setItem(22, InvUtils.createItemStacks(Material.CLOCK, StaticColors.getHexMsg("&#fbca00Reload Plugin"), "&7Reloads the plugin.", ""));
        
        // Edit (Slot 24)
        inventory.setItem(24, InvUtils.createItemStacks(Material.DIAMOND_ORE, StaticColors.getHexMsg("&#fbca00Edit Menu"), "&7Edit how the block regenerates.", ""));
        
        // Player Head (Slot 27)
        inventory.setItem(27, InvUtils.createItemStacks(Material.PLAYER_HEAD, "&e" + player.getName(), "&7A super cool farmer.", ""));
        
        // Exit (Slot 35)
        inventory.setItem(35, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&#fbca00Close | Exit"), "&7Closes the current GUI.", ""));

        ItemStack glass = InvUtils.createItemStacks(Material.YELLOW_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 28, 29, 30, 31, 32, 33, 34}) {
            inventory.setItem(n, glass);
        }
        
        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(this.name)) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        
        switch (event.getRawSlot()) {
            case 11: // Toggle
                player.openInventory(new ToggleMenu().togglemenu(player));
                break;
            case 13: // Prefix
                player.closeInventory();
                ChatInputListener.requestInput(player, "Enter Prefix", null, input -> {
                    plugin.getConfig().set("settings.prefix", input);
                    plugin.saveConfig();
                    player.openInventory(mainmenu(player));
                }, () -> player.openInventory(mainmenu(player)));
                break;
            case 15: // Add
                player.openInventory(new AddMenu().addmenu(player, 1));
                break;
            case 20: // Wand
                ItemStack wand = new ItemStack(Material.STONE_AXE, 1);
                ItemMeta meta = wand.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.YELLOW + "REGEN WAND");
                    meta.setCustomModelData(20);
                    wand.setItemMeta(meta);
                }
                player.getInventory().addItem(wand);
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &fYou have received a Regen Wand"));
                break;
            case 22: // Reload
                plugin.getConfigManager().reloadConfigs();
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &fPlugin Reloaded"));
                break;
            case 24: // Edit
                player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1));
                break;
            case 35: // Close
                player.closeInventory();
                break;
            // More slots to be linked after implementing other menus
        }
    }
}
