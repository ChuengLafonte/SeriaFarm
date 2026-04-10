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
import org.bukkit.event.inventory.InventoryCloseEvent;

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
            String target = InvUtils.extractStr(event.getInventory().getItem(45).getItemMeta().getDisplayName());
            if (target == null) target = "global";
            
            org.bukkit.configuration.file.FileConfiguration config = SeriaFarmPlugin.getInstance().getConfigManager().getConfig("materials.yml");
            SeriaFarmPlugin plugin = SeriaFarmPlugin.getInstance();
            
            int added = 0;
            for (int i = 10; i <= 43; i++) {
                if (isBorder(i)) continue;
                ItemStack item = event.getInventory().getItem(i);
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    String identifier = plugin.getHookManager().getItemIdentifier(item);
                    String matKey = identifier.replace(":", "-").toLowerCase();
                    String path = "blocks." + target + "." + matKey;
                    
                    config.set(path + ".material", identifier);
                    config.set(path + ".regen-delay", 20);
                    added++;
                    
                    // Clear item so it's not returned on close
                    event.getInventory().setItem(i, null);
                }
            }
            
            if (added > 0) {
                SeriaFarmPlugin.getInstance().getConfigManager().saveConfig("materials.yml");
                player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aSuccessfully added &f" + added + " &ablocks to &b" + target));
            }
            player.openInventory(new MainMenu(SeriaFarmPlugin.getInstance()).mainmenu(player));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(name)) return;
        
        Inventory inv = event.getInventory();
        Player player = (Player) event.getPlayer();
        
        for (int i = 0; i < 54; i++) {
            if (isBorder(i)) continue;
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                java.util.Map<Integer, ItemStack> left = player.getInventory().addItem(item);
                for (ItemStack remaining : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), remaining);
                }
                inv.setItem(i, null);
            }
        }
    }

    private boolean isBorder(int slot) {
        int[] cancelledSlots = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52, 45, 49, 53};
        for (int s : cancelledSlots) {
            if (s == slot) return true;
        }
        return false;
    }
}
