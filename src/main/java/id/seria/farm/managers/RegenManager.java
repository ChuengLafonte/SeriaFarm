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
        
        RegenBlock regen = new RegenBlock(loc, block.getType(), block.getBlockData().clone(), restoreTime, replaceBlocks, delayBlocks, materialKey);
        regen.setStartTime(now);
        
        // CHECK FOR GROWTH CAPABILITY (Re-enabled for global to allow right-click harvest replanting)
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
        
        activeRegens.put(toKey(loc), regen);
    }

    public void startAdHocTracking(Block block, String materialKey) {
        if (!isGrowthCapable(block)) return;
        
        Location loc = block.getLocation();
        if (isRegenerating(loc)) return; // Already tracked

        long now = System.currentTimeMillis();
        
        // Find config for delay
        org.bukkit.configuration.ConfigurationSection config = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks." + materialKey);
        int delaySeconds = config != null ? config.getInt("regen-delay", 60) : 60;

        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            int maxAge = ageable.getMaximumAge();
            int currentAge = ageable.getAge();
            
            // If already max, no need to track
            if (currentAge >= maxAge) return;

            // Calculate remaining time
            // Formula: (Total Delay) * (Remaining Stages / Total Stages)
            double progressRatio = (double) currentAge / maxAge;
            long remainingTimeMs = (long) (delaySeconds * 1000L * (1.0 - progressRatio));
            long restoreTime = now + remainingTimeMs;

            // Setup RegenBlock
            RegenBlock regen = new RegenBlock(loc, block.getType(), data.clone(), restoreTime, null, null, materialKey);
            
            setupGrowthBlock(regen, block, delaySeconds, now);
            
            // Adjust current stage and offset startTime for smooth catch-up
            regen.setCurrentStage(currentAge);
            long offset = (long) (currentAge * regen.getStepDuration());
            regen.setStartTime(now - offset);
            
            activeRegens.put(toKey(loc), regen);
            Bukkit.getLogger().info("[SeriaFarm Debug] Started ad-hoc tracking for " + materialKey + " at stage " + currentAge + "/" + maxAge);
            
            // Send message to ops for confirmation
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.isOp() || op.hasPermission("seriafarm.admin")) {
                    op.sendMessage(id.seria.farm.inventory.utils.StaticColors.getHexMsg("&8[&bDebug&8] &aBerhasil melacak Blok Global: &f" + materialKey + " &7(Stage " + currentAge + ")"));
                }
            }
        }
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
