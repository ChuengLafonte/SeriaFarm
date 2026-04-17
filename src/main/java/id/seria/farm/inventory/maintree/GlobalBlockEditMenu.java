package id.seria.farm.inventory.maintree;

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
import id.seria.farm.managers.GuiManager;
import java.util.*;
import id.seria.farm.listeners.ChatInputListener;
import id.seria.farm.inventory.edittree.ReplaceBlockMenu;
import id.seria.farm.inventory.edittree.DropsMenu;

public class GlobalBlockEditMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private String matName;

    public GlobalBlockEditMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public String getMatName() { return matName; }

    public Inventory open(Player player, String matName) {
        this.matName = matName;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        // matName is "global:key" or "garden:key" — resolve section and key
        String[] parts = matName.split(":", 2);
        String section    = parts[0]; // "global" or "garden"
        String materialKey = parts[1];
        String path = "crops." + section + "." + materialKey;

        String displayName = config.getString(path + ".display-name", materialKey.replace("_", " "));
        int delay = config.getInt(path + ".regen-delay", 20);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%display_name%", displayName);
        placeholders.put("%delay%", String.valueOf(delay));
        
        double rottenMulti = config.getDouble(path + ".rotten-rewards.xp-multiplier", 0.2);
        double seedReturn = config.getDouble(path + ".seed-return-chance", 80.0);
        placeholders.put("%rotten_xp_multi%", String.valueOf((int)(rottenMulti * 100)));
        placeholders.put("%seed_return%", String.valueOf((int)seedReturn));

        Inventory inventory = plugin.getGuiManager().createInventory("catalog-edit-menu", placeholders);

        // Metadata for handlers (The invisible info item)
        ItemStack info = new ItemStack(Material.PAPER);
        LocalizedName.set(info, matName);
        inventory.setItem(26, info);

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("catalog-edit-menu")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String action = LocalizedName.get(clicked);
        if (action == null) return; // border pane or untagged item
        
        // Find the matName from the hidden paper in slot 26
        ItemStack infoItem = event.getInventory().getItem(26);
        if (infoItem == null) return;
        String matName = LocalizedName.get(infoItem);
        if (matName == null) return; // info item lost its PDC tag
        // Resolve config path from prefix ("global:key" or "garden:key")
        String[] mparts  = matName.split(":", 2);
        String section    = mparts[0];
        String materialKey = mparts[1];
        String path = "crops." + section + "." + materialKey;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        switch (action) {
            case "edit_name":
                ChatInputListener.requestInput(player, "Display Name", "&fColor coded name (e.g. &#ffaa00Gold...)", input -> {
                    config.set(path + ".display-name", input);
                    plugin.getConfigManager().saveConfig("crops.yml");
                    plugin.getConfigManager().sendPrefixedMessage(player, "&aDisplay Name updated!");
                    player.openInventory(open(player, matName));
                }, () -> player.openInventory(open(player, matName)));
                break;
            case "back_to_catalog":
                player.openInventory(new GlobalBlocksMenu(plugin).blockmenu(player, 1, section));
                break;
            case "edit_time":
                ChatInputListener.requestInput(player, "Harvest Time", "Seconds (e.g. 30)", input -> {
                    try {
                        config.set(path + ".regen-delay", Integer.parseInt(input));
                        plugin.getConfigManager().saveConfig("crops.yml");
                        plugin.getConfigManager().sendPrefixedMessage(player, "&aHarvest Time updated!");
                    } catch (Exception e) {}
                    player.openInventory(open(player, matName));
                }, () -> player.openInventory(open(player, matName)));
                break;
            case "edit_drops":
                new DropsMenu(plugin).open(player, matName, "global", path + ".rewards");
                break;
            case "edit_rotten_drops":
                new DropsMenu(plugin).open(player, matName, "global", path + ".rotten-rewards");
                break;
            case "edit_rotten_xp":
                ChatInputListener.requestInput(player, "Rotten XP %", "0-100 (e.g. 20)", input -> {
                    try {
                        double val = Double.parseDouble(input) / 100.0;
                        config.set(path + ".rotten-rewards.xp-multiplier", val);
                        plugin.getConfigManager().saveConfig("crops.yml");
                        plugin.getConfigManager().sendPrefixedMessage(player, "&aRotten XP set to &f" + (int)(val*100) + "%");
                    } catch (Exception e) {}
                    player.openInventory(open(player, matName));
                }, () -> player.openInventory(open(player, matName)));
                break;
            case "edit_seed_return":
                ChatInputListener.requestInput(player, "Seed Return %", "0-100 (e.g. 80)", input -> {
                    try {
                        double val = Double.parseDouble(input);
                        config.set(path + ".seed-return-chance", val);
                        plugin.getConfigManager().saveConfig("crops.yml");
                        plugin.getConfigManager().sendPrefixedMessage(player, "&aSeed return chance set to &f" + (int)val + "%");
                    } catch (Exception e) {}
                    player.openInventory(open(player, matName));
                }, () -> player.openInventory(open(player, matName)));
                break;
            case "edit_sprout":
                new id.seria.farm.inventory.edittree.SproutBlockMenu(plugin).open(player, matName, "global", "delay-blocks", path);
                break;
            case "edit_skills":
                new id.seria.farm.inventory.edittree.RequiredSkillsMenu(plugin).open(player, matName, "global", path);
                break;
            case "edit_watering":
                player.openInventory(new id.seria.farm.inventory.watering.WateringSettingsMenu(plugin).open(player, matName));
                break;
            case "edit_soil":
                new id.seria.farm.inventory.watering.SoilRequirementMenu(plugin).open(player, matName);
                break;
        }
    }
}
