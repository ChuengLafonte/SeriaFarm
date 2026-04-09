package id.seria.farm.inventory.edittree.RegionEdit;

import id.seria.farm.SeriaFarmPlugin;
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

public class PreRegionMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private static final String name = StaticColors.getHexMsg("&9&lRegion Info Menu");

    public PreRegionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory preregenmenu(Player player, String string) {
        Inventory inventory = Bukkit.createInventory(player, 36, name);
        
        // Mocking some data for the info item
        inventory.setItem(11, InvUtils.createItemStacks(Material.SEAGRASS, StaticColors.getHexMsg("&9[" + string + "]"), StaticColors.getHexMsg("&7World: world"), StaticColors.getHexMsg("&7Pos 1: 0 0 0"), StaticColors.getHexMsg("&7Pos 2: 10 10 10"), "", StaticColors.getHexMsg("&7Click To &aTeleport")));
        
        inventory.setItem(20, InvUtils.createItemStacks(Material.GREEN_STAINED_GLASS_PANE, StaticColors.getHexMsg("&9Regeneration Status"), StaticColors.getHexMsg("&7Toggle Whether This Region Has Regeneration"), "", StaticColors.getHexMsg("&7Current Status: &aEnabled")));
        inventory.setItem(13, InvUtils.createItemStacks(Material.IRON_BARS, StaticColors.getHexMsg("&9Per Region Regeneration"), StaticColors.getHexMsg("&7Should Region Regenerate As Per Region Setting"), "", StaticColors.getHexMsg("&7Current Status: &aEnabled")));
        inventory.setItem(22, InvUtils.createItemStacks(Material.GOLDEN_PICKAXE, StaticColors.getHexMsg("&9Per Region Perms"), StaticColors.getHexMsg("&7Does Player Require Permission"), "", StaticColors.getHexMsg("&7Current Status: &cDisabled")));
        inventory.setItem(15, InvUtils.createItemStacks(Material.GRASS_BLOCK, StaticColors.getHexMsg("&9Allow Block Place"), StaticColors.getHexMsg("&7Players Can Place Block"), "", StaticColors.getHexMsg("&7Current Status: &cDisabled")));
        
        inventory.setItem(24, InvUtils.createItemStacks(Material.MINECART, StaticColors.getHexMsg("&9Edit Blocks Menu"), StaticColors.getHexMsg("&7Edit How Block Regenerates")));
        inventory.setItem(35, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        ItemStack blueGlass = InvUtils.createItemStacks(Material.BLUE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 28, 29, 30, 31, 32, 33, 34}) {
            inventory.setItem(n, blueGlass);
        }
        
        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(name)) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        if (event.getRawSlot() == 35) {
            player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1));
            return;
        }

        // Add more logic here to handle toggles
        if (event.getRawSlot() == 24) {
             String regionName = InvUtils.extractStr(event.getInventory().getItem(11).getItemMeta().getDisplayName());
             // This would open BlockMenu
             player.sendMessage(StaticColors.getHexMsg("&eOpening Edit Blocks Menu for &b" + regionName));
        }
    }
}
