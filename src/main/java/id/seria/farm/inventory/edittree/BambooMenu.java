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

public class BambooMenu implements Listener, InventoryHolder {
    private static final Component NAME = StaticColors.getHexMsg("&#228B22&lBamboo Settings");
    private final SeriaFarmPlugin plugin;
    private String matName;
    private String regionName;

    public BambooMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName) {
        this.matName = matName;
        this.regionName = regionName;
        
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        String path = getConfigPath(matName);
        int maxHeight = config.getInt(path + ".bamboo-max-height", 12);

        Inventory inventory = Bukkit.createInventory(this, 27, NAME);

        // Slot 13: Info & Edit Max Height
        inventory.setItem(13, InvUtils.createItemStacks(Material.BAMBOO, 
            StaticColors.getHexMsg("&#228B22&lMax Growth Height"),
            "&7Configure how many blocks tall",
            "&7this bamboo can grow.",
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

        // Store metadata on a hidden item or use holder
        // Since we don't have a dedicated slot for data, we'll use the holder if needed, 
        // but EditMenu uses slot 45 for data. Here we'll use slot 0 or just pass data via holder.
        
        player.openInventory(inventory);
    }

    private String getConfigPath(String matName) {
        String[] parts = matName.split(":");
        String section = parts.length > 1 ? parts[0] : "legacy";
        String materialKey = parts.length > 1 ? parts[1] : matName;
        if (section.equalsIgnoreCase("legacy")) {
            return "blocks." + materialKey;
        } else {
            return "blocks." + section + "." + materialKey;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null; // Holder identification
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BambooMenu)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        BambooMenu holder = (BambooMenu) event.getInventory().getHolder();
        String currentMat = holder.matName;
        String currentRegion = holder.regionName;

        if (event.getRawSlot() == 18) { // Back
            YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
            File matFile = plugin.getConfigManager().getConfigFile("materials.yml");
            player.openInventory(new EditMenu(plugin).emenu(player, matConfig, currentMat, matFile, currentRegion));
            return;
        }

        if (event.getRawSlot() == 13) { // Edit Max Height
            ChatInputListener.requestInput(player, "Bamboo Max Height", "Positive integer (default: 12)", input -> {
                try {
                    int val = Integer.parseInt(input);
                    if (val < 1) throw new NumberFormatException();
                    
                    YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
                    config.set(getConfigPath(currentMat) + ".bamboo-max-height", val);
                    plugin.getConfigManager().saveConfig("materials.yml");
                    
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aMax height updated to &f" + val));
                } catch (NumberFormatException e) {
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cInvalid number. Operation cancelled."));
                }
                open(player, currentMat, currentRegion);
            }, () -> open(player, currentMat, currentRegion));
        }
    }
}
