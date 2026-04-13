package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.edittree.RegionEdit.RegionSelectionMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class BlockMenu implements Listener, InventoryHolder {
    private static final Component NAME = StaticColors.getHexMsg("&#6495ED&lBlock Menu");
    private final SeriaFarmPlugin plugin;
    private int page;
    private String regionName;

    public BlockMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public int getPage() { return page; }
    public String getRegionName() { return regionName; }

    public Inventory blockmenu(Player player, int page, YamlConfiguration config, String regionName) {
        this.page = page;
        this.regionName = regionName;
        plugin.getVisualManager().setFocusedRegion(player, regionName);
        
        ConfigurationSection blocksSection = config.getConfigurationSection("crops");
        List<String> materials = new ArrayList<>();
        
        if (blocksSection != null) {
            // ONLY Include region-specific blocks
            if (!regionName.equalsIgnoreCase("global")) {
                ConfigurationSection region = blocksSection.getConfigurationSection(regionName);
                if (region != null) {
                    region.getKeys(false).forEach(k -> materials.add(regionName + ":" + k));
                }
            }
        }
        
        materials.sort(Comparator.naturalOrder());

        Component title = NAME.append(StaticColors.getHexMsg(" &7(" + materials.size() + ")"));
        Inventory inventory = Bukkit.createInventory(this, 54, title);

        // Navigation & Info (Dashboard Style)
        inventory.setItem(48, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&#6495ED&l« Previous Page"), "&7Go back", ""));
        
        // Slot 49: Summary Book
        FileConfiguration regConfig = plugin.getConfigManager().getConfig("regions.yml");
        String regPath = "regions." + regionName + ".";
        boolean isEnabled = regConfig.getBoolean(regPath + "enabled", true);
        boolean isPerRegion = regConfig.getBoolean(regPath + "per-region-regen", true);

        inventory.setItem(49, InvUtils.createItemStacks(Material.BOOK, StaticColors.getHexMsg("&#6495ED&lRegion Summary"), 
            "&7Region: &f" + regionName,
            "&7Status: " + (isEnabled ? "&aActive" : "&cPaused"),
            "&7Mode: " + (isPerRegion ? "&bRegional" : "&6Global-Only"),
            "",
            "&7Regional Overrides: &f" + materials.size(),
            "&8&o(Global blocks managed in Global Menu)"));


        inventory.setItem(50, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&#6495ED&lNext Page »"), "&7Go forward", ""));
        
        inventory.setItem(45, InvUtils.createItemStacks(Material.CHEST_MINECART, StaticColors.getHexMsg("&#6495ED&lRegion Selection"), 
            "&7Viewing: &b" + regionName,
            "",
            "&eClick to return to list."));
            
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cBack to Regions"), "&7Return to the main list.", ""));

        // Initialize pagination slots even if invalid (so they can be safely meta-updated)
        inventory.setItem(36, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&cNo Previous Page"), "", ""));
        inventory.setItem(44, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, StaticColors.getHexMsg("&cNo Next Page"), "", ""));

        if (PageUtil.isPageValid(materials, page - 1, 28)) {
            inventory.setItem(36, InvUtils.createItemStacks(Material.GREEN_STAINED_GLASS_PANE, StaticColors.getHexMsg("&#6495ED&l« Previous Page"), "&7Back to page " + (page - 1), ""));
        }
        LocalizedName.set(Objects.requireNonNull(inventory.getItem(36)), String.valueOf(page - 1));

        if (PageUtil.isPageValid(materials, page + 1, 28)) {
            inventory.setItem(44, InvUtils.createItemStacks(Material.GREEN_STAINED_GLASS_PANE, StaticColors.getHexMsg("&#6495ED&lNext Page »"), "&7Advance to page " + (page + 1), ""));
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
            String source = parts[0];
            String displayKey = parts[1];
            
            String path = "crops." + source + "." + displayKey;
            int xp = config.getInt(path + ".rewards.xp", 0);
            int delay = config.getInt(path + ".regen-delay", 20);
            boolean isGlobal = source.equalsIgnoreCase("global");

            ItemStack icon = plugin.getHookManager().getItem(displayKey);
            ItemStack item = InvUtils.applyMeta(icon, 
                StaticColors.getHexMsg("&#6495ED&l" + displayKey.replace("_", " ")), 
                isGlobal ? "&8Inherited from Global" : "&bRegional Override Settings",
                "",
                StaticColors.getHexMsg("&#E7CBB3Regen Delay: &f" + delay + "s"),
                StaticColors.getHexMsg("&#E7CBB3XP Reward: &f" + xp),
                "", 
                "&eClick to Edit Settings", 
                "&cShift-Click to Remove Override");
            
            LocalizedName.set(item, matKey);
            inventory.setItem(slot, item);
            slot++;
        }

        return inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null; // Holder identification only
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlockMenu)) return;
        event.setCancelled(true);
        
        if (event.getRawSlot() >= 54) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        BlockMenu holder = (BlockMenu) event.getInventory().getHolder();
        int currentPage = holder.getPage();
        String regionName = holder.getRegionName();
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        if (event.getRawSlot() == 53) {
            plugin.getVisualManager().setFocusedRegion(player, null);
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1)));
            return;
        }

        if (event.getRawSlot() == 45) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1)));
            return;
        }

        if (event.getRawSlot() == 48 && clicked.getType() == Material.ARROW) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, Math.max(1, currentPage - 1), config, regionName)));
            return;
        }

        if (event.getRawSlot() == 50 && clicked.getType() == Material.ARROW) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, currentPage + 1, config, regionName)));
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            String matKey = LocalizedName.get(clicked); // Format is "region:key" or "global:key"
            if (matKey != null && matKey.contains(":")) {
                String[] parts = matKey.split(":");
                String targetRegion = parts[0];
                String actualKey = parts[1];
                
                String path = "crops." + targetRegion + "." + actualKey;
                if (targetRegion.equalsIgnoreCase("legacy")) path = "crops." + actualKey;
                
                plugin.getConfigManager().getConfig("crops.yml").set(path, null);
                plugin.getConfigManager().saveConfig("crops.yml");
                
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cDeleted &f" + actualKey));
                
                // Refresh the menu
                YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
                player.openInventory(blockmenu(player, currentPage, matConfig, regionName));
            }
        } else if (isMatItem(event.getRawSlot())) {
            // Open EditMenu
            String matName = LocalizedName.get(clicked);
            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
            File matFile = plugin.getConfigManager().getConfigFile("crops.yml");
            
            player.openInventory(new EditMenu(plugin).emenu(player, matConfig, matName, matFile, regionName));
        }
    }

    private boolean isMatItem(int slot) {
        List<Integer> skipSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52, 53, 45, 49);
        return !skipSlots.contains(slot) && slot < 44;
    }
}
