package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.StaticColors;
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
import id.seria.farm.listeners.ChatInputListener;

public class EditMenu implements Listener, InventoryHolder {
    private static final Component NAME = StaticColors.getHexMsg("&#9370db&lEdit Menu");
    private final SeriaFarmPlugin plugin;

    public EditMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory emenu(Player player, YamlConfiguration config, String matName, File file, String regionName) {
        Inventory inventory = Bukkit.createInventory(this, 54, NAME);
        String path = getConfigPath(matName);
        String materialKey = matName.contains(":") ? matName.split(":")[1] : matName;
        
        int delay = config.getInt(path + ".regen-delay", 20);
        int xp = config.getInt(path + ".rewards.xp", 0);

        // Slot 45: Info Item
        ItemStack icon = plugin.getHookManager().getItem(materialKey);
        ItemStack info = InvUtils.applyMeta(icon, StaticColors.getHexMsg("&#9370db[" + materialKey + "]"), "&7You are editing this block", "");
        LocalizedName.set(info, matName + "|" + regionName);
        inventory.setItem(45, info);

        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), "&7Closes The Current Gui", ""));
        
        // Toggles & Settings
        inventory.setItem(10, InvUtils.createItemStacks(Material.GREEN_WOOL, StaticColors.getHexMsg("&#9370dbEnable | Disable"), "&7Click Here To Enable Or Disable", "&7Per Block Regeneration", "", "&eStatus: &aEnabled"));
        inventory.setItem(12, InvUtils.createItemStacks(Material.CLOCK, StaticColors.getHexMsg("&#9370dbCustomize Delay"), "&7Change How Long A Block", "&7Should Regenerate", "", "&eCurrent: &f" + delay));
        inventory.setItem(16, InvUtils.createItemStacks(Material.EXPERIENCE_BOTTLE, StaticColors.getHexMsg("&#9370dbCustomize XP Drops"), "&7Change How Much Xp Should Drop", "", "&eCurrent: &f" + xp));
        
        inventory.setItem(19, InvUtils.createItemStacks(Material.STONE, StaticColors.getHexMsg("&#9370dbCustomize Final Block"), "&7Select which block should be placed", "&7AFTER regeneration is complete.", "&7(Leave empty to restore original)", "&eClick to edit"));
        inventory.setItem(21, InvUtils.createItemStacks(Material.BEDROCK, StaticColors.getHexMsg("&#9370dbCustomize Delay Block"), "&7Select which block should be placed", "&7DURING the regeneration period.", "&7(Temporary replacement)", "&eClick to edit"));
        inventory.setItem(23, InvUtils.createItemStacks(Material.DROPPER, StaticColors.getHexMsg("&#9370dbCustomize Drops"), "&7Add A List Of Drops", "&7With Custom Chances", "", "&eClick to edit"));
        
        inventory.setItem(25, InvUtils.createItemStacks(Material.DIAMOND_PICKAXE, StaticColors.getHexMsg("&#9370dbCustomize Required Tools"), "&7Add A List Of Materials or MMOItems", "&7Which Player Should Use To mine", "", "&eClick to edit"));
        
        inventory.setItem(28, InvUtils.createItemStacks(Material.ANVIL, StaticColors.getHexMsg("&#9370dbAuraSkills Requirements"), "&7Add level requirements for skills", "&7Format: <skill> ; <operator> ; <level>", "", "&eClick to edit"));
        
        inventory.setItem(30, InvUtils.createItemStacks(Material.COMMAND_BLOCK_MINECART, StaticColors.getHexMsg("&#9370dbAdd Commands"), "&7Add A List Of Commands", "&7Which Runs When Player Mines", "", "&eClick to edit"));

        ItemStack glass = InvUtils.createItemStacks(Material.PURPLE_STAINED_GLASS_PANE, " ", "", "");
        
        // Bamboo Specific Button
        if (materialKey.equalsIgnoreCase("BAMBOO")) {
            inventory.setItem(32, InvUtils.createItemStacks(Material.BAMBOO, StaticColors.getHexMsg("&#228B22&lBamboo Settings"), "&7Special settings for Bamboo", "&7(Max growth height, etc.)", "", "&eClick to edit"));
        }

        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 49, 48, 50, 51, 52}) {
            if (inventory.getItem(n) == null) inventory.setItem(n, glass);
        }

        return inventory;
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
        return null; // Holder identification only
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EditMenu)) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemStack infoItem = inv.getItem(45);
        if (infoItem == null) return;
        
        String data = LocalizedName.get(infoItem);
        if (data == null || !data.contains("|")) return;
        
        String[] parts = data.split("\\|");
        final String finalMatName = parts[0];
        final String finalRegionName = parts[1];

        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
        File file = plugin.getConfigManager().getConfigFile("materials.yml");
        String path = getConfigPath(finalMatName);

        if (event.getRawSlot() == 53) {
            player.openInventory(new BlockMenu(plugin).blockmenu(player, 1, config, finalRegionName));
            return;
        }

        if (event.getRawSlot() == 12) { // Delay
            ChatInputListener.requestInput(player, "Customize Delay", "Integer value (e.g. 20)", input -> {
                try {
                    config.set(path + ".regen-delay", Integer.parseInt(input));
                    plugin.getConfigManager().saveConfig("materials.yml");
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aDelay updated to &f" + input));
                } catch (NumberFormatException e) {
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cInvalid input. Please enter a number."));
                }
                player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
            }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
        } else if (event.getRawSlot() == 19) { // Replace Block (Final)
            new ReplaceBlockMenu(plugin).open(player, finalMatName, finalRegionName, "replace-blocks", path);
        } else if (event.getRawSlot() == 21) { // Delay Block (Temporary)
            new ReplaceBlockMenu(plugin).open(player, finalMatName, finalRegionName, "delay-blocks", path);
        } else if (event.getRawSlot() == 23) { // Custom Drops
            new DropsMenu(plugin).open(player, finalMatName, finalRegionName, path);
        } else if (event.getRawSlot() == 16) { // XP
            ChatInputListener.requestInput(player, "Customize XP Drops", "Integer value", input -> {
                try {
                    config.set(path + ".rewards.xp", Integer.parseInt(input));
                    plugin.getConfigManager().saveConfig("materials.yml");
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aXP Drop updated to &f" + input));
                } catch (NumberFormatException e) {
                    player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &cInvalid input. Please enter a number."));
                }
                player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
            }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
        } else if (event.getRawSlot() == 25) { // Required Tools
            java.util.List<String> tools = config.getStringList(path + ".requirements.tools");
            ChatInputListener.requestListInput(player, "Required Tools", tools, "MATERIAL or MMOITEM:ID", list -> {
                config.set(path + ".requirements.tools", list);
                plugin.getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aTools updated!"));
                player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
            }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
        } else if (event.getRawSlot() == 28) { // AuraSkills
            java.util.List<String> reqs = config.getStringList(path + ".requirements.skills");
            ChatInputListener.requestListInput(player, "Skills Requirements", reqs, "skill ; operator ; level", list -> {
                config.set(path + ".requirements.skills", list);
                plugin.getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aRequirements updated!"));
                player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
            }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
        } else if (event.getRawSlot() == 30) { // Commands
            java.util.List<String> currentCmds = config.getStringList(path + ".rewards.commands");
            ChatInputListener.requestListInput(player, "Commands", currentCmds, "[Console/Player] ; cmd ; chance", list -> {
                config.set(path + ".rewards.commands", list);
                plugin.getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aCommands updated!"));
                player.openInventory(emenu(player, config, finalMatName, file, finalRegionName));
            }, () -> player.openInventory(emenu(player, config, finalMatName, file, finalRegionName)));
        } else if (event.getRawSlot() == 32 && clicked.getType() == Material.BAMBOO) {
            new BambooMenu(plugin).open(player, finalMatName, finalRegionName);
        }


    }
}
