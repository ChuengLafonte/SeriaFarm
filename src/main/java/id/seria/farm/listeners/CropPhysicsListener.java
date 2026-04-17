package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

public class CropPhysicsListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public CropPhysicsListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevents custom garden plants from popping off/breaking due to vanilla physics.
     * This allows crops to be grown in caves (low light) or without air blocks above
     * if the user manually placed them.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropPhysics(BlockPhysicsEvent event) {
        // If the block being updated is a registered custom garden plant,
        // cancel the physics check to prevent it from breaking.
        if (plugin.getCustomPlantManager().isCustomPlant(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
