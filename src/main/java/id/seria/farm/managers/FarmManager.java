package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.models.Plant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FarmManager {

    private final SeriaFarmPlugin plugin;
    private final Map<Location, Plant> growingPlants = new ConcurrentHashMap<>();

    public FarmManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        startGrowthTask();
    }

    private void startGrowthTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Plant plant : growingPlants.values()) {
                updateGrowth(plant);
            }
        }, 100L, 100L); // Tick every 5 seconds
    }

    private void updateGrowth(Plant plant) {
        Block block = plant.getLocation().getBlock();
        if (block.getBlockData() instanceof Ageable ageable) {
            int currentAge = ageable.getAge();
            if (currentAge < ageable.getMaximumAge()) {
                // Check watering/fertilizer requirements here
                ageable.setAge(currentAge + 1);
                block.setBlockData(ageable);
            }
        }
    }

    public void registerPlant(Location location, String type, java.util.UUID owner) {
        growingPlants.put(location, new Plant(location, type, owner, System.currentTimeMillis()));
    }

    public Plant getPlant(Location location) {
        return growingPlants.get(location);
    }
}
