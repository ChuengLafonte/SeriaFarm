package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.inventory.MainMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GlobalBlocksMenu implements Listener, InventoryHolder {
    private final SeriaFarmPlugin plugin;
    private final Component name = StaticColors.getHexMsg("&#2ecc71&lFarm Species Wiki");
    private int page;

    public GlobalBlocksMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public int getPage() { return page; }

    public Inventory blockmenu(Player player, int page) {
        this.page = page;
        Inventory inventory = Bukkit.createInventory(this, 54, this.name);
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        
        // Navigation & Info (Modern Wiki Style)
        inventory.setItem(48, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&#2ecc71&l« Previous"), "&7Go back to the last page.", ""));
        
        ItemStack info = InvUtils.createItemStacks(Material.KNOWLEDGE_BOOK, StaticColors.getHexMsg("&#2ecc71&lFarm Wiki Info"), 
            "&7Viewing: &fGlobal Species", 
            "&7Registered Plants: &f" + getGlobalCount(config),
            "&7Current Page: &f#" + page,
            "",
            "&7Discover all the plants and their",
            "&7growth properties in this catalog.");
        inventory.setItem(49, info);
        
        inventory.setItem(50, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&#2ecc71&lNext »"), "&7Advance to the next page.", ""));
        
        if (player.hasPermission("seriafarm.admin")) {
            inventory.setItem(45, InvUtils.createItemStacks(Material.WRITABLE_BOOK, StaticColors.getHexMsg("&#93cfec&lAdd New Species"), 
                "&7Click to register a new", 
                "&7global farming material.", 
                "", 
                "&eAdmin Feature"));
        } else {
            inventory.setItem(45, InvUtils.createItemStacks(Material.SUNFLOWER, StaticColors.getHexMsg("&#fbca00&lFarm Statistics"), 
                "&7Total Varieties: &f" + getGlobalCount(config),
                "&7Global Market Root: &fOnline"));
        }
        
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose Menu"), "&7Exit the wiki", ""));

        ConfigurationSection globalSection = config.getConfigurationSection("crops.global");
        if (globalSection == null) {
            plugin.getLogger().warning("GlobalBlocksMenu: crops.global section not found in crops.yml!");
        }
        List<String> materials = new ArrayList<>();
        if (globalSection != null) {
            globalSection.getKeys(false).forEach(k -> materials.add("global:" + k));
        }
        
        materials.sort(Comparator.naturalOrder());

        // Fillers (Sleek Green/Emerald theme for Farm Wiki)
        ItemStack glass = InvUtils.createItemStacks(Material.LIME_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 51, 52}) {
            inventory.setItem(n, glass);
        }

        // Pagination Logic
        int slot = 10;
        List<Integer> skipSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
        
        for (String matKey : PageUtil.getpageitems(materials, page, 21)) {
            while (skipSlots.contains(slot)) slot++;
            if (slot >= 44) break;

            String materialKey = matKey.split(":")[1];
            String path = "crops.global." + materialKey;
            
            int xp = config.getInt(path + ".rewards.xp", 0);
            int delay = config.getInt(path + ".regen-delay", 20);
            ConfigurationSection rewards = config.getConfigurationSection(path + ".rewards");
            int dropsCount = (rewards != null) ? rewards.getKeys(false).size() : 0;
            if (rewards != null && rewards.contains("xp")) dropsCount--;

            // Rarity Logic
            String rarity = "&#2ecc71Common";
            if (delay >= 120) rarity = "&#f1c40fLegendary";
            else if (delay >= 60) rarity = "&#9b59b6Epic";
            else if (delay >= 30) rarity = "&#3498dbRare";

            // Popularity Logic (Dummy based on material name hash for variety)
            int popularityScore = Math.abs(materialKey.hashCode() % 100);
            String popularity = popularityScore > 80 ? "&aHighly Popular" : (popularityScore > 40 ? "&eTrending" : "&7Stable");

            ItemStack icon = plugin.getHookManager().getItem(materialKey);
            if (icon.getType() == Material.STONE && !materialKey.equalsIgnoreCase("STONE")) {
                // Warning lore if material is invalid
                icon = new ItemStack(Material.BARRIER);
            }
            List<Object> lore = new ArrayList<>(Arrays.asList(
                "&8World Species • Wiki",
                "",
                StaticColors.getHexMsg("&#E7CBB3Rarity: " + rarity),
                StaticColors.getHexMsg("&#E7CBB3Popularity: " + popularity),
                "",
                StaticColors.getHexMsg("&#E7CBB3Growth Duration: &f" + delay + "s"),
                StaticColors.getHexMsg("&#E7CBB3XP Reward: &f" + xp),
                StaticColors.getHexMsg("&#E7CBB3Unique Drops: &f" + Math.max(0, dropsCount)),
                ""
            ));

            if (player.hasPermission("seriafarm.admin")) {
                lore.add("&eClick to Edit Settings");
                lore.add("&cShift-Click to Remove");
            } else {
                lore.add("&7Discover this plant in the fields!");
            }

            ItemStack item = InvUtils.applyMeta(icon, 
                StaticColors.getHexMsg("&#2ecc71&l" + materialKey.replace("_", " ")), 
                lore.toArray());
            
            LocalizedName.set(item, matKey);
            inventory.setItem(slot, item);
            slot++;
        }

        return inventory;
    }

    private int getGlobalCount(YamlConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("crops.global");
        return sec == null ? 0 : sec.getKeys(false).size();
    }


    @Override
    public @NotNull Inventory getInventory() {
        return null; // Holder identification only
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        String title = SeriaFarmPlugin.MINI_MESSAGE.serialize(event.getView().title());
        boolean isHolder = event.getInventory().getHolder() instanceof GlobalBlocksMenu;
        boolean isTitle = title.contains("Farm Species Wiki");

        if (!isHolder && !isTitle) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        
        // Debug Logging
        

        if (event.getRawSlot() >= 54) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.LIME_STAINED_GLASS_PANE) return;

        if (event.getRawSlot() == 53) {
            // Close the menu or go back to main menu
            player.closeInventory();
            return;
        }

        if (event.getRawSlot() == 45 && player.hasPermission("seriafarm.admin")) {
            // Open standard AddBlocksMenu for global
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.addtree.AddBlocksMenu().addblocks_menu(player, "global")));
            return;
        }

        // Pagination handling
        GlobalBlocksMenu holder = (GlobalBlocksMenu) event.getInventory().getHolder();
        int currentPage = holder.getPage();
        if (event.getRawSlot() == 48) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, Math.max(1, currentPage - 1))));
            return;
        }
        if (event.getRawSlot() == 50) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, currentPage + 1)));
            return;
        }

        String matName = LocalizedName.get(clicked);
        if (matName != null && matName.startsWith("global:")) {
            if (!player.hasPermission("seriafarm.admin")) return;

            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
            
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                // Delete Logic
                String subKey = matName.split(":")[1];
                matConfig.set("crops.global." + subKey, null);
                plugin.getConfigManager().saveConfig("crops.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cDeleted Global &f" + subKey));
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, 1)));
            } else {
                // Edit Logic (Wiki Adaptation)
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, matName)));
            }
        }
    }
}
