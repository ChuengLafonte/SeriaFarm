package id.seria.farm.inventory.edittree.RegionEdit;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RegionSelectionMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private final String name = StaticColors.getHexMsg("&#ffa500&lRegion Menu");

    public RegionSelectionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory reg_sel(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(player, 54, this.name);
        
        inventory.setItem(44, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&#ffa500Next Page"), StaticColors.getHexMsg("&7Click To Go To The Next Page"), ""));
        inventory.setItem(36, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&#ffa500Previous Page"), StaticColors.getHexMsg("&7Click To Go To The Previous Page"), ""));
        inventory.setItem(45, InvUtils.createItemStacks(Material.PLAYER_HEAD, StaticColors.getHexMsg("&#ffa500" + player.getName()), StaticColors.getHexMsg("&7A Super Cool Farmer"), ""));
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        if (page == 1) {
            inventory.setItem(10, InvUtils.createItemStacks(Material.MINECART, StaticColors.getHexMsg("&#ffa500Global Setting"), StaticColors.getHexMsg("&7Manage Global Regeneration"), ""));
        }

        List<String> list = plugin.getRegenManager().getRegionNames();
        if (!list.isEmpty()) {
            // UBR has a weird addFirst logic here, but I'll stick to a cleaner pagination
        }

        List<String> pageItems = PageUtil.getpageitems(list, page, 28);
        
        if (PageUtil.isPageValid(list, page - 1, 28)) {
            Objects.requireNonNull(inventory.getItem(36)).setType(Material.GREEN_STAINED_GLASS_PANE);
        }
        LocalizedName.set(Objects.requireNonNull(inventory.getItem(36)), String.valueOf(page - 1));

        if (PageUtil.isPageValid(list, page + 1, 28)) {
            Objects.requireNonNull(inventory.getItem(44)).setType(Material.GREEN_STAINED_GLASS_PANE);
        }
        LocalizedName.set(Objects.requireNonNull(inventory.getItem(44)), String.valueOf(page + 1));

        int slot = 10;
        List<Integer> skipSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 49, 50, 51, 52, 53, 45);

        for (String regionName : pageItems) {
            if (page == 1 && slot == 10) { slot++; continue; }
            while (skipSlots.contains(slot)) slot++;
            if (slot >= 44) break;

            ItemStack item = InvUtils.createItemStacks(Material.CHEST_MINECART, StaticColors.getHexMsg("&#ffa500Region Setting : [" + regionName + "]"), StaticColors.getHexMsg("&7Manage Region Regeneration"), "");
            LocalizedName.set(item, regionName);
            inventory.setItem(slot, item);
            slot++;
        }

        ItemStack glass = InvUtils.createItemStacks(Material.ORANGE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 46, 47, 48, 49, 50, 51, 52}) {
            if (inventory.getItem(n) == null) inventory.setItem(n, glass);
        }

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(this.name)) return;
        event.setCancelled(true);
        
        if (event.getRawSlot() >= 54) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getRawSlot() == 53) {
            player.openInventory(new MainMenu(plugin).mainmenu(player));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int currentPage = Integer.parseInt(LocalizedName.get(event.getInventory().getItem(44))) - 1;

        if (event.getRawSlot() == 36 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            player.openInventory(reg_sel(player, currentPage - 1));
        } else if (event.getRawSlot() == 44 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            player.openInventory(reg_sel(player, currentPage + 1));
        } else if (event.getRawSlot() == 10 && currentPage == 1) {
            player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlocksMenu(plugin).blockmenu(player, 1));
        } else {
            String regionName = LocalizedName.get(clicked);
            if (regionName != null) {
                player.openInventory(new PreRegionMenu(plugin).preregenmenu(player, regionName));
            }
        }
    }
}
