package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

/**
 * Suppresses natural Minecraft growth for blocks managed by SeriaFarm
 * to ensure the plugin's 'regen-delay' is the absolute source of truth.
 */
public class GrowthListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public GrowthListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Location loc = event.getBlock().getLocation();
        
        // 1. If currently regenerating/growing via plugin, block vanilla growth
        if (plugin.getRegenManager().isRegenerating(loc)) {
            event.setCancelled(true);
            return;
        }

        // 2. If it's a managed block in a region, block vanilla growth 
        // to prevent vanilla rules from overriding regen-delay.
        if (plugin.getRegenManager().getRegionAt(loc) != null) {
            // Check if this material is managed in THIS region
            // (Heuristic: most crops in regions are managed)
            if (event.getBlock().getBlockData() instanceof org.bukkit.block.data.Ageable) {
                event.setCancelled(true);
            }
        }
    }
}
