package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.models.RegenBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegenManager {

    private final SeriaFarmPlugin plugin;
    private final Map<Location, RegenBlock> activeRegens = new ConcurrentHashMap<>();
    private BukkitTask task;

    public RegenManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        startTicking();
    }

    private void startTicking() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (RegenBlock regen : activeRegens.values()) {
                if (now >= regen.getRestoreTime()) {
                    restoreBlock(regen);
                }
            }
        }, 20L, 20L);
    }

    public void scheduleRegeneration(Block block, int delaySeconds) {
        Location loc = block.getLocation();
        long restoreTime = System.currentTimeMillis() + (delaySeconds * 1000L);
        
        RegenBlock regen = new RegenBlock(loc, block.getType(), block.getBlockData().clone(), restoreTime);
        activeRegens.put(loc, regen);
        
        // UBR Style: Replace with Bedrock or other temporary block
        block.setType(Material.BEDROCK, false);
    }

    private void restoreBlock(RegenBlock regen) {
        Location loc = regen.getLocation();
        Block block = loc.getBlock();
        
        block.setBlockData(regen.getOriginalData(), true);
        activeRegens.remove(loc);
        
        // Optional logic for sounds/particles on restoration
    }

    public java.util.List<String> getRegionNames() {
        // This will eventually read from regions.yml or database
        return java.util.List.of("Global", "TestRegion");
    }

    public void shutdown() {
        if (task != null) task.cancel();
        // Force restore all or save to DB
        for (RegenBlock regen : activeRegens.values()) {
            restoreBlock(regen);
        }
    }
}
