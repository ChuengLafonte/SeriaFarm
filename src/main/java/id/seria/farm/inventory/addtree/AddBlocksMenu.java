package id.seria.farm.inventory.addtree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import id.seria.farm.inventory.utils.LocalizedName;
import org.bukkit.event.inventory.InventoryCloseEvent;
import java.util.*;
import id.seria.farm.managers.GuiManager;

public class AddBlocksMenu implements Listener {
    private final SeriaFarmPlugin plugin = SeriaFarmPlugin.getInstance();

    public Inventory addblocks_menu(Player player, String target) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%target%", target);

        Inventory inventory = plugin.getGuiManager().createInventory("add-blocks-menu", placeholders);
        
        // Metadata for handlers (The hidden target info)
        ItemStack infoItem = inventory.getItem(45);
        if (infoItem != null) {
            LocalizedName.set(infoItem, target);
        }

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("add-blocks-menu")) return;
        
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();

        if (isBorder(slot)) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType() == Material.AIR) return;
            
            String action = LocalizedName.get(clicked);
            if (action.equals("close_to_main")) {
                player.openInventory(new MainMenu(plugin).mainmenu(player));
                return;
            }

            if (action.equals("confirm_add")) {
                ItemStack targetItem = event.getInventory().getItem(45);
                if (targetItem == null) return;
                
                String target = LocalizedName.get(targetItem);
                if (target == null) target = "global";
                
                org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getConfig("crops.yml");
                
                int added = 0;
                int rejected = 0;
                for (int i = 0; i < 54; i++) {
                    if (isBorder(i)) continue;
                    ItemStack item = event.getInventory().getItem(i);
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        if (!isPlantBlock(item.getType())) {
                            rejected++;
                            continue;
                        }
                        String identifier = plugin.getHookManager().getItemIdentifier(item);
                        String matKey = identifier.replace(":", "-").toUpperCase();
                        
                        String path = target.equalsIgnoreCase("global") ? "crops.global." + matKey : "crops." + target + "." + matKey;
                        
                        config.set(path + ".material", identifier);
                        config.set(path + ".regen-delay", 20);
                        added++;
                        event.getInventory().setItem(i, null);
                    }
                }
                
                if (added > 0) {
                    plugin.getConfigManager().saveConfig("crops.yml");
                    plugin.getConfigManager().sendPrefixedMessage(player, "&aSuccessfully added &f" + added + " &ablocks to &b" + target);
                }
                if (rejected > 0) {
                    plugin.getConfigManager().sendPrefixedMessage(player, "&cRejected &f" + rejected + " &cnon-plant blocks.");
                }
                player.openInventory(new MainMenu(plugin).mainmenu(player));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("add-blocks-menu")) return;
        
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

    private boolean isPlantBlock(Material mat) {
        String name = mat.name();
        return name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO") || 
               name.contains("BEETROOT") || name.contains("NETHER_WART") || name.contains("COCOA") || 
               name.contains("CANE") || name.contains("CACTUS") || name.contains("BAMBOO") || 
               name.contains("KELP") || name.contains("MELON") || name.contains("PUMPKIN") || 
               name.contains("BERRY") || name.contains("VINE") || name.contains("MUSHROOM") || 
               name.contains("FLOWER") || name.contains("SAPLING") || name.contains("LEAVES") ||
               mat.isBlock() && mat.createBlockData() instanceof org.bukkit.block.data.Ageable;
    }

    private boolean isBorder(int slot) {
        // Updated to match the slots defined as borders/fillers/items in gui.yml
        int[] cancelledSlots = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int s : cancelledSlots) {
            if (s == slot) return true;
        }
        return false;
    }
}
