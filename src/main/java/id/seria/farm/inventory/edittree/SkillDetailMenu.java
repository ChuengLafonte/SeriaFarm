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

public class SkillDetailMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public SkillDetailMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String path, String reqId) {

        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        String fullPath = path + ".requirements.skills." + reqId;
        
        String skill = config.getString(fullPath + ".skill", "Farming");
        String op = config.getString(fullPath + ".operator", ">=");
        int level = config.getInt(fullPath + ".level", 0);
        String deny = config.getString(fullPath + ".deny", "Default");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%skill%", skill);
        placeholders.put("%op%", op);
        placeholders.put("%level%", String.valueOf(level));
        placeholders.put("%deny%", deny);

        Inventory inventory = plugin.getGuiManager().createInventory("skill-detail-menu", placeholders);
        
        // Metadata in slot 0
        ItemStack info = inventory.getItem(0);
        if (info != null) LocalizedName.set(info, matName + "|" + regionName + "|" + path + "|" + reqId);

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof id.seria.farm.managers.GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("skill-detail-menu")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemStack infoItem = event.getInventory().getItem(0);
        if (infoItem == null) return;
        String infoData = LocalizedName.get(infoItem);
        if (infoData == null) return;
        String[] parts = infoData.split("\\|");
        String mName = parts[0], rName = parts[1], fPath = parts[2], rId = parts[3];

        String action = LocalizedName.get(clicked);
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        String detailPath = fPath + ".requirements.skills." + rId;

        if (action == null) return;

        switch (action) {
            case "back":
                new RequiredSkillsMenu(plugin).open(player, mName, rName, fPath);
                break;
            case "edit_skill":
                ChatInputListener.requestInput(player, "Enter Skill Name", "e.g. Farming, Mining", input -> {
                    config.set(detailPath + ".skill", input);
                    plugin.getConfigManager().saveConfig("crops.yml");
                    open(player, mName, rName, fPath, rId);
                }, () -> open(player, mName, rName, fPath, rId));
                break;
            case "edit_operator":
                ChatInputListener.requestInput(player, "Enter Operator", ">=, <=, >, <, =", input -> {
                    config.set(detailPath + ".operator", input);
                    plugin.getConfigManager().saveConfig("crops.yml");
                    open(player, mName, rName, fPath, rId);
                }, () -> open(player, mName, rName, fPath, rId));
                break;
            case "edit_level":
                ChatInputListener.requestInput(player, "Enter Required Level", "Integer value", input -> {
                    try {
                        config.set(detailPath + ".level", Integer.parseInt(input));
                        plugin.getConfigManager().saveConfig("crops.yml");
                    } catch (Exception ignored) {}
                    open(player, mName, rName, fPath, rId);
                }, () -> open(player, mName, rName, fPath, rId));
                break;
            case "edit_deny":
                ChatInputListener.requestInput(player, "Enter Deny Message", "Custom warning sign", input -> {
                    config.set(detailPath + ".deny", input);
                    plugin.getConfigManager().saveConfig("crops.yml");
                    open(player, mName, rName, fPath, rId);
                }, () -> open(player, mName, rName, fPath, rId));
                break;
        }
    }
}
