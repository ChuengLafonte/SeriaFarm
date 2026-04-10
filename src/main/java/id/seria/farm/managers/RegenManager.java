package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.models.RegenBlock;
import id.seria.farm.utils.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;
import java.util.Arrays;
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
                if (regen.isGrowth()) {
                    handleGrowthTick(regen, now);
                } else if (now >= regen.getRestoreTime()) {
                    restoreBlock(regen);
                }
            }
        }, 10L, 10L); // Faster tick (0.5s) for smoother phased growth
    }

    private void handleGrowthTick(RegenBlock regen, long now) {
        if (regen.getGrowthMode().equalsIgnoreCase("INSTANT")) {
            if (now >= regen.getRestoreTime()) {
                restoreBlock(regen);
            }
            return;
        }

        // VANILLA Phased Growth
        if (regen.getCurrentStage() < regen.getMaxStage()) {
            if (now >= regen.getLastStepTime() + regen.getStepDuration()) {
                regen.setCurrentStage(regen.getCurrentStage() + 1);
                regen.setLastStepTime(now);
                updateBlockStage(regen);
            }
        } else {
            // Already at max stage? Just cleanup.
            activeRegens.remove(regen.getLocation());
        }
    }

    private void updateBlockStage(RegenBlock regen) {
        Block block = regen.getLocation().getBlock();
        Material original = regen.getOriginalMaterial();
        
        if (original.name().contains("AMETHYST")) {
            List<Material> stages = Arrays.asList(
                Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, 
                Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER
            );
            if (regen.getCurrentStage() < stages.size()) {
                block.setType(stages.get(regen.getCurrentStage()), true);
            }
        } else {
            // Ageable
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable) {
                ageable.setAge(Math.min(regen.getCurrentStage(), ageable.getMaximumAge()));
                block.setBlockData(ageable, true);
            }
        }
    }

    public boolean isRegenerating(Location loc) {
        return activeRegens.containsKey(loc);
    }

    public RegenBlock getRegenBlock(Location loc) {
        return activeRegens.get(loc);
    }

    public void scheduleRegeneration(Block block, int delaySeconds, List<String> replaceBlocks, List<String> delayBlocks, Material fallbackMat) {
        Location loc = block.getLocation();
        long now = System.currentTimeMillis();
        long restoreTime = now + (delaySeconds * 1000L);
        
        RegenBlock regen = new RegenBlock(loc, block.getType(), block.getBlockData().clone(), restoreTime, replaceBlocks, delayBlocks);
        regen.setStartTime(now);
        
        // CHECK FOR GROWTH CAPABILITY
        if (isGrowthCapable(block)) {
            setupGrowthBlock(regen, block, delaySeconds, now);
        } else {
            // Standard block (Bedrock/Delay logic)
            Material tempMat = pickMaterial(delayBlocks);
            if (tempMat == null) tempMat = fallbackMat;
            if (tempMat == null) tempMat = Material.BEDROCK;
            
            final Material finalTempMat = tempMat;
            Bukkit.getScheduler().runTask(plugin, () -> {
                block.setType(finalTempMat, false);
            });
        }
        
        activeRegens.put(loc, regen);
    }

    private boolean isGrowthCapable(Block block) {
        if (block.getBlockData() instanceof Ageable) return true;
        if (block.getType() == Material.AMETHYST_CLUSTER) return true;
        return false;
    }

    private void setupGrowthBlock(RegenBlock regen, Block block, int delaySeconds, long now) {
        regen.setGrowth(true);
        String mode = plugin.getConfigManager().getConfig("config.yml").getString("settings.crop-growth-mode", "INSTANT");
        regen.setGrowthMode(mode);
        
        int maxAge = 0;
        Material startMat = block.getType();
        
        if (block.getBlockData() instanceof Ageable ageable) {
            maxAge = ageable.getMaximumAge();
            regen.setMaxStage(maxAge);
            regen.setCurrentStage(0);
        } else if (block.getType() == Material.AMETHYST_CLUSTER) {
            maxAge = 3; // Small -> Medium -> Large -> Cluster
            regen.setMaxStage(maxAge);
            regen.setCurrentStage(0);
            startMat = Material.SMALL_AMETHYST_BUD;
        }

        if (mode.equalsIgnoreCase("VANILLA") && maxAge > 0) {
            regen.setStepDuration((delaySeconds * 1000L) / maxAge);
        }
        
        regen.setLastStepTime(now);
        
        final Material initialMat = startMat;
        final int initialAge = 0;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            block.setType(initialMat, true);
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable) {
                ageable.setAge(initialAge);
                block.setBlockData(ageable, true);
            }
        });
    }

    private void restoreBlock(RegenBlock regen) {
        Location loc = regen.getLocation();
        Block block = loc.getBlock();
        
        Material resultMat = pickMaterial(regen.getReplaceBlocks());
        if (resultMat != null && !regen.getReplaceBlocks().isEmpty()) {
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

    public List<String> getRegionNames() {
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
                if (LocationUtils.isInside(loc, l1, l2)) return regionName;
            }
        }
        return null;
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (RegenBlock regen : activeRegens.values()) {
            restoreBlock(regen);
        }
    }
}
