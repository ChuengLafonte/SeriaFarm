package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
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
import id.seria.farm.inventory.utils.LocalizedName;
import java.util.HashMap;
import java.util.Map;

public class RequiredSkillsMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public RequiredSkillsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String path) {

        Map<String, String> placeholders = new HashMap<>();
        Inventory inventory = plugin.getGuiManager().createInventory("required-skills-menu", placeholders);
        
        // Metadata in slot 0
        ItemStack info = inventory.getItem(0);
        if (info != null) LocalizedName.set(info, matName + "|" + regionName + "|" + path);

        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        ConfigurationSection skillsSec = config.getConfigurationSection(path + ".requirements.skills");

        // List Requirements
        int slot = 0;
        if (skillsSec != null) {
            for (String key : skillsSec.getKeys(false)) {
                ConfigurationSection sub = skillsSec.getConfigurationSection(key);
                if (sub == null) continue;

                while (slot < inventory.getSize() && inventory.getItem(slot) != null) slot++;
                if (slot >= inventory.getSize()) break;

                String skill = sub.getString("skill", "Farming");
                String op = sub.getString("operator", ">=");
                int level = sub.getInt("level", 0);

                ItemStack item = InvUtils.createItemStacks(Material.BOOK, 
                    StaticColors.getHexMsg("&#9370dbRequirement #" + key),
                    "&7Skill: &f" + skill,
                    "&7Operator: &f" + op,
                    "&7Level: &f" + level,
                    "",
                    "&eClick to Edit",
                    "&cShift-Click to Delete");
                
                LocalizedName.set(item, key);
                inventory.setItem(slot, item);
                slot++;
            }
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof id.seria.farm.managers.GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("required-skills-menu")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemStack infoItem = event.getInventory().getItem(0);
        if (infoItem == null) return;
        String[] parts = LocalizedName.get(infoItem).split("\\|");
        String mName = parts[0], rName = parts[1], fPath = parts[2];

        String action = LocalizedName.get(clicked);
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        if (action != null) {
            switch (action) {
                case "back":
                    if (rName.equalsIgnoreCase("global")) {
                        player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, mName));
                    } else {
                        player.openInventory(new EditMenu(plugin).emenu(player, config, mName, null, rName));
                    }
                    break;
                case "add_requirement":
                    ConfigurationSection sec = config.getConfigurationSection(fPath + ".requirements.skills");
                    int nextId = 1;
                    if (sec != null) {
                        for (String key : sec.getKeys(false)) {
                            try { nextId = Math.max(nextId, Integer.parseInt(key) + 1); } catch (Exception ignored) {}
                        }
                    }
                    String subPath = fPath + ".requirements.skills." + nextId;
                    config.set(subPath + ".skill", "Farming");
                    config.set(subPath + ".operator", ">=");
                    config.set(subPath + ".level", 0);
                    plugin.getConfigManager().saveConfig("crops.yml");
                    new SkillDetailMenu(plugin).open(player, mName, rName, fPath, String.valueOf(nextId));
                    break;
            }
            return;
        }

        if (clicked.getType() == Material.BOOK) {
            String entryId = LocalizedName.get(clicked);
            if (entryId == null) return;

            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                config.set(fPath + ".requirements.skills." + entryId, null);
                plugin.getConfigManager().saveConfig("crops.yml");
                plugin.getConfigManager().sendPrefixedMessage(player, "&cRequirement deleted.");
                open(player, mName, rName, fPath);
            } else {
                new SkillDetailMenu(plugin).open(player, mName, rName, fPath, entryId);
            }
        }
    }
}
