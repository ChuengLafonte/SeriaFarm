package id.seria.farm.inventory.edittree.RegionEdit;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import id.seria.farm.inventory.utils.LocalizedName;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;
import id.seria.farm.managers.GuiManager;

public class PreRegionMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private String regionName;

    public PreRegionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public String getRegionName() { return regionName; }

    public Inventory preregenmenu(Player player, String string) {
        this.regionName = string;
        plugin.getVisualManager().setFocusedRegion(player, string);
        
        FileConfiguration config = plugin.getConfigManager().getConfig("regions.yml");
        String path = "regions." + string + ".";

        String locStr = config.getString(path + "teleport-location", "world;0;0;0;0;0");
        String[] parts = locStr.split(";");
        String displayLoc = parts.length >= 4 ? parts[1] + " " + parts[2] + " " + parts[3] : "Unknown";

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%region%", string);
        placeholders.put("%world%", parts.length > 0 ? parts[0] : "world");
        placeholders.put("%loc%", displayLoc);
        placeholders.put("%status%", config.getBoolean(path + "enabled", true) ? "&aEnabled" : "&cDisabled");
        placeholders.put("%mode%", config.getBoolean(path + "per-region-regen", true) ? "&aEnabled" : "&cDisabled");
        placeholders.put("%perms%", config.getBoolean(path + "require-permission", false) ? "&aEnabled" : "&cDisabled");
        placeholders.put("%place%", config.getBoolean(path + "allow-block-place", false) ? "&aEnabled" : "&cDisabled");

        Inventory inventory = plugin.getGuiManager().createInventory("pre-region-menu", placeholders);

        // Metadata for handlers (The info item in slot 11)
        ItemStack infoItem = inventory.getItem(11);
        if (infoItem != null) LocalizedName.set(infoItem, string);

        return inventory;
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("pre-region-menu")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String action = LocalizedName.get(clicked);
        
        // Find regionName from slot 11 metadata
        ItemStack infoItem = event.getInventory().getItem(11);
        if (infoItem == null) return;
        final String regionName = LocalizedName.get(infoItem);
        if (regionName == null || regionName.isEmpty()) return;

        FileConfiguration config = plugin.getConfigManager().getConfig("regions.yml");
        String path = "regions." + regionName + ".";

        switch (action) {
            case "back_to_list":
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1)));
                plugin.getVisualManager().setFocusedRegion(player, null);
                break;
            case "teleport":
                teleportLogic(player, config, path, regionName);
                break;
            case "toggle_enabled":
                config.set(path + "enabled", !config.getBoolean(path + "enabled", true));
                saveAndRefresh(player, regionName);
                break;
            case "toggle_per_region":
                config.set(path + "per-region-regen", !config.getBoolean(path + "per-region-regen", true));
                saveAndRefresh(player, regionName);
                break;
            case "toggle_perms":
                config.set(path + "require-permission", !config.getBoolean(path + "require-permission", false));
                saveAndRefresh(player, regionName);
                break;
            case "toggle_place":
                config.set(path + "allow-block-place", !config.getBoolean(path + "allow-block-place", false));
                saveAndRefresh(player, regionName);
                break;
            case "edit_blocks":
                YamlConfiguration mConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.edittree.BlockMenu(plugin).blockmenu(player, 1, mConfig, regionName)));
                break;
            case "scan_region":
                performScan(player, config, path, regionName);
                break;
        }
    }

    private void teleportLogic(Player player, FileConfiguration config, String path, String regionName) {
        String worldName = config.getString(path + "teleport-location", "world;0;0;0").split(";")[0];
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getConfigManager().sendPrefixedMessage(player, "&cWorld '&f" + worldName + "&c' is not loaded!");
            return;
        }

        String p1Str = config.getString(path + "pos1");
        String p2Str = config.getString(path + "pos2");
        if (p1Str != null && p2Str != null) {
            Location l1 = deserializeLoc(p1Str);
            Location l2 = deserializeLoc(p2Str);
            if (l1 != null && l2 != null) {
                double midX = (l1.getX() + l2.getX() + 1) / 2.0;
                double midZ = (l1.getZ() + l2.getZ() + 1) / 2.0;
                double midY = Math.max(l1.getY(), l2.getY()) + 1; 
                
                Location target = new Location(world, midX, midY, midZ, player.getLocation().getYaw(), player.getLocation().getPitch());
                player.teleport(target);
                plugin.getConfigManager().sendPrefixedMessage(player, "&fTeleported to the center of &b" + regionName);
                player.closeInventory();
            }
        }
    }

    private void performScan(Player player, FileConfiguration config, String path, String regionName) {
        String p1Str = config.getString(path + "pos1");
        String p2Str = config.getString(path + "pos2");
        if (p1Str == null || p2Str == null) {
            plugin.getConfigManager().sendPrefixedMessage(player, "&cPos1 or Pos2 is not set for this region!");
            return;
        }

        Location l1;
        Location l2;
        try {
            l1 = deserializeLoc(p1Str);
            l2 = deserializeLoc(p2Str);
        } catch (Exception e) {
            plugin.getConfigManager().sendPrefixedMessage(player, "&cInvalid Pos1 or Pos2 data!");
            return;
        }

        plugin.getConfigManager().sendPrefixedMessage(player, "&bScanning region &f" + regionName + "&b...");
        
        java.util.Set<Material> found = id.seria.farm.utils.LocationUtils.getUniqueMaterialsInRange(l1, l2);
        java.util.Set<Material> ignored = new java.util.HashSet<>(java.util.Arrays.asList(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.BEDROCK, Material.BARRIER, Material.WATER, Material.LAVA
        ));

        YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        String blocksPath = "crops." + regionName;
        int count = 0;

        for (Material mat : found) {
            if (ignored.contains(mat)) continue;
            
            boolean exists = false;
            if (matConfig.contains(blocksPath)) {
                for (String key : matConfig.getConfigurationSection(blocksPath).getKeys(false)) {
                    String configMat = matConfig.getString(blocksPath + "." + key + ".material", "");
                    // Use robust fuzzy match from RegenManager
                    if (plugin.getRegenManager().isMatch(mat.name(), configMat, key)) {
                        exists = true;
                        break;
                    }
                }
            }

            if (!exists) {
                String key = mat.name();
                String fullPath = blocksPath + "." + key;
                matConfig.set(fullPath + ".material", mat.name());
                matConfig.set(fullPath + ".regen-delay", 10);
                matConfig.set(fullPath + ".replace-blocks", java.util.Arrays.asList(mat.name()));
                matConfig.set(fullPath + ".delay-blocks", java.util.Arrays.asList("BEDROCK"));
                matConfig.set(fullPath + ".rewards.xp", 0);
                count++;
            }
        }

        if (count > 0) {
            plugin.getConfigManager().saveConfig("crops.yml");
            plugin.getConfigManager().sendPrefixedMessage(player, "&aSuccessfully added &f" + count + " &anew materials to the region!");
        } else {
            plugin.getConfigManager().sendPrefixedMessage(player, "&eNo new materials found to add.");
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
