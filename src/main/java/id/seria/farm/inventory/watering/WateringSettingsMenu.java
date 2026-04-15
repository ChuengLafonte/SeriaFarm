package id.seria.farm.inventory.watering;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.maintree.GlobalBlockEditMenu;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.listeners.ChatInputListener;
import id.seria.farm.managers.GuiManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin GUI for configuring the watering system of a specific crop.
 * Opened from GlobalBlockEditMenu via the "Watering Settings" button.
 */
public class WateringSettingsMenu implements Listener {

    private final SeriaFarmPlugin plugin;

    public WateringSettingsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory open(Player player, String matName) {
        String section = matName.contains(":") ? matName.split(":")[0] : "global";
        String cropKey = matName.contains(":") ? matName.split(":")[1] : matName;
        String path = "crops." + section + "." + cropKey;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        boolean wateringEnabled = config.getBoolean(path + ".watering.enabled", false);
        int maxLevel = config.getInt(path + ".watering.max-level", 10);
        int decayRate = config.getInt(path + ".watering.decay-rate", 1);
        int decayInterval = config.getInt(path + ".watering.decay-interval", 120);
        int rotThreshold = config.getInt(path + ".watering.rot-threshold", 600);
        double holoHeight = config.getDouble(path + ".watering.hologram-height", 1.6);

        Map<String, String> ph = new HashMap<>();
        ph.put("%watering_enabled%", wateringEnabled ? "&aEnabled" : "&cDisabled");
        ph.put("%toggle_mat%", wateringEnabled ? "LIME_WOOL" : "RED_WOOL");
        ph.put("%max_level%", String.valueOf(maxLevel));
        ph.put("%decay_rate%", String.valueOf(decayRate));
        ph.put("%decay_interval%", String.valueOf(decayInterval));
        ph.put("%rot_threshold%", String.valueOf(rotThreshold));
        ph.put("%hologram_height%", String.valueOf(holoHeight));

        Inventory inv = plugin.getGuiManager().createInventory("watering-settings-menu", ph);

        // Hidden info item carrying matName
        ItemStack info = new ItemStack(Material.PAPER);
        LocalizedName.set(info, matName);
        inv.setItem(0, info);

        return inv;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("watering-settings-menu")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String action = LocalizedName.get(clicked);
        if (action == null) return;

        // Retrieve matName from hidden info item (slot 0)
        ItemStack infoItem = event.getInventory().getItem(0);
        if (infoItem == null) return;
        String matName = LocalizedName.get(infoItem);
        if (matName == null) return;
        String section = matName.contains(":") ? matName.split(":")[0] : "global";
        String cropKey = matName.contains(":") ? matName.split(":")[1] : matName;
        String path = "crops." + section + "." + cropKey;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        switch (action) {
            case "toggle_watering" -> {
                boolean current = config.getBoolean(path + ".watering.enabled", false);
                config.set(path + ".watering.enabled", !current);
                plugin.getConfigManager().saveConfig("crops.yml");
                plugin.getConfigManager().sendPrefixedMessage(player, "&eWatering " + (!current ? "&aEnabled" : "&cDisabled"));
                player.openInventory(open(player, matName));
            }
            case "edit_max_level" -> {
                player.closeInventory();
                ChatInputListener.requestInput(player, "Max Water Level", "e.g. 10",
                        input -> {
                            try { config.set(path + ".watering.max-level", Integer.parseInt(input)); plugin.getConfigManager().saveConfig("crops.yml"); }
                            catch (Exception ignored) {}
                            player.openInventory(open(player, matName));
                        }, () -> player.openInventory(open(player, matName)));
            }
            case "edit_decay_rate" -> {
                player.closeInventory();
                ChatInputListener.requestInput(player, "Decay Rate", "Water lost per interval (e.g. 1)",
                        input -> {
                            try { config.set(path + ".watering.decay-rate", Integer.parseInt(input)); plugin.getConfigManager().saveConfig("crops.yml"); }
                            catch (Exception ignored) {}
                            player.openInventory(open(player, matName));
                        }, () -> player.openInventory(open(player, matName)));
            }
            case "edit_decay_interval" -> {
                player.closeInventory();
                ChatInputListener.requestInput(player, "Decay Interval (seconds)", "e.g. 120",
                        input -> {
                            try { config.set(path + ".watering.decay-interval", Integer.parseInt(input)); plugin.getConfigManager().saveConfig("crops.yml"); }
                            catch (Exception ignored) {}
                            player.openInventory(open(player, matName));
                        }, () -> player.openInventory(open(player, matName)));
            }
            case "edit_rot_threshold" -> {
                player.closeInventory();
                ChatInputListener.requestInput(player, "Rot Threshold (seconds)", "e.g. 600",
                        input -> {
                            try { config.set(path + ".watering.rot-threshold", Integer.parseInt(input)); plugin.getConfigManager().saveConfig("crops.yml"); }
                            catch (Exception ignored) {}
                            player.openInventory(open(player, matName));
                        }, () -> player.openInventory(open(player, matName)));
            }
            case "edit_hologram_height" -> {
                player.closeInventory();
                ChatInputListener.requestInput(player, "Hologram Height Offset", "e.g. 0.5 (Decimal point allowed)",
                        input -> {
                            try { config.set(path + ".watering.hologram-height", Double.parseDouble(input)); plugin.getConfigManager().saveConfig("crops.yml"); }
                            catch (Exception ignored) {}
                            player.openInventory(open(player, matName));
                        }, () -> player.openInventory(open(player, matName)));
            }
            case "back" -> player.openInventory(new GlobalBlockEditMenu(plugin).open(player, matName));
        }
    }
}
