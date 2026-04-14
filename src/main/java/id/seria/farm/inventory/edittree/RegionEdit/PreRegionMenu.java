package id.seria.farm.inventory.edittree.RegionEdit;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PreRegionMenu implements Listener, InventoryHolder {
    private final SeriaFarmPlugin plugin;
    private static final Component name = StaticColors.getHexMsg("&9&lRegion Info Menu");
    private String regionName;

    public PreRegionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public String getRegionName() { return regionName; }

    public Inventory preregenmenu(Player player, String string) {
        this.regionName = string;
        plugin.getVisualManager().setFocusedRegion(player, string);
        Inventory inventory = Bukkit.createInventory(this, 36, name);
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
        
        // Slot 31: Scan Region
        inventory.setItem(31, InvUtils.createItemStacks(Material.ENDER_EYE, StaticColors.getHexMsg("&b&lSCAN REGION"), 
            StaticColors.getHexMsg("&7Detects all blocks inside the region."), 
            StaticColors.getHexMsg("&7and automatically adds them to the list."), 
            "", StaticColors.getHexMsg("&eLeft-Click To Start Scanning")));

        inventory.setItem(35, InvUtils.createItemStacks(Material.BARRIER, StaticColors.getHexMsg("&cClose | Exit"), StaticColors.getHexMsg("&7Closes The Current Gui"), ""));

        ItemStack blueGlass = InvUtils.createItemStacks(Material.BLUE_STAINED_GLASS_PANE, " ", "", "");
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 28, 29, 30, 32, 33, 34}) {
            inventory.setItem(n, blueGlass);
        }
        
        return inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null; // Holder identification only
    }

    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        String title = SeriaFarmPlugin.MINI_MESSAGE.serialize(event.getView().title());
        boolean isHolder = event.getInventory().getHolder() instanceof PreRegionMenu;
        boolean isTitle = title.contains("Region Info Menu");

        if (!isHolder && !isTitle) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        
        // Debug Logging
        

        PreRegionMenu holder;
        if (isHolder) {
            holder = (PreRegionMenu) event.getInventory().getHolder();
        } else {
            // Cannot reliably proceed without holder state in this architecture
            return; 
        }

        final String regionName = holder.getRegionName();
        if (regionName == null || regionName.isEmpty()) {
        
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig("regions.yml");
        String path = "regions." + regionName + ".";

        switch (event.getRawSlot()) {
            case 35: // Back
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new RegionSelectionMenu(plugin).reg_sel(player, 1)));
                plugin.getVisualManager().setFocusedRegion(player, null);
                break;
            case 11: // Teleport
                teleportLogic(player, config, path, regionName);
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
            case 15: // Allow Block Place
                config.set(path + "allow-block-place", !config.getBoolean(path + "allow-block-place", false));
                saveAndRefresh(player, regionName);
                break;
            case 24: // Edit Blocks
                YamlConfiguration mConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.edittree.BlockMenu(plugin).blockmenu(player, 1, mConfig, regionName)));
                break;
            case 31: // SCAN REGION
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
