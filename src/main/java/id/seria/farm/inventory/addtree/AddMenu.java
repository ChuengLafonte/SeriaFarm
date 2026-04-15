package id.seria.farm.inventory.addtree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import id.seria.farm.managers.GuiManager;

public class AddMenu implements Listener {
    private final SeriaFarmPlugin plugin = SeriaFarmPlugin.getInstance();
    private int page;

    public Inventory addmenu(Player player, int page) {
        this.page = page;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player_name%", player.getName());

        Inventory inventory = plugin.getGuiManager().createInventory("add-main-menu", placeholders);

        List<String> list = plugin.getRegenManager().getRegionNames();
        list.sort(Comparator.naturalOrder());

        int itemsPerPage = 21; // Available slots for regions
        List<String> pageItems = PageUtil.getpageitems(list, page, itemsPerPage);

        // Update Nav buttons aesthetics based on page validity (Dynamic coloring)
        if (PageUtil.isPageValid(list, page - 1, itemsPerPage)) {
            ItemStack prev = inventory.getItem(36);
            if (prev != null) inventory.setItem(36, InvUtils.applyMeta(prev.withType(Material.GREEN_STAINED_GLASS_PANE), null));
        }
        if (PageUtil.isPageValid(list, page + 1, itemsPerPage)) {
            ItemStack next = inventory.getItem(44);
            if (next != null) inventory.setItem(44, InvUtils.applyMeta(next.withType(Material.GREEN_STAINED_GLASS_PANE), null));
        }

        int slot = 0;
        for (String regionName : pageItems) {
            // Find next empty slot
            while (slot < inventory.getSize() && inventory.getItem(slot) != null && inventory.getItem(slot).getType() != Material.AIR) {
                slot++;
            }
            if (slot >= inventory.getSize()) break;

            ItemStack regionItem = InvUtils.createItemStacks(Material.CHEST_MINECART,
                            StaticColors.getHexMsg("&#ffa500Region Setting : [" + regionName + "]"),
                            StaticColors.getHexMsg("&7Add Blocks To Region Regeneration"),
                            StaticColors.getHexMsg("&7Regeneration Should Be Set To Regional"));
            LocalizedName.set(regionItem, "region:" + regionName);
            inventory.setItem(slot, regionItem);
            slot++;
        }

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("add-main-menu")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String action = LocalizedName.get(clicked);

        switch (action) {
            case "close_menu":
                player.closeInventory();
                break;
            case "add_global":
                player.openInventory(new id.seria.farm.inventory.addtree.AddBlocksMenu().addblocks_menu(player, "global"));
                break;
            case "prev_page":
                if (clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
                    player.openInventory(addmenu(player, page - 1));
                }
                break;
            case "next_page":
                if (clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
                    player.openInventory(addmenu(player, page + 1));
                }
                break;
            default:
                if (action.startsWith("region:")) {
                    String regionName = action.replace("region:", "");
                    player.openInventory(new id.seria.farm.inventory.addtree.AddBlocksMenu().addblocks_menu(player, regionName));
                }
                break;
        }
    }
}
