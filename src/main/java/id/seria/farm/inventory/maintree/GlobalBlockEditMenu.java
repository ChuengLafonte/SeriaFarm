package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import id.seria.farm.listeners.ChatInputListener;
import id.seria.farm.inventory.edittree.ReplaceBlockMenu;
import id.seria.farm.inventory.edittree.DropsMenu;

public class GlobalBlockEditMenu implements Listener, InventoryHolder {
    private final SeriaFarmPlugin plugin;
    private String matName;
    private static final net.kyori.adventure.text.Component name = StaticColors.getHexMsg("&#9370db&lPlant Editor");

    public GlobalBlockEditMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public org.bukkit.inventory.Inventory getInventory() { return null; }
    public String getMatName() { return matName; }

    public Inventory open(Player player, String matName) {
        this.matName = matName;
        Inventory inventory = Bukkit.createInventory(this, 27, name); // 3 Rows as per Wiki
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        String path = "crops.global." + matName.replace("global:", "");
        String displayMaterial = matName.replace("global:", "");
        
        int delay = config.getInt(path + ".regen-delay", 20);

        // Background / Fillers (Gray/Light Gray theme)
        ItemStack spacer = InvUtils.createItemStacks(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", "", "");
        ItemStack divider = InvUtils.createItemStacks(Material.GRAY_STAINED_GLASS_PANE, " ", "", "");
        
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, spacer);
        }
        inventory.setItem(9, divider); // Vertical divider start
        inventory.setItem(6, InvUtils.createItemStacks(Material.WHITE_STAINED_GLASS_PANE, " ", "", ""));
        inventory.setItem(8, InvUtils.createItemStacks(Material.WHITE_STAINED_GLASS_PANE, " ", "", ""));

        // Slot 0: Display Name (Name Tag)
        inventory.setItem(0, InvUtils.createItemStacks(Material.NAME_TAG, 
            StaticColors.getHexMsg("&#9370dbDisplay Name"), 
            "&7Current: &f" + displayMaterial.replace("_", " "), 
            "&eClick to rename"));

        // Slot 2: Storage/Category (Barrel)
        inventory.setItem(2, InvUtils.createItemStacks(Material.BARREL, 
            StaticColors.getHexMsg("&#9370dbCategory Settings"), 
            "&7Manage how this plant is grouped.", 
            "&eStatus: &fGlobal"));

        // Slot 4: Yields/Products (Bundle)
        inventory.setItem(4, InvUtils.createItemStacks(Material.BUNDLE, 
            StaticColors.getHexMsg("&#9370dbPlant Yields"), 
            "&7Customize what this plant drops.", 
            "&eClick to edit drops"));

        // Slot 5: Watering / Harvest Time (Clock)
        inventory.setItem(5, InvUtils.createItemStacks(Material.CLOCK, 
            StaticColors.getHexMsg("&#9370dbHarvest Time"), 
            "&7Duration: &f" + delay + "s", 
            "&7Watering: &fNot Required", 
            "", 
            "&eL-Click to edit time", 
            "&eR-Click to toggle water"));

        // Slot 11: Soil Requirement (Farmland)
        inventory.setItem(11, InvUtils.createItemStacks(Material.FARMLAND, 
            StaticColors.getHexMsg("&#9370dbSoil Requirement"), 
            "&7Required block underneath.", 
            "&eCurrent: &fVanilla/Auto"));

        // Slot 13: Sprout Type (Block Visualization)
        inventory.setItem(13, InvUtils.createItemStacks(Material.OAK_SAPLING, 
            StaticColors.getHexMsg("&#9370dbSprout Type"), 
            "&7The block appearing while growing.", 
            "&eClick to select block"));

        // Navigation
        inventory.setItem(18, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&cBack"), "&7Return to Catalog", ""));
        
        // Metadata for handlers
        ItemStack info = new ItemStack(Material.PAPER);
        LocalizedName.set(info, matName);
        inventory.setItem(26, info);

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GlobalBlockEditMenu)) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        GlobalBlockEditMenu holder = (GlobalBlockEditMenu) event.getInventory().getHolder();
        String matName = holder.getMatName();
        String path = "crops.global." + matName.replace("global:", "");

        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        switch (event.getRawSlot()) {
            case 18: // Back
                player.openInventory(new GlobalBlocksMenu(plugin).blockmenu(player, 1));
                break;
            case 5: // Harvest Time
                ChatInputListener.requestInput(player, "Harvest Time", "Seconds (e.g. 30)", input -> {
                    try {
                        config.set(path + ".regen-delay", Integer.parseInt(input));
                        plugin.getConfigManager().saveConfig("crops.yml");
                        player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aHarvest Time updated!"));
                    } catch (Exception e) {}
                    player.openInventory(open(player, matName));
                }, () -> player.openInventory(open(player, matName)));
                break;
            case 4: // Drops
                new DropsMenu(plugin).open(player, matName, "global", path);
                break;
            case 13: // Sprout Type
                new ReplaceBlockMenu(plugin).open(player, matName, "global", "delay-blocks", path);
                break;
        }
    }
}
