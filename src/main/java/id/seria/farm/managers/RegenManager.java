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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class RegenManager {

    private final SeriaFarmPlugin plugin;
    private final Map<String, RegenBlock> activeRegens = new ConcurrentHashMap<>();
    private final Map<String, CachedRegion> regionCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> materialLookup = new ConcurrentHashMap<>();
    private final Map<String, Integer> maxHeightCache = new ConcurrentHashMap<>();
    private BukkitTask task;

    private static record CachedRegion(Location pos1, Location pos2, String worldName) {}
    
    public String toKey(Location loc) {
        if (loc == null) return "null";
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public RegenManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        refreshCaches();
        startTicking();
    }

    private void startTicking() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRegens.isEmpty()) return;
            
            long now = System.currentTimeMillis();
            activeRegens.values().removeIf(regen -> {
                if (regen.isGrowth()) {
                    return handleGrowthTick(regen, now);
                } else if (now >= regen.getRestoreTime()) {
                    restoreBlock(regen);
                    return true;
                }
                return false;
            });
        }, 10L, 10L);
    }

    private boolean handleGrowthTick(RegenBlock regen, long now) {
        if (regen.getGrowthMode().equalsIgnoreCase("INSTANT")) {
            if (now >= regen.getRestoreTime()) {
                restoreBlock(regen);
                return true;
            }
            return false;
        }

        // VANILLA Phased Growth
        if (regen.getCurrentStage() < regen.getMaxStage()) {
            long elapsed = now - regen.getStartTime();
            int expectedStage = (int) (elapsed / regen.getStepDuration());
            
            if (expectedStage > regen.getMaxStage()) expectedStage = regen.getMaxStage();

            if (expectedStage > regen.getCurrentStage()) {
                regen.setCurrentStage(expectedStage);
                regen.setLastStepTime(now);
                updateBlockStage(regen);
                playGrowthEffects(regen);
            }
            return false;
        } else {
            return true;
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
        } else if (original == Material.BAMBOO || original == Material.SUGAR_CANE || original == Material.CACTUS) {
            handleVerticalGrowthTick(regen);
        } else {
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable) {
                int targetAge = Math.min(regen.getCurrentStage(), ageable.getMaximumAge());
                if (ageable.getAge() != targetAge) {
                    ageable.setAge(targetAge);
                    block.setBlockData(ageable, true);
                }
            }
        }
    }

    private void playGrowthEffects(RegenBlock regen) {
        // Sound suppressed intentionally — no audio cue per growth stage.
    }

    private void handleVerticalGrowthTick(RegenBlock regen) {
        Block root = regen.getLocation().getBlock();
        Material original = regen.getOriginalMaterial();
        int targetStage = regen.getCurrentStage();
        
        if (targetStage == 0) {
            Material seedling = getSeedlingMaterial(regen);
            if (root.getType() != seedling) {
                root.setType(seedling, true);
            }
            return;
        }

        for (int i = 0; i < targetStage; i++) {
            Block stalk = root.getRelative(0, i, 0);
            if (stalk.getType() != original) {
                Material stalkType = stalk.getType();
                if (stalkType == Material.AIR || stalkType == Material.CAVE_AIR || stalkType == getSeedlingMaterial(regen)) {
                    stalk.setType(original, i == 0);
                } else {
                    break;
                }
            }

            if (original == Material.BAMBOO && stalk.getBlockData() instanceof org.bukkit.block.data.type.Bamboo bamboo) {
                org.bukkit.block.data.type.Bamboo.Leaves leafType = org.bukkit.block.data.type.Bamboo.Leaves.NONE;
                if (i == targetStage - 1) leafType = org.bukkit.block.data.type.Bamboo.Leaves.LARGE;
                else if (i == targetStage - 2) leafType = org.bukkit.block.data.type.Bamboo.Leaves.SMALL;
                
                if (bamboo.getLeaves() != leafType) {
                    bamboo.setLeaves(leafType);
                    stalk.setBlockData(bamboo, true);
                }
            }
        }
    }

    private Material getSeedlingMaterial(RegenBlock regen) {
        ConfigurationSection config = plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops." + regen.getMaterialKey());
        if (config != null && config.contains("seedling-material")) {
            Material mat = Material.matchMaterial(config.getString("seedling-material"));
            if (mat != null) return mat;
        }
        
        Material original = regen.getOriginalMaterial();
        if (original == Material.BAMBOO) return Material.BAMBOO_SAPLING;
        return Material.WHEAT; 
    }

    private Material getSeedlingMaterial(Material original) {
        if (original == Material.BAMBOO) return Material.BAMBOO_SAPLING;
        return Material.WHEAT; 
    }

    public boolean isRegenerating(Location loc) {
        return activeRegens.containsKey(toKey(loc));
    }
 
    public RegenBlock getRegenBlock(Location loc) {
        return activeRegens.get(toKey(loc));
    }

    public void cancelRegeneration(Location loc) {
        activeRegens.remove(toKey(loc));
    }
 
    public void scheduleRegeneration(Block block, int delaySeconds, List<String> replaceBlocks, List<String> delayBlocks, Material fallbackMat, String materialKey) {
        Location loc = block.getLocation();
        long now = System.currentTimeMillis();
        long restoreTime = now + (delaySeconds * 1000L);
        
        Material originalMat = (fallbackMat != null && (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR)) ? fallbackMat : block.getType();
        BlockData originalData = (originalMat == block.getType()) ? block.getBlockData().clone() : Bukkit.createBlockData(originalMat);

        RegenBlock regen = new RegenBlock(loc, originalMat, originalData, restoreTime, replaceBlocks, delayBlocks, materialKey);
        regen.setStartTime(now);
        
        if (isGrowthCapable(originalMat, originalData)) {
            setupGrowthBlock(regen, block, delaySeconds, now);
        } else {
            Material tempMat = pickMaterial(delayBlocks);
            if (tempMat == null) tempMat = fallbackMat;
            if (tempMat == null) tempMat = Material.BEDROCK;
            
            final Material finalTempMat = tempMat;
            Bukkit.getScheduler().runTask(plugin, () -> block.setType(finalTempMat, false));
        }
        
        activeRegens.put(toKey(loc), regen);
    }

    public Block getVerticalRoot(Block block) {
        Material type = block.getType();
        if (!isVerticalCrop(type)) return block;
        
        Block root = block;
        for (int i = 0; i < 15; i++) { // Limit height search for performance
            Block below = root.getRelative(0, -1, 0);
            if (isVerticalCrop(below.getType())) {
                root = below;
            } else {
                break;
            }
        }
        return root;
    }

    private boolean isVerticalCrop(Material mat) {
        return mat == Material.BAMBOO || mat == Material.BAMBOO_SAPLING || 
               mat == Material.SUGAR_CANE || mat == Material.WHEAT || 
               mat == Material.CACTUS;
    }

    private Material getBaseMaterial(Material current) {
        if (current == Material.BAMBOO || current == Material.BAMBOO_SAPLING) return Material.BAMBOO;
        if (current == Material.SUGAR_CANE || current == Material.WHEAT) return Material.SUGAR_CANE;
        if (current == Material.CACTUS) return Material.CACTUS;
        return current;
    }

    public int getVerticalHeight(Block root, Material stalkType) {
        int height = 0;
        Block stalk = root;
        Material seedling = getSeedlingMaterial(stalkType == Material.BAMBOO ? Material.BAMBOO : (stalkType == Material.SUGAR_CANE ? Material.SUGAR_CANE : Material.CACTUS));
        while (stalk.getType() == stalkType || stalk.getType() == seedling) {
            height++;
            stalk = stalk.getRelative(0, 1, 0);
            if (height > 15) break; 
        }
        return height;
    }

    public void startAdHocTracking(Block block, String materialKey) {
        if (!isGrowthCapable(block)) return;
        
        Location loc = block.getLocation();
        if (isRegenerating(loc)) return;

        long now = System.currentTimeMillis();
        ConfigurationSection config = plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops." + materialKey);
        int delaySeconds = config != null ? config.getInt("regen-delay", 45) : 45;

        BlockData data = block.getBlockData();
        int maxAge = 0;
        int currentAge = 0;
        
        if (data instanceof Ageable ageable) {
            maxAge = ageable.getMaximumAge();
            currentAge = ageable.getAge();
        } else if (block.getType() == Material.BAMBOO || block.getType() == Material.SUGAR_CANE || block.getType() == Material.CACTUS) {
            Block root = getVerticalRoot(block);
            if (isRegenerating(root.getLocation())) return;
            
            maxAge = getMaxHeight(materialKey, block.getType());
            currentAge = getVerticalHeight(root, getBaseMaterial(block.getType()));
            loc = root.getLocation();
            if (isRegenerating(loc)) return;
        }

        if (maxAge > 0 && currentAge < maxAge) {
            RegenBlock regen = new RegenBlock(loc, block.getType(), data.clone(), now + (delaySeconds * 1000L), null, null, materialKey);
            setupGrowthBlock(regen, loc.getBlock(), delaySeconds, now);
            regen.setCurrentStage(currentAge);
            long offset = (long) (currentAge * regen.getStepDuration());
            regen.setStartTime(now - offset);
            activeRegens.put(toKey(loc), regen);
        }
    }

    private int getMaxHeight(String materialKey, Material type) {
        return maxHeightCache.computeIfAbsent(materialKey, k -> {
            ConfigurationSection matConfig = plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops." + k);
            int max = matConfig != null ? matConfig.getInt("growth-max-height", 3) : 3;
            if (type == Material.BAMBOO && (matConfig == null || !matConfig.contains("growth-max-height"))) {
                max = matConfig != null ? matConfig.getInt("bamboo-max-height", 12) : 12;
            }
            return max;
        });
    }
 
    private boolean isGrowthCapable(Material mat, BlockData data) {
        if (data instanceof Ageable) return true;
        return mat == Material.AMETHYST_CLUSTER || mat == Material.BAMBOO || mat == Material.BAMBOO_SAPLING || mat == Material.SUGAR_CANE || mat == Material.CACTUS;
    }

    private boolean isGrowthCapable(Block block) {
        return isGrowthCapable(block.getType(), block.getBlockData());
    }
 
    private void setupGrowthBlock(RegenBlock regen, Block block, int delaySeconds, long now) {
        regen.setGrowth(true);
        String mode = plugin.getConfigManager().getConfig("config.yml").getString("settings.crop-growth-mode", "INSTANT");
        regen.setGrowthMode(mode);
        
        int maxAge = 0;
        Material startMat = block.getType();
        Material original = regen.getOriginalMaterial();
        
        if (block.getBlockData() instanceof Ageable ageable) {
            maxAge = ageable.getMaximumAge();
            startMat = block.getType();
        } else if (original == Material.AMETHYST_CLUSTER) {
            maxAge = 3;
            startMat = Material.SMALL_AMETHYST_BUD;
        } else if (original == Material.BAMBOO || original == Material.SUGAR_CANE || original == Material.CACTUS) {
            maxAge = getMaxHeight(regen.getMaterialKey(), original);
            startMat = getSeedlingMaterial(regen);
        }
    
        regen.setMaxStage(maxAge);
        regen.setCurrentStage(0);

        if (mode.equalsIgnoreCase("VANILLA") && maxAge > 0) {
            regen.setStepDuration((delaySeconds * 1000L) / maxAge);
        }
        
        regen.setLastStepTime(now);
        final Material initialMat = startMat;
        Bukkit.getScheduler().runTask(plugin, () -> {
            block.setType(initialMat, true);
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable) {
                ageable.setAge(0);
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
            if (regen.getOriginalMaterial() == Material.BAMBOO) {
                int maxHeight = getMaxHeight(regen.getMaterialKey(), Material.BAMBOO);
                for (int i = 0; i < maxHeight; i++) {
                    Block b = block.getRelative(0, i, 0);
                    if (i == 0 || b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR) {
                        b.setType(Material.BAMBOO, true);
                        if (b.getBlockData() instanceof org.bukkit.block.data.type.Bamboo bamboo) {
                            if (i == maxHeight - 1) bamboo.setLeaves(org.bukkit.block.data.type.Bamboo.Leaves.LARGE);
                            else if (i == maxHeight - 2) bamboo.setLeaves(org.bukkit.block.data.type.Bamboo.Leaves.SMALL);
                            b.setBlockData(bamboo, true);
                        }
                    }
                }
            } else {
                block.setBlockData(regen.getOriginalData(), true);
            }
        }
    }
 
    private Material pickMaterial(List<String> replaceBlocks) {
        if (replaceBlocks == null || replaceBlocks.isEmpty()) return null;
        double totalChance = 0;
        Map<Material, Double> weights = new HashMap<>();
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
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) return entry.getKey();
        }
        return null;
    }

    public List<String> getRegionNames() {
        ConfigurationSection section = plugin.getConfigManager().getConfig("regions.yml").getConfigurationSection("regions");
        if (section == null) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(section.getKeys(false));
    }
  
    public String getRegionAt(Location loc) {
        if (regionCache.isEmpty()) return null;
        String worldName = loc.getWorld().getName();
        
        for (Map.Entry<String, CachedRegion> entry : regionCache.entrySet()) {
            CachedRegion region = entry.getValue();
            if (!region.worldName().equals(worldName)) continue;
            
            if (LocationUtils.isInside(loc, region.pos1(), region.pos2())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void refreshCaches() {
        refreshRegionCache();
        refreshMaterialCache();
        maxHeightCache.clear();
    }

    public void refreshRegionCache() {
        regionCache.clear();
        ConfigurationSection section = plugin.getConfigManager().getConfig("regions.yml").getConfigurationSection("regions");
        if (section == null) return;

        for (String regionName : section.getKeys(false)) {
            String p1Str = section.getString(regionName + ".pos1");
            String p2Str = section.getString(regionName + ".pos2");
            if (p1Str == null || p2Str == null) continue;

            Location l1 = LocationUtils.deserialize(p1Str);
            Location l2 = LocationUtils.deserialize(p2Str);
            if (l1 != null && l2 != null) {
                regionCache.put(regionName, new CachedRegion(l1, l2, l1.getWorld().getName()));
            }
        }
    }

    public void refreshMaterialCache() {
        materialLookup.clear();
        ConfigurationSection blocks = plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops");
        if (blocks == null) return;

        for (String sectionName : blocks.getKeys(false)) {
            ConfigurationSection section = blocks.getConfigurationSection(sectionName);
            if (section == null) continue;

            Map<String, String> normalizedMap = new HashMap<>();
            for (String key : section.getKeys(false)) {
                String matStr = section.getString(key + ".material", key);
                normalizedMap.put(normalize(matStr), key);
                normalizedMap.put(normalize(key), key);
            }
            materialLookup.put(sectionName, normalizedMap);
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        return stripPlural(s.toLowerCase().replace("_", ""));
    }

    public String findBlockKey(Block block, Player player) {
        String matName = block.getType().name();
        String normalizedMat = normalize(matName);
        String regionName = getRegionAt(block.getLocation());
        
        // 1. Check Region-Specific
        if (regionName != null) {
            Map<String, String> regionMap = materialLookup.get(regionName);
            if (regionMap != null) {
                String key = regionMap.get(normalizedMat);
                if (key != null) return regionName + "." + key;
            }
            // STRICT ISOLATION: If we are in a region, we DO NOT fall back to global.
            return null;
        }

        // 2. Check Global
        Map<String, String> globalMap = materialLookup.get("global");
        if (globalMap != null) {
            String key = globalMap.get(normalizedMat);
            if (key != null) return "global." + key;
        }
        
        return null;
    }

    public boolean isMatch(String matName, String configMat, String key) {
        if (configMat.equalsIgnoreCase(matName) || key.equalsIgnoreCase(matName)) return true;
        
        String m = normalize(matName);
        String k = normalize(key);
        String c = normalize(configMat);

        if (m.isEmpty()) return false;
        return k.contains(m) || m.contains(k) || c.contains(m) || m.contains(c);
    }

    public String stripPlural(String s) {
        if (s.endsWith("es")) return s.substring(0, s.length() - 2);
        if (s.endsWith("s")) return s.substring(0, s.length() - 1);
        return s;
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (RegenBlock regen : activeRegens.values()) {
            restoreBlock(regen);
        }
        activeRegens.clear();
    }
}
