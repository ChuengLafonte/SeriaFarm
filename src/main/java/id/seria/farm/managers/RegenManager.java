package id.seria.farm.managers;
 
import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.models.RegenBlock;
import id.seria.farm.inventory.utils.StaticColors;
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
import java.util.concurrent.ConcurrentHashMap;
 
public class RegenManager {
 
    private final SeriaFarmPlugin plugin;
    private final Map<String, RegenBlock> activeRegens = new ConcurrentHashMap<>();
    private BukkitTask task;
    
    public String toKey(Location loc) {
        if (loc == null) return "null";
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }
 
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
 
        // VANILLA Phased Growth (Catch-up Logic)
        if (regen.getCurrentStage() < regen.getMaxStage()) {
            long elapsed = now - regen.getStartTime();
            int expectedStage = (int) (elapsed / regen.getStepDuration());
            
            if (plugin.getConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().info("[GrowthDebug] " + regen.getMaterialKey() + " | Stage: " + regen.getCurrentStage() + " -> Expected: " + expectedStage + " | Elapsed: " + elapsed + "ms");
            }

            // Limit to max stage
            if (expectedStage > regen.getMaxStage()) expectedStage = regen.getMaxStage();

            if (expectedStage > regen.getCurrentStage()) {
                regen.setCurrentStage(expectedStage);
                regen.setLastStepTime(now);
                updateBlockStage(regen);
            }
        } else {
            // FINISHED: The final stage is already reached and set by updateBlockStage.
            // For phased growth crops, we strictly remove from map immediately 
            // to prevent 'restoreBlock' (designed for ores) from cycling it back to stage 0.
            activeRegens.remove(toKey(regen.getLocation()));
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
        } else if (original == Material.BAMBOO) {
            // Bamboo Growth Logic
            int stage = regen.getCurrentStage();
            if (stage == 0) {
                block.setType(Material.BAMBOO_SAPLING, true);
            } else {
                // Grow upwards
                for (int i = 0; i < stage; i++) {
                    org.bukkit.block.Block b = block.getRelative(0, i, 0);
                    if (plugin.getConfig().getBoolean("settings.debug", false)) {
                        plugin.getLogger().info("[GrowthDebug] Setting " + b.getType() + " at Y+" + i + " for " + regen.getMaterialKey());
                    }
                    if (i == 0) {
                        b.setType(Material.BAMBOO, true);
                    } else if (b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.BAMBOO) {
                        b.setType(Material.BAMBOO, true);
                        // Add leaf visuals
                        if (b.getBlockData() instanceof org.bukkit.block.data.type.Bamboo bamboo) {
                            if (i == stage - 1) bamboo.setLeaves(org.bukkit.block.data.type.Bamboo.Leaves.LARGE);
                            else if (i == stage - 2) bamboo.setLeaves(org.bukkit.block.data.type.Bamboo.Leaves.SMALL);
                            else bamboo.setLeaves(org.bukkit.block.data.type.Bamboo.Leaves.NONE);
                            b.setBlockData(bamboo, true);
                        }
                    }
                }
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

        plugin.getLogger().info("[RegenDebug] Scheduling " + originalMat + " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " (Key: " + materialKey + ")");

        RegenBlock regen = new RegenBlock(loc, originalMat, originalData, restoreTime, replaceBlocks, delayBlocks, materialKey);
        regen.setStartTime(now);
        
        // CHECK FOR GROWTH CAPABILITY
        boolean growth = isGrowthCapable(originalMat, originalData);
        plugin.getLogger().info("[RegenDebug] Growth Capable: " + growth);
        if (growth) {
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
        
        activeRegens.put(toKey(loc), regen);
    }

    public Block getBambooRoot(Block block) {
        if (block.getType() != Material.BAMBOO && block.getType() != Material.BAMBOO_SAPLING) return block;
        Block root = block;
        while (root.getRelative(0, -1, 0).getType() == Material.BAMBOO) {
            root = root.getRelative(0, -1, 0);
        }
        return root;
    }

    public int getBambooHeight(Block root) {
        if (root.getType() != Material.BAMBOO && root.getType() != Material.BAMBOO_SAPLING) return 0;
        int height = 0;
        Block stalk = root;
        while (stalk.getType() == Material.BAMBOO || stalk.getType() == Material.BAMBOO_SAPLING) {
            height++;
            stalk = stalk.getRelative(0, 1, 0);
        }
        return height;
    }

    public void startAdHocTracking(Block block, String materialKey) {
        if (!isGrowthCapable(block)) return;
        
        Location loc = block.getLocation();
        if (isRegenerating(loc)) return; // Already tracked

        long now = System.currentTimeMillis();
        
        // Find config for delay
        ConfigurationSection config = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks." + materialKey);
        int delaySeconds = config != null ? config.getInt("regen-delay", 45) : 45;

        BlockData data = block.getBlockData();
        int maxAge = 0;
        int currentAge = 0;
        
        if (data instanceof Ageable ageable) {
            maxAge = ageable.getMaximumAge();
            currentAge = ageable.getAge();
        } else if (block.getType() == Material.BAMBOO || block.getType() == Material.BAMBOO_SAPLING) {
            // Bamboo/Sapling logic for ad-hoc
            Block root = getBambooRoot(block);
            if (isRegenerating(root.getLocation())) return; // Root is already being tracked
            
            maxAge = config != null ? config.getInt("bamboo-max-height", 12) : 12;
            currentAge = getBambooHeight(root);
            
            // Redirect tracking to ROOT
            loc = root.getLocation();
            if (isRegenerating(loc)) return;
        }

        if (maxAge > 0 && currentAge < maxAge) {
            // Setup RegenBlock
            RegenBlock regen = new RegenBlock(loc, block.getType(), data.clone(), now + (delaySeconds * 1000L), null, null, materialKey);
            setupGrowthBlock(regen, loc.getBlock(), delaySeconds, now);
            
            // Adjust current stage and offset startTime for smooth catch-up
            regen.setCurrentStage(currentAge);
            long offset = (long) (currentAge * regen.getStepDuration());
            regen.setStartTime(now - offset);
            
            activeRegens.put(toKey(loc), regen);
            if (plugin.getConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().info("[GrowthDebug] Started ad-hoc tracking for " + materialKey + " at stage " + currentAge + "/" + maxAge);
            }
        }
    }
 
    private boolean isGrowthCapable(Material mat, BlockData data) {
        if (data instanceof Ageable) return true;
        if (mat == Material.AMETHYST_CLUSTER || mat == Material.BAMBOO || mat == Material.BAMBOO_SAPLING) return true;
        return false;
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
        
        if (block.getBlockData() instanceof Ageable ageable) {
            maxAge = ageable.getMaximumAge();
            regen.setMaxStage(maxAge);
            regen.setCurrentStage(0);
        } else if (block.getType() == Material.AMETHYST_CLUSTER) {
            maxAge = 3; // Small -> Medium -> Large -> Cluster
            regen.setMaxStage(maxAge);
            regen.setCurrentStage(0);
            startMat = Material.SMALL_AMETHYST_BUD;
        } else if (block.getType() == Material.BAMBOO) {
            org.bukkit.configuration.ConfigurationSection matConfig = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks." + regen.getMaterialKey());
            maxAge = matConfig != null ? matConfig.getInt("bamboo-max-height", 12) : 12;
            regen.setMaxStage(maxAge);
            regen.setCurrentStage(0);
            startMat = Material.BAMBOO_SAPLING;
            plugin.getLogger().info("[GrowthDebug] Bamboo Setup: maxAge=" + maxAge + " delay=" + delaySeconds);
        }
    
        if (mode.equalsIgnoreCase("VANILLA") && maxAge > 0) {
            regen.setStepDuration((delaySeconds * 1000L) / maxAge);
            plugin.getLogger().info("[GrowthDebug] Step Duration: " + regen.getStepDuration() + "ms");
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
            if (regen.getOriginalMaterial() == Material.BAMBOO) {
                // Multi-block restoration for Bamboo in INSTANT mode
                int maxHeight = plugin.getConfigManager().getConfig("materials.yml").getInt("blocks." + regen.getMaterialKey() + ".bamboo-max-height", 12);
                for (int i = 0; i < maxHeight; i++) {
                    org.bukkit.block.Block b = block.getRelative(0, i, 0);
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
        activeRegens.remove(toKey(loc));
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
 
    public String findBlockKey(Block block, Player player) {
        String matName = block.getType().name();
        String regionName = getRegionAt(block.getLocation());
        ConfigurationSection rootBlocks = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks");
        
        if (rootBlocks == null) {
            if (player != null && player.isOp()) player.sendMessage(StaticColors.getHexMsg("&8[&bDebug&8] &cRoot section 'blocks' MISSING from materials.yml!"));
            return null;
        }

        // 1. Check Region-Specific Overrides
        if (regionName != null) {
            ConfigurationSection regionSection = rootBlocks.getConfigurationSection(regionName);
            if (regionSection != null) {
                for (String key : regionSection.getKeys(false)) {
                    String configMat = regionSection.getString(key + ".material", "");
                    if (isMatch(matName, configMat, key)) {
                        return regionName + "." + key;
                    }
                }
            }
        }

        // 2. Check Global Settings
        ConfigurationSection globalSection = rootBlocks.getConfigurationSection("global");
        if (globalSection != null) {
            for (String key : globalSection.getKeys(false)) {
                String configMat = globalSection.getString(key + ".material", "");
                if (isMatch(matName, configMat, key)) {
                    return "global." + key;
                }
            }
        }
        
        // ADMIN VERBOSE DEBUG (Snapshot of internal state)
        if (player != null && block.getType() != Material.AIR && (player.isOp() || player.hasPermission("seriafarm.admin"))) {
            player.sendMessage(StaticColors.getHexMsg("&8[&bDebug&8] &7Material: &f" + matName + " &7| ConfigFound: &cTIDAK"));
            if (globalSection != null) {
                String foundKeys = String.join(", ", globalSection.getKeys(false));
                player.sendMessage(StaticColors.getHexMsg("&8[&bDebug&8] &7Available Global Keys: &e[" + foundKeys + "]"));
            } else {
                player.sendMessage(StaticColors.getHexMsg("&8[&bDebug&8] &cGlobal section 'blocks.global' NOT found in config!"));
            }
        }
        return null;
    }

    public boolean isMatch(String matName, String configMat, String key) {
        // EXACT MATCH (Case-insensitive)
        if (configMat.equalsIgnoreCase(matName) || key.equalsIgnoreCase(matName)) return true;
        
        // AGGRESSIVE FUZZY (Handle plural/singular and keyword segments)
        String m = matName.toLowerCase().replace("_", "");
        String k = key.toLowerCase().replace("_", "");
        String c = configMat.toLowerCase().replace("_", "");

        // Remove plural suffixes (s and es)
        m = stripPlural(m);
        k = stripPlural(k);
        c = stripPlural(c);

        if (m.isEmpty()) return false;

        // Keyword containment (e.g., "beetroot" matches "beetrootseeds")
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
    }
}
