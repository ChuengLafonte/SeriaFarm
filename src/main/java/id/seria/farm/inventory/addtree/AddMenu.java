package id.seria.farm.inventory.addtree;

import id.seria.farm.SeriaFarmPlugin;
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
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AddMenu implements Listener {
    private final String name = StaticColors.getHexMsg("&#ffa500&lAdd Menu");

    public Inventory addmenu(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(player, 54, this.name);

        inventory.setItem(44, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE,
                StaticColors.getHexMsg("&aNext Page"), StaticColors.getHexMsg("&7Click To Go To The Next Page"), ""));
        inventory.setItem(36,
                InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&aPrevious Page"),
                        StaticColors.getHexMsg("&7Click To Go To The Previous Page"), ""));
        inventory.setItem(45,
                InvUtils.createItemStacks(Material.PLAYER_HEAD, StaticColors.getHexMsg("&#ffa500" + player.getName()),
                        "", StaticColors.getHexMsg("&7A Super Cool Farmer")));
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&4Close | Exit"),
                StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        // Slot 10: Global
        inventory.setItem(10,
                InvUtils.createItemStacks(Material.MINECART, StaticColors.getHexMsg("&#ffa500Global Setting"),
                        StaticColors.getHexMsg("&7Add Blocks To Global Regeneration"),
                        StaticColors.getHexMsg("&7Regeneration Should Be Set To Global")));

        List<String> list = SeriaFarmPlugin.getInstance().getRegenManager().getRegionNames();
        list.sort(Comparator.naturalOrder());

        int itemsPerPage = (page == 1) ? 27 : 28;

        if (PageUtil.isPageValid(list, page - 1, itemsPerPage)) {
            Objects.requireNonNull(inventory.getItem(36)).setType(Material.GREEN_STAINED_GLASS_PANE);
        }
        LocalizedName.set(Objects.requireNonNull(inventory.getItem(36)), String.valueOf(page - 1));

        if (PageUtil.isPageValid(list, page + 1, itemsPerPage)) {
            Objects.requireNonNull(inventory.getItem(44)).setType(Material.GREEN_STAINED_GLASS_PANE);
        }
        LocalizedName.set(Objects.requireNonNull(inventory.getItem(44)), String.valueOf(page + 1));

        ItemStack glass = InvUtils.createItemStacks(Material.ORANGE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 46, 47, 48, 49, 50, 51, 52 }) {
            inventory.setItem(n, glass);
        }

        int slot = (page == 1) ? 11 : 10;
        List<String> pageItems = PageUtil.getpageitems(list, page, itemsPerPage);

        List<Integer> skipSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47,
                48, 49, 50, 51, 52, 53);

        for (String regionName : pageItems) {
            while (skipSlots.contains(slot))
                slot++;
            if (slot >= 54)
                break;

            inventory.setItem(slot,
                    InvUtils.createItemStacks(Material.CHEST_MINECART,
                            StaticColors.getHexMsg("&#ffa500Region Setting : [" + regionName + "]"),
                            StaticColors.getHexMsg("&7Add Blocks To Region Regeneration"),
                            StaticColors.getHexMsg("&7Regeneration Should Be Set To Regional")));
            slot++;
        }

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(this.name))
            return;
        event.setCancelled(true);

        if (event.getRawSlot() >= 54)
            return;
        Player player = (Player) event.getWhoClicked();

        if (event.getRawSlot() == 53) {
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        int currentPage = Integer.parseInt(LocalizedName.get(event.getInventory().getItem(44))) - 1;

        if (event.getRawSlot() == 36 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            player.openInventory(addmenu(player, currentPage - 1));
        } else if (event.getRawSlot() == 44 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            player.openInventory(addmenu(player, currentPage + 1));
        } else if (event.getRawSlot() == 10 && currentPage == 1) {
            // Open AddBlocksMenu for global
            player.openInventory(new AddBlocksMenu().addblocks_menu(player, "global"));
        } else {
            String regionName = InvUtils.extractStr(clicked.getItemMeta().getDisplayName());
            if (regionName != null) {
                player.openInventory(new AddBlocksMenu().addblocks_menu(player, regionName));
            }
        }
    }
}
