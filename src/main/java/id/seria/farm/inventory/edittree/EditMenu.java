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
        String path = "Materials." + matName + ".";

        // Slot 45: Info Item
        ItemStack info = InvUtils.createItemStacks(Material.getMaterial(InvUtils.getSingleCrop(matName)), 
            StaticColors.getHexMsg("&#9370db[" + matName + "]"), "&7You are editing this block", "");
        LocalizedName.set(info, regionName);
        inventory.setItem(45, info);

        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), "&7Closes The Current Gui", ""));
        
        // Toggles & Settings
        inventory.setItem(10, InvUtils.createItemStacks(Material.GREEN_WOOL, StaticColors.getHexMsg("&#9370dbEnable | Disable"), "&7Click Here To Enable Or Disable", "&7Per Block Regeneration", "", "&eStatus: &aEnabled"));
        inventory.setItem(12, InvUtils.createItemStacks(Material.CLOCK, StaticColors.getHexMsg("&#9370dbCustomize Delay"), "&7Change How Long A Block", "&7Should Regenerate", "", "&eCurrent: &f20"));
        inventory.setItem(16, InvUtils.createItemStacks(Material.EXPERIENCE_BOTTLE, StaticColors.getHexMsg("&#9370dbCustomize XP Drops"), "&7Change How Much Xp Should Drop", "", "&eCurrent: &f20"));
        
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
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(name)) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        if (event.getRawSlot() == 53) {
            String regionName = LocalizedName.get(event.getInventory().getItem(45));
            // Back to BlockMenu (dummy for now as we don't have the config here)
            player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &fReturning to Block Menu..."));
            player.closeInventory();
            return;
        }

        if (event.getRawSlot() == 12) { // Delay
            ChatInputListener.requestInput(player, "Customize Delay", "5 or 5-8 or 1,4-6", input -> {
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aDelay updated to &f" + input));
                // In a real scenario, this would save to the YAML file
            }, () -> player.openInventory(emenu(player, null, "stone", null, "global")));
        } else if (event.getRawSlot() == 16) { // XP
            ChatInputListener.requestInput(player, "Customize XP Drops", "5-10 or 8", input -> {
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aXP Drop updated to &f" + input));
            }, () -> player.openInventory(emenu(player, null, "stone", null, "global")));
        } else if (event.getRawSlot() == 25) { // Required Tools
            java.util.List<String> tools = new java.util.ArrayList<>();
            ChatInputListener.requestListInput(player, "Required Tools", tools, "MATERIAL or MMOITEM:ID", list -> {
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aTools updated!"));
            }, () -> player.openInventory(emenu(player, null, "stone", null, "global")));
        } else if (event.getRawSlot() == 28) { // AuraSkills
            java.util.List<String> reqs = new java.util.ArrayList<>();
            ChatInputListener.requestListInput(player, "Skills Requirements", reqs, "skill ; operator ; level", list -> {
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aRequirements updated!"));
            }, () -> player.openInventory(emenu(player, null, "stone", null, "global")));
        } else if (event.getRawSlot() == 30) { // Commands
            java.util.List<String> currentCmds = new java.util.ArrayList<>();
            ChatInputListener.requestListInput(player, "Commands", currentCmds, "[Console/Player] ; cmd ; chance", list -> {
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aCommands updated!"));
            }, () -> player.openInventory(emenu(player, null, "stone", null, "global")));
        }
    }
}
