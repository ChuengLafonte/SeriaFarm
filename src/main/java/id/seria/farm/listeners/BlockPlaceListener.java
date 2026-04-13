package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
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
        Player player = event.getPlayer();

        // 1. Check if this is a managed crop/block
        String blockKey = plugin.getRegenManager().findBlockKey(block, null); // Silent check for key
        
        // 2. Automatically start tracking if it's a managed block
        if (blockKey != null) {
            plugin.getRegenManager().startAdHocTracking(block, blockKey);
        }
    }
}
