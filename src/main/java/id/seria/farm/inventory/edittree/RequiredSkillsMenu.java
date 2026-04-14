package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.stream.IntStream;

public class RequiredSkillsMenu implements Listener, InventoryHolder {

    private final SeriaFarmPlugin plugin;
    private String matName;
    private String regionName;
    private String path;
    private static final net.kyori.adventure.text.Component name = StaticColors.getHexMsg("&#9370db&lSkill Requirements");

    public RequiredSkillsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String path) {
        this.matName = matName;
        this.regionName = regionName;
        this.path = path;
        player.openInventory(getInventory(player));
    }

    public Inventory getInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(this, 36, name);
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        ConfigurationSection skillsSec = config.getConfigurationSection(path + ".requirements.skills");

        // Fill Borders
        ItemStack border = InvUtils.createItemStacks(Material.BLACK_STAINED_GLASS_PANE, " ");
        IntStream.range(0, 36).forEach(i -> {
            if (i < 10 || i > 25 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, border);
            }
        });

        // Back Button
        inventory.setItem(31, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&cBack"), "&7Return to List"));

        // List Requirements
        int slot = 10;
        if (skillsSec != null) {
            for (String key : skillsSec.getKeys(false)) {
                if (slot > 16 && slot < 19) slot = 19; // Skip to next row inner if needed, though with 9x4 we have room
                if (slot > 25) break;

                ConfigurationSection sub = skillsSec.getConfigurationSection(key);
                if (sub == null) continue;

                String skill = sub.getString("skill", "Farming");
                String op = sub.getString("operator", ">=");
                int level = sub.getInt("level", 0);
                String deny = sub.getString("deny", "");

                ItemStack item = InvUtils.createItemStacks(Material.BOOK, 
                    StaticColors.getHexMsg("&#9370dbRequirement #" + key),
                    "&7Skill: &f" + skill,
                    "&7Operator: &f" + op,
                    "&7Level: &f" + level,
                    "&7Message: &f" + (deny.isEmpty() ? "&8(Default)" : (deny.length() > 20 ? deny.substring(0, 17) + "..." : deny)),
                    "",
                    "&eClick to Edit",
                    "&cShift-Click to Delete");
                
                // Store Key in NBT/LocalizedName
                id.seria.farm.inventory.utils.LocalizedName.set(item, key);
                inventory.setItem(slot, item);
                slot++;
                
                // Safety skip for borders in middle rows
                if (slot % 9 == 8) slot += 2; 
            }
        }

        // Add Button (Plus)
        if (slot <= 25) {
            inventory.setItem(slot, InvUtils.createItemStacks(Material.LIME_STAINED_GLASS_PANE, 
                StaticColors.getHexMsg("&a&lAdd Requirement"), 
                "&7Add a new skill gated condition."));
        }

        return inventory;
    }

    @Override
    public @NotNull Inventory getInventory() { return null; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RequiredSkillsMenu)) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        RequiredSkillsMenu holder = (RequiredSkillsMenu) event.getInventory().getHolder();
        ItemStack clicked = event.getCurrentItem();
        
        if (clicked == null || clicked.getType() == Material.AIR) return;

        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        if (event.getRawSlot() == 31) { // Back
            if (holder.regionName.equalsIgnoreCase("global")) {
                player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, holder.matName));
            } else {
                player.openInventory(new EditMenu(plugin).emenu(player, config, holder.matName, null, holder.regionName));
            }
            return;
        }

        if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE) {
            // Find next available ID
            ConfigurationSection sec = config.getConfigurationSection(holder.path + ".requirements.skills");
            int nextId = 1;
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try { nextId = Math.max(nextId, Integer.parseInt(key) + 1); } catch (Exception ignored) {}
                }
            }
            // Create default
            String subPath = holder.path + ".requirements.skills." + nextId;
            config.set(subPath + ".skill", "Farming");
            config.set(subPath + ".operator", ">=");
            config.set(subPath + ".level", 0);
            plugin.getConfigManager().saveConfig("crops.yml");
            
            new SkillDetailMenu(plugin).open(player, holder.matName, holder.regionName, holder.path, String.valueOf(nextId));
            return;
        }

        if (clicked.getType() == Material.BOOK) {
            String entryId = id.seria.farm.inventory.utils.LocalizedName.get(clicked);
            if (entryId == null) return;

            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                config.set(path + ".requirements.skills." + entryId, null);
                plugin.getConfigManager().saveConfig("crops.yml");
                plugin.getConfigManager().sendPrefixedMessage(player, "&cRequirement deleted.");
                open(player, holder.matName, holder.regionName, holder.path);
            } else {
                new SkillDetailMenu(plugin).open(player, holder.matName, holder.regionName, holder.path, entryId);
            }
        }
    }
}
