package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.listeners.ChatInputListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.stream.IntStream;

public class SkillDetailMenu implements Listener, InventoryHolder {

    private final SeriaFarmPlugin plugin;
    private String matName;
    private String regionName;
    private String path;
    private String reqId;
    private static final net.kyori.adventure.text.Component name = StaticColors.getHexMsg("&#9370db&lRequirement Details");

    public SkillDetailMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String path, String reqId) {
        this.matName = matName;
        this.regionName = regionName;
        this.path = path;
        this.reqId = reqId;
        player.openInventory(getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        Inventory inventory = Bukkit.createInventory(this, 36, name);
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        String fullPath = path + ".requirements.skills." + reqId;
        
        String skill = config.getString(fullPath + ".skill", "Farming");
        String op = config.getString(fullPath + ".operator", ">=");
        int level = config.getInt(fullPath + ".level", 0);

        // Fill Borders
        ItemStack border = InvUtils.createItemStacks(Material.BLACK_STAINED_GLASS_PANE, " ");
        IntStream.range(0, 36).forEach(i -> {
            if (i < 9 || i > 26 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, border);
            }
        });

        // Slot 20: Skill
        inventory.setItem(20, InvUtils.createItemStacks(Material.BOOK, 
            StaticColors.getHexMsg("&#9370db&lSkill Name"), 
            "&7Current: &f" + skill, 
            "", "&eClick to change skill name"));

        // Slot 22: Operator
        inventory.setItem(22, InvUtils.createItemStacks(Material.ANVIL, 
            StaticColors.getHexMsg("&#9370db&lEdit Operator"), 
            "&7Current: &f" + op, 
            "", "&7Allowed: &f>=, <=, >, <, =", 
            "", "&eClick to change operator"));

        // Slot 24: Level
        inventory.setItem(24, InvUtils.createItemStacks(Material.EXPERIENCE_BOTTLE, 
            StaticColors.getHexMsg("&#9370db&lEdit Level"), 
            "&7Current: &f" + level, 
            "", "&eClick to change value"));

        // Slot 26: Deny Message
        String deny = config.getString(fullPath + ".deny", "");
        inventory.setItem(26, InvUtils.createItemStacks(Material.OAK_SIGN, 
            StaticColors.getHexMsg("&#9370db&lEdit Deny Message"), 
            "&7Current: &f" + (deny.isEmpty() ? "&8(Default)" : deny), 
            "", "&7Placeholders: &f%skill%, %level%, %operator%, %current%", 
            "", "&eClick to change message"));

        // Back Button
        inventory.setItem(31, InvUtils.createItemStacks(Material.ARROW, StaticColors.getHexMsg("&cBack"), "&7Return to List"));

        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SkillDetailMenu)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        SkillDetailMenu holder = (SkillDetailMenu) event.getInventory().getHolder();
        String fullPath = holder.path + ".requirements.skills." + holder.reqId;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");

        if (event.getRawSlot() == 31) { // Back
            new RequiredSkillsMenu(plugin).open(player, holder.matName, holder.regionName, holder.path);
            return;
        }

        if (event.getRawSlot() == 20) { // Skill
            ChatInputListener.requestInput(player, "Enter Skill Name", "e.g. Farming, Mining, Woodcutting", input -> {
                config.set(fullPath + ".skill", input);
                plugin.getConfigManager().saveConfig("crops.yml");
                player.openInventory(holder.getInventory());
            }, () -> player.openInventory(holder.getInventory()));
        } else if (event.getRawSlot() == 22) { // Operator
            ChatInputListener.requestInput(player, "Enter Operator", ">=, <=, >, <, =", input -> {
                config.set(fullPath + ".operator", input);
                plugin.getConfigManager().saveConfig("crops.yml");
                player.openInventory(holder.getInventory());
            }, () -> player.openInventory(holder.getInventory()));
        } else if (event.getRawSlot() == 24) { // Level
            ChatInputListener.requestInput(player, "Enter Required Level", "Integer value", input -> {
                try {
                    config.set(fullPath + ".level", Integer.parseInt(input));
                    plugin.getConfigManager().saveConfig("crops.yml");
                } catch (Exception ignored) {}
                player.openInventory(holder.getInventory());
            }, () -> player.openInventory(holder.getInventory()));
        } else if (event.getRawSlot() == 26) { // Deny Message
            ChatInputListener.requestInput(player, "Enter Deny Message", "Placeholders: %skill%, %level%, %operator%, %current%", input -> {
                config.set(fullPath + ".deny", input);
                plugin.getConfigManager().saveConfig("crops.yml");
                player.openInventory(holder.getInventory());
            }, () -> player.openInventory(holder.getInventory()));
        }
    }
}
