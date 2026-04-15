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

    /**
     * Called by WaterDecayTask every decay-interval seconds.
     * Handles: water decay → rot check → growth advancement.
     */
    public void tickDecay() {
        long now = System.currentTimeMillis();
        for (CustomPlantState state : cache.values()) {
            if (state.isRotten()) continue;

            ConfigurationSection waterCfg = getWaterConfig(state.getCropKey());
            if (waterCfg == null || !waterCfg.getBoolean("enabled", false)) continue;

            int decayRate         = waterCfg.getInt("decay-rate", 1);
            int decayIntervalSecs = waterCfg.getInt("decay-interval", 120);
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

            // ── Growth advancement (only when watered) ────────────────────
            if (newLevel > 0) {
                int regenDelaySecs = getRegenDelaySecs(state.getCropKey());
                long wateredMs = now - state.getPlantedAt();
                
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    Location loc = state.getLocation();
                    if (loc.getWorld() == null) return;
                    Block b = loc.getBlock();
                    if (b.getBlockData() instanceof Ageable ag && ag.getAge() < ag.getMaximumAge()) {
                        int exactAge = (int) Math.floor(((double)wateredMs / (regenDelaySecs * 1000L)) * ag.getMaximumAge());
                        exactAge = Math.min(ag.getMaximumAge(), Math.max(ag.getAge(), exactAge));
                        if (exactAge != ag.getAge()) {
                            ag.setAge(exactAge);
                            b.setBlockData(ag, true);
                        }
                    }
                });
            }

            updateAsync(state);
        }
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
        if (garden == null) {
            plugin.getLogger().warning("[DEBUG] isValidSoil: crops.garden section NOT FOUND in crops.yml!");
            return false;
        }

        for (String cropKey : garden.getKeys(false)) {
            for (String soil : garden.getStringList(cropKey + ".soil")) {
                // 1. Vanilla name match (fast, no HookManager call)
                if (soil.equalsIgnoreCase(blockTypeName)) return true;
                // 2. Resolved base-material match (cached after first call)
                Material resolved = resolveSoilMaterial(soil);
                plugin.getLogger().info("[DEBUG] isValidSoil: soil='" + soil + "' resolved='" + resolved + "' blockType='" + blockTypeName + "'");
                if (resolved != Material.AIR && resolved == blockType) return true;
            }
        }
        return false;
    }

    public java.util.List<String> getAllSoilIdentifiers() {
        java.util.List<String> soils = new java.util.ArrayList<>();
        ConfigurationSection garden = plugin.getConfigManager().getConfig("crops.yml")
                .getConfigurationSection("crops.garden");
        if (garden == null) return soils;
        for (String cropKey : garden.getKeys(false)) {
            soils.addAll(garden.getStringList(cropKey + ".soil"));
        }
        return soils.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    public boolean isSoilItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String id = plugin.getHookManager().getItemIdentifier(item);
        
        ConfigurationSection garden = plugin.getConfigManager().getConfig("crops.yml")
                .getConfigurationSection("crops.garden");
        if (garden == null) return false;

        for (String cropKey : garden.getKeys(false)) {
            for (String soil : garden.getStringList(cropKey + ".soil")) {
                if (soil.equalsIgnoreCase(id) || soil.equalsIgnoreCase(item.getType().name())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find the garden crop key whose seed-item matches the given identifier.
     * Only garden crops have seed-item; vanilla crops are never returned.
     */
    public String findCropBySeed(String seedIdentifier) {
        ConfigurationSection garden = plugin.getConfigManager().getConfig("crops.yml")
                .getConfigurationSection("crops.garden");
        if (garden == null) {
            plugin.getLogger().warning("[DEBUG] findCropBySeed: crops.garden NOT FOUND!");
            return null;
        }
        for (String cropKey : garden.getKeys(false)) {
            String seed = garden.getString(cropKey + ".seed-item", "");
            if (seed.equalsIgnoreCase(seedIdentifier)) return cropKey;
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
