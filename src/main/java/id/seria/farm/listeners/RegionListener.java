package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class RegionListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public RegionListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // 0. Check if plugin is globally enabled
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.enabled", true)) return;

        Player player = event.getPlayer();
        if (player.isOp()) return; // OP always allowed

        Location loc = event.getBlock().getLocation();
        String regionName = plugin.getRegenManager().getRegionAt(loc);
        
        if (regionName != null) {
            FileConfiguration config = plugin.getConfigManager().getConfig("regions.yml");
            boolean allowPlace = config.getBoolean("regions." + regionName + ".allow-block-place", false);
            
            if (!allowPlace) {
                event.setCancelled(true);
                String prefix = plugin.getConfig().getString("settings.prefix", "&6&lSeriaFarm &8»");
                player.sendMessage(StaticColors.getHexMsg(prefix + " &cYou are not allowed to place blocks in this region."));
            }
        }
    }
}
