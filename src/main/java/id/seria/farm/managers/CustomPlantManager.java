package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.models.CustomPlantState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all custom (garden) plant state — planting, watering, growth, decay, and rot.
 *
 * Garden crops live under <code>crops.garden</code> in crops.yml and are NOT
 * processed by the vanilla regen system.  Growth is driven by the watering engine:
 * each decay-interval tick, if water level > 0 the crop advances one stage.
 */
public class CustomPlantManager {

    private final SeriaFarmPlugin plugin;
    /** key = "world;x;y;z" → state  */
    private final Map<String, CustomPlantState> cache = new ConcurrentHashMap<>();
    /**
     * Cache: soil identifier → its resolved base {@link Material}.
     * keyed by the raw string from crops.yml (e.g. "mi:MISCELLANEOUS:COMPOSTED_SOIL").
     * {@link Material#AIR} is used as a sentinel for unresolvable identifiers.
     */
    private final Map<String, Material> soilMaterialCache = new ConcurrentHashMap<>();

    public CustomPlantManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Key Helpers ──────────────────────────────────────────────────────

    private String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    // ─── Startup Load ─────────────────────────────────────────────────────

    public void loadFromDatabase() {
        String sql = "SELECT * FROM custom_plants";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x"), y = rs.getInt("y"), z = rs.getInt("z");
                UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                String cropKey = rs.getString("crop_key");
                int waterLevel = rs.getInt("watering_level");
                long plantedAt = rs.getLong("planted_at");
                long noWaterSince = rs.getLong("no_water_since");
                boolean rotten = rs.getInt("is_rotten") == 1;

                org.bukkit.World w = org.bukkit.Bukkit.getWorld(world);
                if (w == null) continue;
                Location loc = new Location(w, x, y, z);
                cache.put(key(world, x, y, z),
                        new CustomPlantState(loc, owner, cropKey, waterLevel, plantedAt, noWaterSince, rotten));
            }
            plugin.getLogger().info("Loaded " + cache.size() + " custom plant states from DB.");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load custom plants: " + e.getMessage());
        }
    }

    /** Clears the soil material cache (call on config reload). */
    public void clearSoilCache() {
        soilMaterialCache.clear();
    }

    // ─── API ──────────────────────────────────────────────────────────────

    public boolean isCustomPlant(Location loc) {
        return cache.containsKey(key(loc));
    }

    public CustomPlantState getState(Location loc) {
        return cache.get(key(loc));
    }

    /** Register a new custom plant after a player places a seed. */
    public void plant(Location loc, Player player, String cropKey) {
        long now = System.currentTimeMillis();
        CustomPlantState state = new CustomPlantState(loc, player.getUniqueId(), cropKey, 0, now, 0, false);
        cache.put(key(loc), state);
        saveAsync(state);
    }

    /** Unregisters a custom plant when broken. */
    public void removePlant(Location loc) {
        cache.remove(key(loc));
        deleteAsync(loc);
    }

    /**
     * Add water level to a plant.  Clamps to max-level from config.
     * Returns the actual amount added.
     */
    public int water(Location loc, int amount) {
        CustomPlantState state = cache.get(key(loc));
        if (state == null) return 0;

        ConfigurationSection waterCfg = getWaterConfig(state.getCropKey());
        int maxLevel = waterCfg != null ? waterCfg.getInt("max-level", 10) : 10;
        int before = state.getWateringLevel();
        int after = Math.min(before + amount, maxLevel);
        state.setWateringLevel(after);

        if (after > 0) {
            long noWaterSince = state.getNoWaterSince();
            if (noWaterSince > 0) {
                long dryMs = System.currentTimeMillis() - noWaterSince;
                state.setPlantedAt(state.getPlantedAt() + dryMs);
                state.setNoWaterSince(0); // reset rot timer
            }
        }
        updateAsync(state);
        return after - before;
    }

    public void tickDecay() {
        long now = System.currentTimeMillis();
        for (CustomPlantState state : cache.values()) {
            if (state.isRotten()) continue;

            // PERFORMANCE: Only process if chunk is loaded
            Location loc = state.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue; 
            }

            ConfigurationSection waterCfg = getWaterConfig(state.getCropKey());
            if (waterCfg == null || !waterCfg.getBoolean("enabled", false)) continue;

            int decayRate         = waterCfg.getInt("decay-rate", 1);
            long rotThresholdMs   = waterCfg.getLong("rot-threshold", 600) * 1000L;

            // ── Water decay ───────────────────────────────────────────────
            int newLevel = Math.max(0, state.getWateringLevel() - decayRate);
            state.setWateringLevel(newLevel);

            if (newLevel == 0) {
                if (state.getNoWaterSince() == 0) {
                    state.setNoWaterSince(now);
                } else if (now - state.getNoWaterSince() >= rotThresholdMs) {
                    rotPlant(state);
                    continue; // nothing more to do for this plant
                }
            }

            updateAsync(state);
        }
    }

    /**
     * Called by GrowthTask every growth-tick-interval seconds.
     * Updates the physical block age based on time elapsed since planting.
     */
    public void tickGrowth() {
        long now = System.currentTimeMillis();
        List<Runnable> syncTasks = new java.util.ArrayList<>();

        for (CustomPlantState state : cache.values()) {
            if (state.isRotten() || state.getWateringLevel() <= 0) continue;

            // PERFORMANCE: Only process if chunk is loaded
            Location loc = state.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue; 
            }

            int regenDelaySecs = getRegenDelaySecs(state.getCropKey());
            long wateredMs = now - state.getPlantedAt();
            
            // MATH (Calculated in caller thread - async if tickGrowth is called async)
            // Note: Currently GrowthTask calls this, so it's sync, but the logic is prepared.
            double progress = (double) wateredMs / (regenDelaySecs * 1000L);
            
            syncTasks.add(() -> {
                Block b = loc.getBlock();
                if (b.getBlockData() instanceof Ageable ag && ag.getAge() < ag.getMaximumAge()) {
                    int exactAge = (int) Math.floor(progress * ag.getMaximumAge());
                    exactAge = Math.min(ag.getMaximumAge(), Math.max(ag.getAge(), exactAge));
                    if (exactAge != ag.getAge()) {
                        ag.setAge(exactAge);
                        b.setBlockData(ag, true);
                    }
                }
            });
        }

        // Batch execution on Main Thread
        if (!syncTasks.isEmpty()) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                for (Runnable r : syncTasks) r.run();
            });
        }
    }

    /**
     * Checks if a plant is logically fully grown based on time elapsed.
     */
    public boolean isLogicallyFullyGrown(CustomPlantState state) {
        if (state == null) return false;
        if (state.isRotten()) return false;
        
        long now = System.currentTimeMillis();
        long regenDelayMs = getRegenDelaySecs(state.getCropKey()) * 1000L;
        return (now - state.getPlantedAt()) >= regenDelayMs;
    }

    private void rotPlant(CustomPlantState state) {
        state.setRotten(true);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            Location loc = state.getLocation();
            if (loc.getWorld() == null) return;
            plugin.getRegenManager().cancelRegeneration(loc);
            loc.getBlock().setType(Material.DEAD_BUSH, false);
        });
    }

    /** Remove a custom plant entirely (on break or rot-clear). */
    public void remove(Location loc) {
        cache.remove(key(loc));
        deleteAsync(loc);
    }

    // ─── Garden Config Helpers ────────────────────────────────────────────

    /**
     * Returns the config base path for a crop key.
     * Garden crops live under <code>crops.garden</code>;
     * falls back to <code>crops.global</code> for legacy entries.
     */
    private String basePath(String cropKey) {
        var cfg = plugin.getConfigManager().getConfig("crops.yml");
        return cfg.contains("crops.garden." + cropKey)
                ? "crops.garden." + cropKey
                : "crops.global." + cropKey;
    }

    private ConfigurationSection getWaterConfig(String cropKey) {
        var cfg = plugin.getConfigManager().getConfig("crops.yml");
        ConfigurationSection cs = cfg.getConfigurationSection(basePath(cropKey));
        return cs != null ? cs.getConfigurationSection("watering") : null;
    }

    private int getRegenDelaySecs(String cropKey) {
        return plugin.getConfigManager().getConfig("crops.yml")
                .getInt(basePath(cropKey) + ".regen-delay", 300);
    }

    /**
     * Returns the list of valid soil identifiers for a crop.
     * Only garden crops have a soil list; vanilla crops return empty.
     */
    public List<String> getValidSoils(String cropKey) {
        var cfg = plugin.getConfigManager().getConfig("crops.yml");
        return cfg.getStringList(basePath(cropKey) + ".soil");
    }

    /**
     * Resolve a soil identifier string to its base {@link Material}.
     * Results are cached so HookManager.getItem() is only called once per identifier.
     * Returns {@link Material#AIR} as a sentinel for unresolvable identifiers.
     */
    private Material resolveSoilMaterial(String soilId) {
        return soilMaterialCache.computeIfAbsent(soilId, k -> {
            // Try direct vanilla name first (fastest path)
            Material m = Material.matchMaterial(k.toUpperCase());
            if (m != null && m.isItem()) return m;

            // Custom identifier: resolve via HookManager → read base material
            try {
                ItemStack it = plugin.getHookManager().getItem(k);
                if (it != null && it.getType() != Material.STONE && it.getType() != Material.AIR) {
                    return it.getType();
                }
            } catch (Exception ignored) {}

            return Material.AIR; // unresolvable sentinel
        });
    }

    /**
     * Check whether a world block qualifies as valid soil for ANY garden crop.
     *
     * Strategy (no ItemStack created from block type — avoids crash on
     * block-only materials like POTATOES / CARROTS / BEETROOTS):
     *  1. Direct material name match (e.g. "FARMLAND").
     *  2. Resolve custom identifier → base material, compare with block type
     *     (e.g. "mi:MISCELLANEOUS:COMPOSTED_SOIL" → FARMLAND → matches FARMLAND block).
     */
    public boolean isValidSoil(Block block) {
        Material blockType  = block.getType();
        String blockTypeName = blockType.name();

        ConfigurationSection garden = plugin.getConfigManager().getConfig("crops.yml")
                .getConfigurationSection("crops.garden");
        if (garden == null) return false;

        for (String cropKey : garden.getKeys(false)) {
            for (String soilKey : garden.getStringList(cropKey + ".soil")) {
                // 1. Resolve key to actual item-id from soils.yml
                String actualId = getSoilItemId(soilKey);
                if (actualId == null) {
                    // Fallback to strict material name match for vanity (like "FARMLAND")
                    if (soilKey.equalsIgnoreCase(blockTypeName)) return true;
                    continue;
                }

                // 2. Resolve actualId to Material
                Material resolved = resolveSoilMaterial(actualId);
                if (resolved != Material.AIR && resolved == blockType) return true;
            }
        }
        return false;
    }

    public boolean isSoilItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String id = plugin.getHookManager().getItemIdentifier(item);
        String matName = item.getType().name();

        ConfigurationSection soils = plugin.getConfigManager().getConfig("soils.yml")
                .getConfigurationSection("soils");
        if (soils == null) return false;

        for (String key : soils.getKeys(false)) {
            String itemId = soils.getString(key + ".item-id");
            if (itemId == null) continue;
            if (itemId.equalsIgnoreCase(id) || itemId.equalsIgnoreCase(matName)) return true;
        }
        return false;
    }

    public String getSoilKeyFromItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        String id = plugin.getHookManager().getItemIdentifier(item);
        String matName = item.getType().name();

        ConfigurationSection soils = plugin.getConfigManager().getConfig("soils.yml")
                .getConfigurationSection("soils");
        if (soils == null) return null;

        for (String key : soils.getKeys(false)) {
            String itemId = soils.getString(key + ".item-id");
            if (itemId == null) continue;
            if (itemId.equalsIgnoreCase(id) || itemId.equalsIgnoreCase(matName)) return key;
        }
        return null;
    }

    public String getSoilItemId(String key) {
        return plugin.getConfigManager().getConfig("soils.yml").getString("soils." + key + ".item-id");
    }

    /** Resolve a seed key from seeds.yml to its technical item identifier. */
    public String getSeedItemId(String key) {
        return plugin.getConfigManager().getConfig("seeds.yml").getString("seeds." + key + ".item-id");
    }

    /** Find the seed key for a given item stack by checking seeds.yml. */
    public String getSeedKeyFromItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        String id = plugin.getHookManager().getItemIdentifier(item);
        String matName = item.getType().name();

        ConfigurationSection seeds = plugin.getConfigManager().getConfig("seeds.yml")
                .getConfigurationSection("seeds");
        if (seeds == null) return null;

        for (String key : seeds.getKeys(false)) {
            String itemId = seeds.getString(key + ".item-id");
            if (itemId == null) continue;
            if (itemId.equalsIgnoreCase(id) || itemId.equalsIgnoreCase(matName)) return key;
        }
        return null;
    }

    /** Returns all registered soil keys from soils.yml. */
    public List<String> getAllSoilKeys() {
        ConfigurationSection soils = plugin.getConfigManager().getConfig("soils.yml")
                .getConfigurationSection("soils");
        if (soils == null) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(soils.getKeys(false));
    }

    /** Returns all registered seed keys from seeds.yml. */
    public List<String> getAllSeedKeys() {
        ConfigurationSection seeds = plugin.getConfigManager().getConfig("seeds.yml")
                .getConfigurationSection("seeds");
        if (seeds == null) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(seeds.getKeys(false));
    }

    /**
     * Find the garden crop key whose seed-item matches the given identifier.
     * Only garden crops have seed-item; vanilla crops are never returned.
     */
    public String findCropBySeed(String seedIdentifier) {
        ConfigurationSection garden = plugin.getConfigManager().getConfig("crops.yml")
                .getConfigurationSection("crops.garden");
        if (garden == null) return null;

        for (String cropKey : garden.getKeys(false)) {
            // 1. Check new 'seed' field (reference to seeds.yml)
            String seedKey = garden.getString(cropKey + ".seed");
            if (seedKey != null) {
                String techId = getSeedItemId(seedKey);
                if (techId != null && techId.equalsIgnoreCase(seedIdentifier)) return cropKey;
            }

            // 2. Fallback to legacy 'seed-item' field
            String legacySeed = garden.getString(cropKey + ".seed-item");
            if (legacySeed != null && legacySeed.equalsIgnoreCase(seedIdentifier)) return cropKey;
        }
        return null;
    }

    // ─── DB Async Helpers ────────────────────────────────────────────────

    private void saveAsync(CustomPlantState s) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT OR REPLACE INTO custom_plants " +
                    "(world, x, y, z, owner_uuid, crop_key, watering_level, planted_at, no_water_since, is_rotten) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, s.getLocation().getWorld().getName());
                ps.setInt(2,    s.getLocation().getBlockX());
                ps.setInt(3,    s.getLocation().getBlockY());
                ps.setInt(4,    s.getLocation().getBlockZ());
                ps.setString(5, s.getOwnerUUID().toString());
                ps.setString(6, s.getCropKey());
                ps.setInt(7,    s.getWateringLevel());
                ps.setLong(8,   s.getPlantedAt());
                ps.setLong(9,   s.getNoWaterSince());
                ps.setInt(10,   s.isRotten() ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (save custom plant): " + e.getMessage());
            }
        });
    }

    private void updateAsync(CustomPlantState s) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE custom_plants SET watering_level=?, no_water_since=?, is_rotten=? " +
                    "WHERE world=? AND x=? AND y=? AND z=?";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setInt(1,    s.getWateringLevel());
                ps.setLong(2,   s.getNoWaterSince());
                ps.setInt(3,    s.isRotten() ? 1 : 0);
                ps.setString(4, s.getLocation().getWorld().getName());
                ps.setInt(5,    s.getLocation().getBlockX());
                ps.setInt(6,    s.getLocation().getBlockY());
                ps.setInt(7,    s.getLocation().getBlockZ());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (update custom plant): " + e.getMessage());
            }
        });
    }

    private void deleteAsync(Location loc) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "DELETE FROM custom_plants WHERE world=? AND x=? AND y=? AND z=?";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2,    loc.getBlockX());
                ps.setInt(3,    loc.getBlockY());
                ps.setInt(4,    loc.getBlockZ());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (delete custom plant): " + e.getMessage());
            }
        });
    }
}
