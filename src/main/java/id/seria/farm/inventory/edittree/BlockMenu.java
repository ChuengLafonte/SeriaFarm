package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.edittree.RegionEdit.RegionSelectionMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.util.Comparator;
import java.util.List;

import java.util.HashMap;
import java.util.Map;
import id.seria.farm.managers.GuiManager;

public class BlockMenu implements Listener {
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
            if (!regionName.equalsIgnoreCase("global")) {
                ConfigurationSection region = blocksSection.getConfigurationSection(regionName);
                if (region != null) {
                    region.getKeys(false).forEach(k -> materials.add(regionName + ":" + k));
                }
            }
        }
        materials.sort(Comparator.naturalOrder());

        FileConfiguration regConfig = plugin.getConfigManager().getConfig("regions.yml");
        String regPath = "regions." + regionName + ".";
        boolean isEnabled = regConfig.getBoolean(regPath + "enabled", true);
        boolean isPerRegion = regConfig.getBoolean(regPath + "per-region-regen", true);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%region%", regionName);
        placeholders.put("%count%", String.valueOf(materials.size()));
        placeholders.put("%status%", isEnabled ? "&aActive" : "&cPaused");
        placeholders.put("%mode%", isPerRegion ? "&bRegional" : "&6Global-Only");

        Inventory inventory = plugin.getGuiManager().createInventory("block-menu", placeholders);
        
        // Metadata for handlers (slot 49 contains regionName)
        ItemStack summary = inventory.getItem(49);
        if (summary != null) LocalizedName.set(summary, regionName);

        // Update Nav buttons aesthetics based on page validity
        if (PageUtil.isPageValid(materials, page - 1, 28)) {
            ItemStack prev = inventory.getItem(36);
            if (prev != null) inventory.setItem(36, InvUtils.applyMeta(prev.withType(Material.GREEN_STAINED_GLASS_PANE), null));
        }
        if (PageUtil.isPageValid(materials, page + 1, 28)) {
            ItemStack next = inventory.getItem(44);
            if (next != null) inventory.setItem(44, InvUtils.applyMeta(next.withType(Material.GREEN_STAINED_GLASS_PANE), null));
        }

        int slot = 0;
        List<String> pageItems = PageUtil.getpageitems(materials, page, 28);
        
        for (String matKey : pageItems) {
            // Find next empty slot
            while (slot < inventory.getSize() && inventory.getItem(slot) != null && inventory.getItem(slot).getType() != Material.AIR) {
                slot++;
            }
            if (slot >= inventory.getSize()) break;

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

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("block-menu")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Retrieve regionName from metadata in slot 49
        ItemStack summaryItem = event.getInventory().getItem(49);
        String rawRegion = (summaryItem != null) ? LocalizedName.get(summaryItem) : "global";
        final String currentRegion = (rawRegion != null) ? rawRegion : "global";

        String action = LocalizedName.get(clicked);
        if (action == null) return;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        switch (action) {
            case "close_menu":
                plugin.getVisualManager().setFocusedRegion(player, null);
                player.closeInventory();
                break;
            case "back_to_selection":
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1)));
                break;
            case "prev_page":
                if (clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, page - 1, config, currentRegion)));
                }
                break;
            case "next_page":
                if (clicked.getType() == Material.GREEN_STAINED_GLASS_PANE) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, page + 1, config, currentRegion)));
                }
                break;
            default:
                // Logic for blocks (action will be region:BLOCK)
                if (action.contains(":")) {
                    if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                        String[] parts = action.split(":");
                        String targetRegion = parts[0];
                        String actualKey = parts[1];
                        
                        String path = "crops." + targetRegion + "." + actualKey;
                        plugin.getConfigManager().getConfig("crops.yml").set(path, null);
                        plugin.getConfigManager().saveConfig("crops.yml");
                        plugin.getConfigManager().sendPrefixedMessage(player, "&cDeleted &f" + actualKey);
                        
                        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, page, config, currentRegion)));
                    } else {
                        File matFile = plugin.getConfigManager().getConfigFile("crops.yml");
                        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new EditMenu(plugin).emenu(player, config, action, matFile, currentRegion)));
                    }
                }
                break;
        }
    }

}
