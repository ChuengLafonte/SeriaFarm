package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.inventory.MainMenu;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GlobalBlocksMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private final String name = StaticColors.getHexMsg("&#fbca00&lPlant Catalog");

    public GlobalBlocksMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory blockmenu(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(player, 54, this.name);
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        
        // Navigation & Admin Icons (LiteFarm Style)
        inventory.setItem(48, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&aPrevious Page"), "&7Go back", ""));
        inventory.setItem(49, InvUtils.createItemStacks(Material.BOOK, StaticColors.getHexMsg("&#fbca00&lCatalog Information"), "&7Viewing: &fGlobal Materials", "&7Total Plants: &f" + getGlobalCount(config)));
        inventory.setItem(50, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&aNext Page"), "&7Go forward", ""));
        
        inventory.setItem(45, InvUtils.createItemStacks(Material.WRITABLE_BOOK, StaticColors.getHexMsg("&#93cfec&lAdd New Plant"), "&7Click to register a new", "&7global farming material.", "", "&eRight-click to browse templates"));
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cBack"), "&7Return to Region Menu", ""));

        ConfigurationSection globalSection = config.getConfigurationSection("blocks.global");
        List<String> materials = new ArrayList<>();
        if (globalSection != null) {
            globalSection.getKeys(false).forEach(k -> materials.add("global:" + k));
        }
        
        materials.sort(Comparator.naturalOrder());

        // Fillers (Orange/Gold for Catalog)
        ItemStack glass = InvUtils.createItemStacks(Material.ORANGE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 51, 52}) {
            inventory.setItem(n, glass);
        }

        // Pagination Logic
        int slot = 10;
        List<Integer> skipSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
        
        for (String matKey : PageUtil.getpageitems(materials, page, 21)) { // Adjusted for a cleaner grid
            while (skipSlots.contains(slot)) slot++;
            if (slot >= 44) break;

            String materialKey = matKey.split(":")[1];
            int xp = config.getInt("blocks.global." + materialKey + ".rewards.xp", 0);
            
            ItemStack icon = plugin.getHookManager().getItem(materialKey);
            ItemStack item = InvUtils.applyMeta(icon, 
                StaticColors.getHexMsg("&#fbca00" + materialKey.replace("_", " ")), 
                StaticColors.getHexMsg("&#E7CBB3Level: &f1"), // Placeholder for growth level
                StaticColors.getHexMsg("&#E7CBB3Harvested: &f0"), // Placeholder for stats
                StaticColors.getHexMsg("&#E7CBB3Chance: &f100%"), // Placeholder for chance
                StaticColors.getHexMsg("&#E7CBB3EXP: &f" + xp),
                "", 
                "&7Click to &eEdit Settings", 
                "&7Shift-Click to &cRemove");
            
            LocalizedName.set(item, matKey);
            inventory.setItem(slot, item);
            slot++;
        }

        return inventory;
    }

    private int getGlobalCount(YamlConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("blocks.global");
        return sec == null ? 0 : sec.getKeys(false).size();
    }


    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(this.name)) return;
        event.setCancelled(true);
        
        if (event.getRawSlot() >= 54) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.ORANGE_STAINED_GLASS_PANE) return;

        Player player = (Player) event.getWhoClicked();

        if (event.getRawSlot() == 53) {
            player.openInventory(new MainMenu(plugin).mainmenu(player));
            return;
        }

        String matName = LocalizedName.get(clicked);
        if (matName != null && matName.startsWith("global:")) {
            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
            
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                // Delete Logic
                String subKey = matName.split(":")[1];
                matConfig.set("blocks.global." + subKey, null);
                plugin.getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cDeleted Global &f" + subKey));
                player.openInventory(blockmenu(player, 1));
            } else {
                // Edit Logic (Wiki Adaptation)
                player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, matName));
            }
        }
    }
}
