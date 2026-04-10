package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.event.EventHandler;
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

    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        // If the block is currently in the middle of a plugin-managed regeneration/growth cycle,
        // cancel the natural Minecraft growth tick.
        if (plugin.getRegenManager().isRegenerating(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
