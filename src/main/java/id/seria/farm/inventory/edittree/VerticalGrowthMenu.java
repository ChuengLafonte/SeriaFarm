package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.listeners.ChatInputListener;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import id.seria.farm.inventory.utils.LocalizedName;

import java.util.HashMap;
import java.util.Map;

public class VerticalGrowthMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public VerticalGrowthMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName) {
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        String path = getConfigPath(matName);
        
        int maxHeight = config.getInt(path + ".growth-max-height", 
            matName.toLowerCase().contains("bamboo") ? 12 : 3);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%height%", String.valueOf(maxHeight));

        Inventory inventory = plugin.getGuiManager().createInventory("vertical-growth-menu", placeholders);
        
        // Metadata in slot 0
        ItemStack info = inventory.getItem(0);
        if (info != null) LocalizedName.set(info, matName + "|" + regionName);

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof id.seria.farm.managers.GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("vertical-growth-menu")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemStack infoItem = event.getInventory().getItem(0);
        if (infoItem == null) return;
        String[] parts = LocalizedName.get(infoItem).split("\\|");
        String mName = parts[0], rName = parts[1];

        String action = LocalizedName.get(clicked);
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        if (action == null) return;

        switch (action) {
            case "back":
                player.openInventory(new EditMenu(plugin).emenu(player, config, mName, null, rName));
                break;
            case "edit_height":
                ChatInputListener.requestInput(player, "Max Growth Height", "Positive integer", input -> {
                    try {
                        int val = Integer.parseInt(input);
                        if (val < 1) throw new NumberFormatException();
                        config.set(getConfigPath(mName) + ".growth-max-height", val);
                        plugin.getConfigManager().saveConfig("crops.yml");
                        plugin.getConfigManager().sendPrefixedMessage(player, "&aMax height updated to &f" + val);
                    } catch (NumberFormatException e) {
                        plugin.getConfigManager().sendPrefixedMessage(player, "&cInvalid number.");
                    }
                    open(player, mName, rName);
                }, () -> open(player, mName, rName));
                break;
        }
    }
}
