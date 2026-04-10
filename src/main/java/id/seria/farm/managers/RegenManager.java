package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.models.RegenBlock;
import id.seria.farm.utils.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import java.util.List;
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

    public void scheduleRegeneration(Block block, int delaySeconds, List<String> replaceBlocks) {
        Location loc = block.getLocation();
        long restoreTime = System.currentTimeMillis() + (delaySeconds * 1000L);
        
        RegenBlock regen = new RegenBlock(loc, block.getType(), block.getBlockData().clone(), restoreTime, replaceBlocks);
        activeRegens.put(loc, regen);
        
        // UBR Style: Replace with Bedrock or other temporary block
        block.setType(Material.BEDROCK, false);
    }

    private void restoreBlock(RegenBlock regen) {
        Location loc = regen.getLocation();
        Block block = loc.getBlock();
        
        Material resultMat = pickMaterial(regen.getReplaceBlocks());
        if (resultMat != null) {
            block.setType(resultMat, true);
        } else {
            block.setBlockData(regen.getOriginalData(), true);
        }
        activeRegens.remove(loc);
    }

    private Material pickMaterial(List<String> replaceBlocks) {
        if (replaceBlocks == null || replaceBlocks.isEmpty()) return null;
        
        double totalChance = 0;
        java.util.Map<Material, Double> weights = new java.util.HashMap<>();
        
        for (String line : replaceBlocks) {
            try {
                String[] parts = line.split(";");
                Material mat = Material.matchMaterial(parts[0].trim());
                double chance = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 100.0;
                
                if (mat != null) {
                    weights.put(mat, chance);
                    totalChance += chance;
                }
            } catch (Exception ignored) {}
        }
        
        if (weights.isEmpty()) return null;
        
        double random = Math.random() * totalChance;
        double cumulative = 0;
        for (java.util.Map.Entry<Material, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) return entry.getKey();
        }
        
        return null;
    }

    public java.util.List<String> getRegionNames() {
        org.bukkit.configuration.ConfigurationSection section = plugin.getConfigManager().getConfig("regions.yml").getConfigurationSection("regions");
        if (section == null) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(section.getKeys(false));
    }

    public String getRegionAt(Location loc) {
        org.bukkit.configuration.ConfigurationSection section = plugin.getConfigManager().getConfig("regions.yml").getConfigurationSection("regions");
        if (section == null) return null;

        for (String regionName : section.getKeys(false)) {
            String p1Str = section.getString(regionName + ".pos1");
            String p2Str = section.getString(regionName + ".pos2");
            if (p1Str == null || p2Str == null) continue;

            Location l1 = LocationUtils.deserialize(p1Str);
            Location l2 = LocationUtils.deserialize(p2Str);
            if (l1 != null && l2 != null) {
                if (LocationUtils.isInside(loc, l1, l2)) {
                    return regionName;
                }
            }
        }
        return null;
    }

    public void shutdown() {
        if (task != null) task.cancel();
        // Force restore all or save to DB
        for (RegenBlock regen : activeRegens.values()) {
            restoreBlock(regen);
        }
    }
}
