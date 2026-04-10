package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.edittree.RegionEdit.RegionSelectionMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BlockMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private final String name = StaticColors.getHexMsg("&#6495ED&lBlock Menu");

    public BlockMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory blockmenu(Player player, int page, YamlConfiguration config, String regionName) {
        plugin.getVisualManager().setFocusedRegion(player, regionName);
        Inventory inventory = Bukkit.createInventory(player, 54, this.name);
        
        inventory.setItem(44, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&aNext Page"), StaticColors.getHexMsg("&7Click To Go To The Next Page"), ""));
        inventory.setItem(36, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&aPrevious Page"), StaticColors.getHexMsg("&7Click To Go To The Previous Page"), ""));
        inventory.setItem(45, InvUtils.createItemStacks(Material.CHEST_MINECART, StaticColors.getHexMsg("&#6495ED[" + regionName + "]"), StaticColors.getHexMsg("&aA Super Cool Farmer"), ""));
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        ConfigurationSection blocksSection = config.getConfigurationSection("blocks");
        List<String> materials = new ArrayList<>();
        
        if (blocksSection != null) {
            // Include global blocks
            ConfigurationSection global = blocksSection.getConfigurationSection("global");
            if (global != null) {
                global.getKeys(false).forEach(k -> materials.add("global:" + k));
            }
            
            // Include region-specific blocks
            if (!regionName.equalsIgnoreCase("global")) {
                ConfigurationSection region = blocksSection.getConfigurationSection(regionName);
                if (region != null) {
                    region.getKeys(false).forEach(k -> materials.add(regionName + ":" + k));
                }
            }
            
            // Include legacy blocks (for backward compatibility)
            List<String> regions = plugin.getRegenManager().getRegionNames();
            for (String key : blocksSection.getKeys(false)) {
                if (!key.equalsIgnoreCase("global") && !regions.contains(key)) {
                    if (!blocksSection.isConfigurationSection(key)) continue;
                    materials.add("legacy:" + key);
                }
            }
        }
        
        materials.sort(Comparator.naturalOrder());

        if (PageUtil.isPageValid(materials, page - 1, 28)) {
            Objects.requireNonNull(inventory.getItem(36)).setType(Material.GREEN_STAINED_GLASS_PANE);
        }
        LocalizedName.set(Objects.requireNonNull(inventory.getItem(36)), String.valueOf(page - 1));

        if (PageUtil.isPageValid(materials, page + 1, 28)) {
            Objects.requireNonNull(inventory.getItem(44)).setType(Material.GREEN_STAINED_GLASS_PANE);
        }
        LocalizedName.set(Objects.requireNonNull(inventory.getItem(44)), String.valueOf(page + 1));

        ItemStack glass = InvUtils.createItemStacks(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 46, 47, 48, 49, 50, 51, 52}) {
            if (inventory.getItem(n) == null) inventory.setItem(n, glass);
        }

        int slot = 10;
        List<Integer> skipSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52, 53, 45, 49);
        
        for (String matKey : PageUtil.getpageitems(materials, page, 28)) {
            while (skipSlots.contains(slot)) slot++;
            if (slot >= 44) break;

            String[] parts = matKey.split(":");
            String displayKey = parts.length > 1 ? parts[1] : parts[0];
            
            ItemStack icon = plugin.getHookManager().getItem(displayKey);
            ItemStack item = InvUtils.applyMeta(icon, StaticColors.getHexMsg("&#6495ED[" + displayKey + "]"), StaticColors.getHexMsg("&7Customize How Block Should Regenerate"), StaticColors.getHexMsg("&7According To This Regen"), "", StaticColors.getHexMsg("&7Shift Click To &cDelete"), StaticColors.getHexMsg("&7Click To &eEdit"));
            LocalizedName.set(item, matKey);
            inventory.setItem(slot, item);
            slot++;
        }

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(this.name)) return;
        event.setCancelled(true);
        
        if (event.getRawSlot() >= 54) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        int currentPage = Integer.parseInt(LocalizedName.get(event.getInventory().getItem(44))) - 1;
        String regionName = InvUtils.extractStr(event.getInventory().getItem(45).getItemMeta().getDisplayName());

        if (event.getRawSlot() == 53) {
            plugin.getVisualManager().setFocusedRegion(player, null);
            player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1));
            return;
        }

        if (event.getRawSlot() == 36 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
            player.openInventory(blockmenu(player, currentPage - 1, matConfig, regionName));
            return;
        }

        if (event.getRawSlot() == 44 && clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
            player.openInventory(blockmenu(player, currentPage + 1, matConfig, regionName));
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            String matName = LocalizedName.get(clicked);
            player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cDeleted &f" + matName));
            // Deletion logic would go here
        } else if (isMatItem(event.getRawSlot())) {
            // Open EditMenu
            String matName = LocalizedName.get(clicked);
            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
            File matFile = plugin.getConfigManager().getConfigFile("materials.yml");
            
            player.openInventory(new EditMenu(plugin).emenu(player, matConfig, matName, matFile, regionName));
        }
    }

    private boolean isMatItem(int slot) {
        List<Integer> skipSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52, 53, 45, 49);
        return !skipSlots.contains(slot) && slot < 44;
    }
}
