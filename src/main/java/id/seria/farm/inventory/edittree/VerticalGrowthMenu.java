package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.listeners.ChatInputListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class VerticalGrowthMenu implements Listener, InventoryHolder {
    private static final Component NAME = StaticColors.getHexMsg("&#228B22&lGrowth Settings");
    private final SeriaFarmPlugin plugin;
    private String matName;
    private String regionName;

    public VerticalGrowthMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName) {
        this.matName = matName;
        this.regionName = regionName;
        
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        String path = getConfigPath(matName);
        
        // Use the unified key growth-max-height
        int maxHeight = config.getInt(path + ".growth-max-height", 
            matName.toLowerCase().contains("bamboo") ? 12 : 3);

        Inventory inventory = Bukkit.createInventory(this, 27, NAME);

        Material iconMat = Material.matchMaterial(matName.split(":")[1]) != null ? Material.matchMaterial(matName.split(":")[1]) : Material.OAK_SAPLING;

        // Slot 13: Info & Edit Max Height
        inventory.setItem(13, InvUtils.createItemStacks(iconMat, 
            StaticColors.getHexMsg("&#228B22&lMax Growth Height"),
            "&7Configure how many blocks tall",
            "&7this plant can grow.",
            "",
            "&eCurrent Limit: &f" + maxHeight + " blocks",
            "",
            "&bClick to Change Value"));

        // Back button
        inventory.setItem(18, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&7« Back"), "&7Return to Edit Menu", ""));

        // Glass fillers
        ItemStack glass = InvUtils.createItemStacks(Material.LIME_STAINED_GLASS_PANE, " ", "", "");
        for (int i = 0; i < 27; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, glass);
        }
        
        player.openInventory(inventory);
    }

    private String getConfigPath(String matName) {
        String[] parts = matName.split(":");
        String section = parts.length > 1 ? parts[0] : "legacy";
        String materialKey = parts.length > 1 ? parts[1] : matName;
        if (section.equalsIgnoreCase("legacy")) {
            return "crops." + materialKey;
        } else {
            return "crops." + section + "." + materialKey;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 27, NAME); // Dummy for Holder identification
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof VerticalGrowthMenu)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        VerticalGrowthMenu holder = (VerticalGrowthMenu) event.getInventory().getHolder();
        String currentMat = this.matName; // Simplified since it's a new instance per open usually
        String currentRegion = this.regionName;

        if (event.getRawSlot() == 18) { // Back
            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
            File matFile = plugin.getConfigManager().getConfigFile("crops.yml");
            player.openInventory(new EditMenu(plugin).emenu(player, matConfig, currentMat, matFile, currentRegion));
            return;
        }

        if (event.getRawSlot() == 13) { // Edit Max Height
            ChatInputListener.requestInput(player, "Max Growth Height", "Positive integer", input -> {
                try {
                    int val = Integer.parseInt(input);
                    if (val < 1) throw new NumberFormatException();
                    
                    YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
                    config.set(getConfigPath(currentMat) + ".growth-max-height", val);
                    plugin.getConfigManager().saveConfig("crops.yml");
                    
                    plugin.getConfigManager().sendPrefixedMessage(player, "&aMax height updated to &f" + val);
                } catch (NumberFormatException e) {
                    plugin.getConfigManager().sendPrefixedMessage(player, "&cInvalid number. Operation cancelled.");
                }
                open(player, currentMat, currentRegion);
            }, () -> open(player, currentMat, currentRegion));
        }
    }
}
