package id.seria.farm.inventory.edittree.RegionEdit;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import id.seria.farm.managers.GuiManager;

public class RegionSelectionMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private int page;

    public RegionSelectionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public int getPage() { return page; }

    public Inventory reg_sel(Player player, int page) {
        this.page = page;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player_name%", player.getName());

        Inventory inventory = plugin.getGuiManager().createInventory("region-selection-menu", placeholders);
        
        List<String> list = plugin.getRegenManager().getRegionNames();
        list.sort(Comparator.naturalOrder());

        int itemsPerPage = 21; // Available slots for regions
        List<String> pageItems = PageUtil.getpageitems(list, page, itemsPerPage);
        
        // Update Nav buttons aesthetics based on page validity
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

            ItemStack item = InvUtils.createItemStacks(Material.CHEST_MINECART, 
                StaticColors.getHexMsg("&#ffa500Region Setting : [" + regionName + "]"), 
                StaticColors.getHexMsg("&7Manage Region Regeneration"), "");
            LocalizedName.set(item, regionName);
            inventory.setItem(slot, item);
            slot++;
        }

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("region-selection-menu")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String action = LocalizedName.get(clicked);

        switch (action) {
            case "open_global":
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlocksMenu(plugin).blockmenu(player, 1)));
                break;
            case "prev_page":
                if (clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(reg_sel(player, page - 1)));
                }
                break;
            case "next_page":
                if (clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(reg_sel(player, page + 1)));
                }
                break;
            case "close_to_main":
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new MainMenu(plugin).mainmenu(player)));
                break;
            default:
                // Handle region selection
                if (!action.isEmpty() && !action.equals("info_player")) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new PreRegionMenu(plugin).preregenmenu(player, action)));
                }
                break;
        }
    }
}
