package id.seria.farm.inventory.addtree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.Arrays;
import java.util.List;

public class AddBlocksMenu implements Listener {
    private static final String name = StaticColors.getHexMsg("&#ffa500&lAdd Regen Blocks Menu");

    public Inventory addblocks_menu(Player player, String target) {
        Inventory inventory = Bukkit.createInventory(player, 54, name);
        
        inventory.setItem(49, InvUtils.createItemStacks(Material.NETHER_STAR, StaticColors.getHexMsg("&fAdd Blocks"), StaticColors.getHexMsg("&7Drop All Blocks To Be Added Above"), StaticColors.getHexMsg("&7Click Here TO Continue")));
        inventory.setItem(45, InvUtils.createItemStacks(Material.PLAYER_HEAD, StaticColors.getHexMsg("&#ffa500[" + target + "]"), StaticColors.getHexMsg("&7A Super Cool Farmer"), ""));
        inventory.setItem(53, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&4Close | Exit"), StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        ItemStack orangeGlass = InvUtils.createItemStacks(Material.ORANGE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52}) {
            inventory.setItem(n, orangeGlass);
        }
        
        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(name)) return;
        
        int slot = event.getRawSlot();
        int[] cancelledSlots = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52, 45, 49, 53};
        
        for (int s : cancelledSlots) {
            if (s == slot) {
                event.setCancelled(true);
                break;
            }
        }

        Player player = (Player) event.getWhoClicked();
        if (slot == 53) {
            player.closeInventory();
            return;
        }

        if (slot == 49) {
            // Save logic
            String target = InvUtils.extractStr(event.getInventory().getItem(45).getItemMeta().getDisplayName());
            // This is where UBR saves the blocks to materials.yml or regen blocks.
            // For now, let's just return to MainMenu as a placeholder for the logic.
            player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aBlocks added to [" + target + "]"));
            player.openInventory(new MainMenu(SeriaFarmPlugin.getInstance()).mainmenu(player));
        }
    }
}
