package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.util.Arrays;
import id.seria.farm.listeners.ChatInputListener;

public class EditMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private static final String name = StaticColors.getHexMsg("&#9370db&lEdit Menu");

    public EditMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory emenu(Player player, YamlConfiguration config, String matName, File file, String regionName) {
        Inventory inventory = Bukkit.createInventory(player, 54, name);
        String[] parts = matName.split(":");
        String section = parts.length > 1 ? parts[0] : "legacy";
        String materialKey = parts.length > 1 ? parts[1] : matName;
        
        String path = section.equalsIgnoreCase("legacy") ? "blocks." + materialKey + "." : "blocks." + section + "." + materialKey + ".";
        
        int delay = config.getInt(path + "regen-delay", 20);
        int xp = config.getInt(path + "rewards.xp", 0);

        // Slot 45: Info Item
        ItemStack icon = plugin.getHookManager().getItem(materialKey);
        ItemStack info = InvUtils.applyMeta(icon, StaticColors.getHexMsg("&#9370db[" + materialKey + "]"), "&7You are editing this block", "");
        LocalizedName.set(info, matName + ":" + regionName);
        inventory.setItem(45, info);

        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), "&7Closes The Current Gui", ""));
        
        // Toggles & Settings
        inventory.setItem(10, InvUtils.createItemStacks(Material.GREEN_WOOL, StaticColors.getHexMsg("&#9370dbEnable | Disable"), "&7Click Here To Enable Or Disable", "&7Per Block Regeneration", "", "&eStatus: &aEnabled"));
        inventory.setItem(12, InvUtils.createItemStacks(Material.CLOCK, StaticColors.getHexMsg("&#9370dbCustomize Delay"), "&7Change How Long A Block", "&7Should Regenerate", "", "&eCurrent: &f" + delay));
        inventory.setItem(16, InvUtils.createItemStacks(Material.EXPERIENCE_BOTTLE, StaticColors.getHexMsg("&#9370dbCustomize XP Drops"), "&7Change How Much Xp Should Drop", "", "&eCurrent: &f" + xp));
        
        inventory.setItem(19, InvUtils.createItemStacks(Material.STONE, StaticColors.getHexMsg("&#9370dbCustomize Replace Block"), "&7Select Which Block Should Be Replaced", "&7After Regeneration", "", "&eClick to edit"));
        inventory.setItem(21, InvUtils.createItemStacks(Material.BEDROCK, StaticColors.getHexMsg("&#9370dbCustomize Delay Block"), "&7Select Which Block Should Be Replaced", "&7During Regeneration", "", "&eClick to edit"));
        inventory.setItem(23, InvUtils.createItemStacks(Material.DROPPER, StaticColors.getHexMsg("&#9370dbCustomize Drops"), "&7Add A List Of Drops", "&7With Custom Chances", "", "&eClick to edit"));
        
        inventory.setItem(25, InvUtils.createItemStacks(Material.DIAMOND_PICKAXE, StaticColors.getHexMsg("&#9370dbCustomize Required Tools"), "&7Add A List Of Materials or MMOItems", "&7Which Player Should Use To mine", "", "&eClick to edit"));
        
        inventory.setItem(28, InvUtils.createItemStacks(Material.ANVIL, StaticColors.getHexMsg("&#9370dbAuraSkills Requirements"), "&7Add level requirements for skills", "&7Format: <skill> ; <operator> ; <level>", "", "&eClick to edit"));
        
        inventory.setItem(30, InvUtils.createItemStacks(Material.COMMAND_BLOCK_MINECART, StaticColors.getHexMsg("&#9370dbAdd Commands"), "&7Add A List Of Commands", "&7Which Runs When Player Mines", "", "&eClick to edit"));

        ItemStack glass = InvUtils.createItemStacks(Material.PURPLE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 49, 48, 50, 51, 52}) {
            if (inventory.getItem(n) == null) inventory.setItem(n, glass);
        }

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Edit Menu")) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        ItemStack infoItem = event.getInventory().getItem(45);
        if (infoItem == null) return;
        
        String data = LocalizedName.get(infoItem);
        if (data == null || !data.contains(":")) return;
        
        String matName = data.split(":")[0];
        String regionName = data.split(":")[1];
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        File file = plugin.getConfigManager().getConfigFile("materials.yml");
        String path = "blocks." + matName + ".";

        if (event.getRawSlot() == 53) {
            player.openInventory(new BlockMenu(plugin).blockmenu(player, 1, config, regionName));
            return;
        }

        if (event.getRawSlot() == 12) { // Delay
            ChatInputListener.requestInput(player, "Customize Delay", "Integer value (e.g. 20)", input -> {
                try {
                    config.set(path + "regen-delay", Integer.parseInt(input));
                    plugin.getConfigManager().saveConfig("materials.yml");
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aDelay updated to &f" + input));
                } catch (NumberFormatException e) {
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cInvalid input. Please enter a number."));
                }
                player.openInventory(emenu(player, config, matName, file, regionName));
            }, () -> player.openInventory(emenu(player, config, matName, file, regionName)));
        } else if (event.getRawSlot() == 19) { // Replace Block
            new ReplaceBlockMenu(plugin).open(player, matName, regionName);
        } else if (event.getRawSlot() == 16) { // XP
            ChatInputListener.requestInput(player, "Customize XP Drops", "Integer value", input -> {
                try {
                    config.set(path + "rewards.xp", Integer.parseInt(input));
                    plugin.getConfigManager().saveConfig("materials.yml");
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aXP Drop updated to &f" + input));
                } catch (NumberFormatException e) {
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cInvalid input. Please enter a number."));
                }
                player.openInventory(emenu(player, config, matName, file, regionName));
            }, () -> player.openInventory(emenu(player, config, matName, file, regionName)));
        } else if (event.getRawSlot() == 25) { // Required Tools
            java.util.List<String> tools = config.getStringList(path + "requirements.tools");
            ChatInputListener.requestListInput(player, "Required Tools", tools, "MATERIAL or MMOITEM:ID", list -> {
                config.set(path + "requirements.tools", list);
                plugin.getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aTools updated!"));
                player.openInventory(emenu(player, config, matName, file, regionName));
            }, () -> player.openInventory(emenu(player, config, matName, file, regionName)));
        } else if (event.getRawSlot() == 28) { // AuraSkills
            java.util.List<String> reqs = config.getStringList(path + "requirements.skills");
            ChatInputListener.requestListInput(player, "Skills Requirements", reqs, "skill ; operator ; level", list -> {
                config.set(path + "requirements.skills", list);
                plugin.getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aRequirements updated!"));
                player.openInventory(emenu(player, config, matName, file, regionName));
            }, () -> player.openInventory(emenu(player, config, matName, file, regionName)));
        } else if (event.getRawSlot() == 30) { // Commands
            java.util.List<String> currentCmds = config.getStringList(path + "rewards.commands");
            ChatInputListener.requestListInput(player, "Commands", currentCmds, "[Console/Player] ; cmd ; chance", list -> {
                config.set(path + "rewards.commands", list);
                plugin.getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aCommands updated!"));
                player.openInventory(emenu(player, config, matName, file, regionName));
            }, () -> player.openInventory(emenu(player, config, matName, file, regionName)));
        }
    }
}
