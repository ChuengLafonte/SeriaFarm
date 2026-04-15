package id.seria.farm.tasks;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodic task that ticks water decay for all registered custom plants.
 * Runs every decay-interval seconds (minimum interval used across all crops).
 * The CustomPlantManager handles per-crop interval differences internally.
 */
public class WaterDecayTask extends BukkitRunnable {

    private final SeriaFarmPlugin plugin;

    public WaterDecayTask(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getCustomPlantManager().tickDecay();
    }

    /**
     * Starts the decay task. Uses the global minimum decay-interval from config (default 60s).
     * Runs on the main thread so block changes (rot) are safe.
     */
    public static void start(SeriaFarmPlugin plugin) {
        int intervalSeconds = plugin.getConfigManager().getConfig("config.yml")
                .getInt("settings.decay-tick-interval", 60);
        long intervalTicks = intervalSeconds * 20L;
        new WaterDecayTask(plugin).runTaskTimer(plugin, intervalTicks, intervalTicks);
    }
}
