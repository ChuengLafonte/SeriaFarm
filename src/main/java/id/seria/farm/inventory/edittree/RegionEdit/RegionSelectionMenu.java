package id.seria.farm.inventory.edittree.RegionEdit;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.List;

public class RegionSelectionMenu implements Listener, InventoryHolder {
    private static final Component NAME = StaticColors.getHexMsg("&#ffa500&lRegion Menu");
    private final SeriaFarmPlugin plugin;
    private int page;

    public RegionSelectionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public int getPage() { return page; }

    public Inventory reg_sel(Player player, int page) {
        this.page = page;
        Inventory inventory = Bukkit.createInventory(this, 54, NAME);
        int itemsPerPage = 28;
        
        inventory.setItem(44, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&#ffa500Next Page"), StaticColors.getHexMsg("&7Click To Go To The Next Page"), ""));
        inventory.setItem(36, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&#ffa500Previous Page"), StaticColors.getHexMsg("&7Click To Go To The Previous Page"), ""));
        inventory.setItem(45, InvUtils.createItemStacks(Material.PLAYER_HEAD, StaticColors.getHexMsg("&#ffa500" + player.getName()), StaticColors.getHexMsg("&7A Super Cool Farmer"), ""));
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        if (page == 1) {
            inventory.setItem(10, InvUtils.createItemStacks(Material.MINECART, StaticColors.getHexMsg("&#ffa500Global Setting"), StaticColors.getHexMsg("&7Manage Global Regeneration"), ""));
        }

        List<String> list = plugin.getRegenManager().getRegionNames();
        List<String> pageItems = PageUtil.getpageitems(list, page, itemsPerPage);
        
        if (PageUtil.isPageValid(list, page - 1, itemsPerPage)) {
            ItemStack item = inventory.getItem(36);
            if (item != null) inventory.setItem(36, item.withType(Material.GREEN_STAINED_GLASS_PANE));
        }

        if (PageUtil.isPageValid(list, page + 1, itemsPerPage)) {
            ItemStack item = inventory.getItem(44);
            if (item != null) inventory.setItem(44, item.withType(Material.GREEN_STAINED_GLASS_PANE));
        }

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

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        String title = SeriaFarmPlugin.MINI_MESSAGE.serialize(event.getView().title());
        boolean isHolder = event.getInventory().getHolder() instanceof RegionSelectionMenu;
        boolean isTitle = title.contains("Region Menu");

        if (!isHolder && !isTitle) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        
        // Debug Logging
        Bukkit.getLogger().info("[SeriaFarm Debug] Click detected in RegionSelectionMenu. Slot: " + event.getRawSlot() + " | Title Match: " + isTitle + " | Holder Match: " + isHolder);

        if (event.getRawSlot() >= 54) return;

        if (event.getRawSlot() == 53) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new MainMenu(plugin).mainmenu(player)));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        RegionSelectionMenu holder;
        if (isHolder) {
            holder = (RegionSelectionMenu) event.getInventory().getHolder();
        } else {
            // Fallback to title-based identification logic if holder fails
            return; 
        }

        int currentPage = holder.getPage();

        if (event.getRawSlot() == 36 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(reg_sel(player, currentPage - 1)));
        } else if (event.getRawSlot() == 44 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(reg_sel(player, currentPage + 1)));
        } else if (event.getRawSlot() == 10 && currentPage == 1) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlocksMenu(plugin).blockmenu(player, 1)));
        } else {
            if (event.getRawSlot() >= 10 && event.getRawSlot() <= 43) {
                String regionName = LocalizedName.get(clicked);
                if (regionName.equals("0")) {
                     regionName = InvUtils.extractStr(SeriaFarmPlugin.MINI_MESSAGE.serialize(clicked.getItemMeta().displayName()));
                }
                
                if (regionName != null && !regionName.equals("0")) {
                    final String finalRegion = regionName;
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new PreRegionMenu(plugin).preregenmenu(player, finalRegion)));
                }
            }
        }
    }
}
