package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Composted Soil slot system.
 * Each player has a base quota + bonus from AuraSkills Farming level thresholds.
 */
public class SoilSlotManager {

    private final SeriaFarmPlugin plugin;
    // In-memory cache of placed soil blocks
    private final Map<org.bukkit.Location, UUID> placedSoils = new ConcurrentHashMap<>();
    private boolean loaded = false;

    public SoilSlotManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        placedSoils.clear();
        String sql = "SELECT * FROM player_soil_blocks";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String worldName = rs.getString("world");
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world == null) continue;
                org.bukkit.Location loc = new org.bukkit.Location(
                        world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                placedSoils.put(loc, owner);
            }
            loaded = true;
            plugin.getLogger().info("Loaded " + placedSoils.size() + " composted soil blocks.");
        } catch (SQLException e) {
            plugin.getLogger().warning("DB Error (load soil blocks): " + e.getMessage());
        }
    }

    // ─── Slot Calculation ─────────────────────────────────────────────────

    public int getMaxSlots(Player player) {
        int base = plugin.getConfigManager().getConfig("config.yml")
                .getInt("composted-soil.base-slots", 9);

        ConfigurationSection thresholds = plugin.getConfigManager().getConfig("config.yml")
                .getConfigurationSection("composted-soil.level-thresholds");

        if (thresholds == null || !plugin.getHookManager().isAuraSkillsEnabled()) {
            return base;
        }

        int farmingLevel = plugin.getAuraSkillsManager().getFarmingLevel(player);
        int bonus = 0;
        for (String levelStr : thresholds.getKeys(false)) {
            try {
                int threshold = Integer.parseInt(levelStr);
                if (farmingLevel >= threshold) {
                    bonus = Math.max(bonus, thresholds.getInt(levelStr, 0));
                }
            } catch (NumberFormatException ignored) {}
        }
        return base + bonus;
    }

    public int getUsedSlots(Player player) {
        return getUsedSlotsByUUID(player.getUniqueId());
    }

    public int getUsedSlotsByUUID(UUID uuid) {
        if (!loaded) loadAll();
        return (int) placedSoils.values().stream().filter(u -> u.equals(uuid)).count();
    }

    public boolean canPlace(Player player) {
        return getUsedSlots(player) < getMaxSlots(player);
    }

    public boolean isSoilOwner(org.bukkit.Location loc, UUID uuid) {
        UUID owner = placedSoils.get(loc);
        return owner != null && owner.equals(uuid);
    }

    public UUID getOwner(org.bukkit.Location loc) {
        return placedSoils.get(loc);
    }

    public void placeSoil(org.bukkit.Location loc, Player player) {
        UUID uuid = player.getUniqueId();
        placedSoils.put(loc, uuid);
        saveAsync(loc, uuid);
    }

    public void breakSoil(org.bukkit.Location loc) {
        if (placedSoils.remove(loc) != null) {
            deleteAsync(loc);
        }
    }

    // ─── DB ───────────────────────────────────────────────────────────────

    private void saveAsync(org.bukkit.Location loc, UUID uuid) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT OR REPLACE INTO player_soil_blocks (world, x, y, z, owner_uuid) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (save soil blocks): " + e.getMessage());
            }
        });
    }

    private void deleteAsync(org.bukkit.Location loc) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "DELETE FROM player_soil_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (delete soil blocks): " + e.getMessage());
            }
        });
    }
}
