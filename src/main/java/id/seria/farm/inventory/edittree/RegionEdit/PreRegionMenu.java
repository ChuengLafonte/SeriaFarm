package id.seria.farm.inventory.edittree.RegionEdit;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import id.seria.farm.inventory.edittree.BlockMenu;
import id.seria.farm.inventory.utils.LocalizedName;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PreRegionMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private static final String name = StaticColors.getHexMsg("&9&lRegion Info Menu");

    public PreRegionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory preregenmenu(Player player, String string) {
        plugin.getVisualManager().setFocusedRegion(player, string);
        Inventory inventory = Bukkit.createInventory(player, 36, name);
        FileConfiguration config = plugin.getConfigManager().getConfig("regions.yml");
        String path = "regions." + string + ".";

        String locStr = config.getString(path + "teleport-location", "world;0;0;0;0;0");
        String[] parts = locStr.split(";");
        String displayLoc = parts.length >= 4 ? parts[1] + " " + parts[2] + " " + parts[3] : "Unknown";

        // Slot 11: Info Item
        ItemStack info = InvUtils.createItemStacks(Material.SEAGRASS, StaticColors.getHexMsg("&9[" + string + "]"), 
            StaticColors.getHexMsg("&7World: " + (parts.length > 0 ? parts[0] : "world")), 
            StaticColors.getHexMsg("&7Location: " + displayLoc), 
            "", StaticColors.getHexMsg("&7Click To &aTeleport"));
        LocalizedName.set(info, string);
        inventory.setItem(11, info);

        inventory.setItem(20, InvUtils.createItemStacks(Material.GREEN_STAINED_GLASS_PANE, StaticColors.getHexMsg("&9Regeneration Status"), StaticColors.getHexMsg("&7Toggle Whether This Region Has Regeneration"), "", 
            StaticColors.getHexMsg("&7Current Status: " + (config.getBoolean(path + "enabled", true) ? "&aEnabled" : "&cDisabled"))));
        
        inventory.setItem(13, InvUtils.createItemStacks(Material.IRON_BARS, StaticColors.getHexMsg("&9Per Region Regeneration"), StaticColors.getHexMsg("&7Should Region Regenerate As Per Region Setting"), "", 
            StaticColors.getHexMsg("&7Current Status: " + (config.getBoolean(path + "per-region-regen", true) ? "&aEnabled" : "&cDisabled"))));
        
        inventory.setItem(22, InvUtils.createItemStacks(Material.GOLDEN_PICKAXE, StaticColors.getHexMsg("&9Per Region Perms"), StaticColors.getHexMsg("&7Does Player Require Permission"), "", 
            StaticColors.getHexMsg("&7Current Status: " + (config.getBoolean(path + "require-permission", false) ? "&aEnabled" : "&cDisabled"))));
        
        inventory.setItem(15, InvUtils.createItemStacks(Material.GRASS_BLOCK, StaticColors.getHexMsg("&9Allow Block Place"), StaticColors.getHexMsg("&7Players Can Place Block"), "", 
            StaticColors.getHexMsg("&7Current Status: " + (config.getBoolean(path + "allow-block-place", false) ? "&aEnabled" : "&cDisabled"))));
        
        inventory.setItem(24, InvUtils.createItemStacks(Material.MINECART, StaticColors.getHexMsg("&9Edit Blocks Menu"), StaticColors.getHexMsg("&7Edit How Block Regenerates")));
        inventory.setItem(35, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        ItemStack blueGlass = InvUtils.createItemStacks(Material.BLUE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 28, 29, 30, 31, 32, 33, 34}) {
            inventory.setItem(n, blueGlass);
        }
        
        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!ChatColor.translateAlternateColorCodes('&', event.getView().getTitle()).equals(name)) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        ItemStack infoItem = event.getInventory().getItem(11);
        if (infoItem == null) return;
        String regionName = LocalizedName.get(infoItem);
        FileConfiguration config = plugin.getConfigManager().getConfig("regions.yml");
        String path = "regions." + regionName + ".";

        if (event.getRawSlot() == 35) {
            plugin.getVisualManager().setFocusedRegion(player, null);
            player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1));
            return;
        }

        switch (event.getRawSlot()) {
            case 11: // Teleport
                String p1Str = config.getString(path + "pos1");
                String p2Str = config.getString(path + "pos2");
                if (p1Str != null && p2Str != null) {
                    Location l1 = deserializeLoc(p1Str);
                    Location l2 = deserializeLoc(p2Str);
                    if (l1 != null && l2 != null) {
                        double midX = (l1.getX() + l2.getX() + 1) / 2.0;
                        double midZ = (l1.getZ() + l2.getZ() + 1) / 2.0;
                        double midY = Math.min(l1.getY(), l2.getY());
                        
                        Location target = new Location(l1.getWorld(), midX, midY, midZ, player.getLocation().getYaw(), player.getLocation().getPitch());
                        player.teleport(target);
                        player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &fTeleported to the center of &b" + regionName));
                        return;
                    }
                }
                
                // Fallback to legacy teleport location if pos1/2 missing
                String locStr = config.getString(path + "teleport-location");
                if (locStr != null) {
                    String[] p = locStr.split(";");
                    if (p.length >= 4) {
                        Location loc = new Location(Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), 
                                        p.length > 4 ? Float.parseFloat(p[4]) : 0, p.length > 5 ? Float.parseFloat(p[5]) : 0);
                        player.teleport(loc);
                        player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &fTeleported to &b" + regionName));
                    }
                }
                break;
            case 20: // Enabled
                config.set(path + "enabled", !config.getBoolean(path + "enabled", true));
                saveAndRefresh(player, regionName);
                break;
            case 13: // Per Region Regen
                config.set(path + "per-region-regen", !config.getBoolean(path + "per-region-regen", true));
                saveAndRefresh(player, regionName);
                break;
            case 22: // Permission
                config.set(path + "require-permission", !config.getBoolean(path + "require-permission", false));
                saveAndRefresh(player, regionName);
                break;
            case 15: // Block Place
                config.set(path + "allow-block-place", !config.getBoolean(path + "allow-block-place", false));
                saveAndRefresh(player, regionName);
                break;
            case 24: // Edit Blocks
                YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("materials.yml");
                player.openInventory(new BlockMenu(plugin).blockmenu(player, 1, matConfig, regionName));
                break;
        }
    }

    private void saveAndRefresh(Player player, String regionName) {
        plugin.getConfigManager().saveConfig("regions.yml");
        player.openInventory(preregenmenu(player, regionName));
    }

    private Location deserializeLoc(String str) {
        try {
            String[] p = str.split(";");
            if (p.length >= 4) {
                return new Location(Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            }
        } catch (Exception ignored) {}
        return null;
    }
}
