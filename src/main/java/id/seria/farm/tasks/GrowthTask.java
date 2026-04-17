package id.seria.farm.tasks;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodic task that updates the visual growth stage (age) of all custom plants.
 * This runs more frequently than water decay to ensure smooth visual progress.
 */
public class GrowthTask extends BukkitRunnable {

    private final SeriaFarmPlugin plugin;

    public GrowthTask(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getCustomPlantManager().tickGrowth();
    }

    /**
     * Starts the growth task. Uses settings.growth-tick-interval from config (default 2s).
     */
    public static void start(SeriaFarmPlugin plugin) {
        int intervalSeconds = plugin.getConfigManager().getConfig("config.yml")
                .getInt("settings.growth-tick-interval", 2);
        long intervalTicks = intervalSeconds * 20L;
        new GrowthTask(plugin).runTaskTimer(plugin, intervalTicks, intervalTicks);
    }
}
