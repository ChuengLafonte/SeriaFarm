package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockPlaceListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public BlockPlaceListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // 0. Check if plugin is globally enabled
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.enabled", true)) return;

        Block block = event.getBlock();

        // 1. Check if this is a managed crop/block
        String blockKey = plugin.getRegenManager().findBlockKey(block, null); // Silent check for key
        
        if (blockKey != null) {
            // 2. Check Requirement Engine (Engine handles messaging)
            ConfigurationSection config = plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops." + blockKey);
            if (config != null && !plugin.getRequirementEngine().canPlace(event.getPlayer(), config)) {
                event.setCancelled(true);
                return;
            }

            // 3. Automatically start tracking if it's a managed block
            plugin.getRegenManager().startAdHocTracking(block, blockKey);
        }
    }
}
