package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.LocalizedName;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import id.seria.farm.listeners.ChatInputListener;

import java.util.HashMap;
import java.util.Map;
import id.seria.farm.managers.GuiManager;

public class EditMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public EditMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory emenu(Player player, YamlConfiguration config, String matName, File file, String regionName) {
        String path = getConfigPath(matName);
        String materialKey = matName.contains(":") ? matName.split(":")[1] : matName;
        
        int delay = config.getInt(path + ".regen-delay", 20);
        int xp = config.getInt(path + ".rewards.xp", 0);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%material%", materialKey);
        placeholders.put("%region%", regionName);
        placeholders.put("%status%", config.getBoolean(path + ".enabled", true) ? "&aEnabled" : "&cDisabled");
        placeholders.put("%delay%", String.valueOf(delay));
        placeholders.put("%xp%", String.valueOf(xp));

        Inventory inventory = plugin.getGuiManager().createInventory("edit-menu", placeholders);

        // Metadata for handlers (The info item in slot 45)
        ItemStack infoItem = inventory.getItem(45);
        if (infoItem != null) LocalizedName.set(infoItem, matName + "|" + regionName);

        // Handle Growth Settings Visibility
        if (!materialKey.equalsIgnoreCase("BAMBOO") && !materialKey.equalsIgnoreCase("SUGAR_CANE") && !materialKey.equalsIgnoreCase("CACTUS")) {
            inventory.setItem(32, null);
        }

        return inventory;
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
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("edit-menu")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemStack infoItem = event.getInventory().getItem(45);
        if (infoItem == null) return;
        
        String data = LocalizedName.get(infoItem);
        if (data == null || !data.contains("|")) return;
        
        String[] parts = data.split("\\|");
        final String finalMatName = parts[0];
        final String finalRegionName = parts[1];

        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        File file = plugin.getConfigManager().getConfigFile("crops.yml");
        String path = getConfigPath(finalMatName);
        String action = LocalizedName.get(clicked);
        if (action == null) return;

        switch (action) {
            case "back_to_blocks":
                player.openInventory(new BlockMenu(plugin).blockmenu(player, 1, config, finalRegionName));
                break;
            case "edit_delay":
                ChatInputListener.requestInput(player, "Customize Delay", "Integer value (e.g. 20)", input -> {
                    try {
                        config.set(path + ".regen-delay", Integer.parseInt(input));
                        plugin.getConfigManager().saveConfig("crops.yml");
                        plugin.getConfigManager().sendPrefixedMessage(player, "&aDelay updated to &f" + input);
                    } catch (NumberFormatException e) {
                        plugin.getConfigManager().sendPrefixedMessage(player, "&cInvalid input. Please enter a number.");
                    }
                    player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
                }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
                break;
            case "edit_final":
                new ReplaceBlockMenu(plugin).open(player, finalMatName, finalRegionName, "replace-blocks", path);
                break;
            case "edit_during":
                new SproutBlockMenu(plugin).open(player, finalMatName, finalRegionName, "delay-blocks", path);
                break;
            case "edit_drops":
                new DropsMenu(plugin).open(player, finalMatName, finalRegionName, path);
                break;
            case "edit_xp":
                ChatInputListener.requestInput(player, "Customize XP Drops", "Integer value", input -> {
                    try {
                        config.set(path + ".rewards.xp", Integer.parseInt(input));
                        plugin.getConfigManager().saveConfig("crops.yml");
                        plugin.getConfigManager().sendPrefixedMessage(player, "&aXP Drop updated to &f" + input);
                    } catch (NumberFormatException e) {
                        plugin.getConfigManager().sendPrefixedMessage(player, "&cInvalid input. Please enter a number.");
                    }
                    player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
                }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
                break;
            case "edit_tools":
                new RequiredToolsMenu(plugin).open(player, finalMatName, finalRegionName, path);
                break;
            case "edit_skills":
                new RequiredSkillsMenu(plugin).open(player, finalMatName, finalRegionName, path);
                break;
            case "edit_commands":
                java.util.List<String> currentCmds = config.getStringList(path + ".rewards.commands");
                ChatInputListener.requestListInput(player, "Commands", currentCmds, "[Console/Player] ; cmd ; chance", list -> {
                    config.set(path + ".rewards.commands", list);
                    plugin.getConfigManager().saveConfig("crops.yml");
                    plugin.getRequirementEngine().canBreak(player, config);
                    plugin.getConfigManager().sendPrefixedMessage(player, "&aCommands updated!");
                    player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
                }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
                break;
            case "delete_block":
                config.set(path, null);
                plugin.getConfigManager().saveConfig("crops.yml");
                plugin.getConfigManager().sendPrefixedMessage(player, "&cBlock configuration deleted.");
                player.openInventory(new BlockMenu(plugin).blockmenu(player, 1, config, finalRegionName));
                break;
            case "edit_growth":
                new VerticalGrowthMenu(plugin).open(player, finalMatName, finalRegionName);
                break;
            case "toggle_block":
                config.set(path + ".enabled", !config.getBoolean(path + ".enabled", true));
                plugin.getConfigManager().saveConfig("crops.yml");
                player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
                break;
        }
    }
}
