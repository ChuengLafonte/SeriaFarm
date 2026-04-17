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

    // ─── Model ───
    public static class SoilBlock {
        private final UUID owner;
        private final String soilId;
        public SoilBlock(UUID owner, String soilId) {
            this.owner = owner;
            this.soilId = soilId;
        }
        public UUID getOwner() { return owner; }
        public String getSoilId() { return soilId; }
    }

    private final SeriaFarmPlugin plugin;
    // In-memory cache of placed soil blocks
    private final Map<org.bukkit.Location, SoilBlock> placedSoils = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> extraSlotsCache = new ConcurrentHashMap<>();
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
                String sId = rs.getString("soil_id");
                placedSoils.put(loc, new SoilBlock(owner, sId));
            }
            loaded = true;
            plugin.getLogger().info("Loaded " + placedSoils.size() + " composted soil blocks.");
        } catch (SQLException e) {
            plugin.getLogger().warning("DB Error (load soil blocks): " + e.getMessage());
        }
    }

    // ─── Slot Calculation ─────────────────────────────────────────────────

    public int getMaxSlots(Player player) {
        int base = plugin.getConfigManager().getSettings().baseSoilSlots;

        // Add admin-given extra slots
        int extra = getExtraSlots(player.getUniqueId());

        ConfigurationSection thresholds = plugin.getConfigManager().getConfig("config.yml")
                .getConfigurationSection("composted-soil.level-thresholds");

        if (thresholds == null || !plugin.getHookManager().isAuraSkillsEnabled()) {
            return base + extra;
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
        return base + bonus + extra;
    }

    public int getExtraSlots(UUID uuid) {
        return extraSlotsCache.computeIfAbsent(uuid, k -> plugin.getDatabaseManager().getExtraSlots(k));
    }

    public int getUsedSlots(Player player) {
        return getUsedSlotsByUUID(player.getUniqueId());
    }

    public int getUsedSlotsByUUID(UUID uuid) {
        if (!loaded) loadAll();
        return (int) placedSoils.values().stream().filter(b -> b.getOwner().equals(uuid)).count();
    }

    public boolean canPlace(Player player) {
        return getUsedSlots(player) < getMaxSlots(player);
    }

    public boolean isSoilOwner(org.bukkit.Location loc, UUID uuid) {
        SoilBlock b = placedSoils.get(loc);
        return b != null && b.getOwner().equals(uuid);
    }

    public UUID getOwner(org.bukkit.Location loc) {
        SoilBlock b = placedSoils.get(loc);
        return b != null ? b.getOwner() : null;
    }

    public String getSoilId(org.bukkit.Location loc) {
        SoilBlock b = placedSoils.get(loc);
        return b != null ? b.getSoilId() : null;
    }

    public Map<org.bukkit.Location, SoilBlock> getPlacedSoils() {
        return placedSoils;
    }

    public void placeSoil(org.bukkit.Location loc, Player player, String soilId) {
        UUID uuid = player.getUniqueId();
        placedSoils.put(loc, new SoilBlock(uuid, soilId));
        saveAsync(loc, uuid, soilId);
    }

    public void breakSoil(org.bukkit.Location loc) {
        if (placedSoils.remove(loc) != null) {
            deleteAsync(loc);
        }
    }

    /**
     * Forcefully removes soil without typical break checks (used for island deletion).
     */
    public void forceRemoveSoil(org.bukkit.Location loc) {
        if (placedSoils.remove(loc) != null) {
            deleteAsync(loc);
            loc.getBlock().setType(org.bukkit.Material.AIR);
        }
    }

    /**
     * Picks up all soil blocks owned by the target player and returns them to their inventory.
     */
    public int pickupAllSoil(org.bukkit.command.CommandSender admin, Player target) {
        UUID uuid = target.getUniqueId();
        java.util.List<org.bukkit.Location> targetLocs = new java.util.ArrayList<>();
        
        for (Map.Entry<org.bukkit.Location, SoilBlock> entry : placedSoils.entrySet()) {
            if (entry.getValue().getOwner().equals(uuid)) {
                targetLocs.add(entry.getKey());
            }
        }

        int count = 0;
        for (org.bukkit.Location loc : targetLocs) {
            String soilId = getSoilId(loc);
            if (soilId != null) {
                // Return item to player
                org.bukkit.configuration.file.FileConfiguration soilsConfig = plugin.getConfigManager().getConfig("soils.yml");
                String itemId = soilsConfig.getString("soils." + soilId + ".item-id");
                if (itemId != null) {
                    org.bukkit.inventory.ItemStack item = plugin.getHookManager().getItem(itemId);
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        target.getInventory().addItem(item);
                    }
                }
            }
            forceRemoveSoil(loc);
            count++;
        }
        return count;
    }

    // ─── Extra Slots Management ──────────────────────────────────────────

    public void setExtraSlots(UUID uuid, int total) {
        int val = Math.max(0, total);
        extraSlotsCache.put(uuid, val);
        plugin.getDatabaseManager().setExtraSlots(uuid, val);
    }

    public void addExtraSlots(UUID uuid, int amount) {
        setExtraSlots(uuid, getExtraSlots(uuid) + amount);
    }

    public void removeExtraSlots(UUID uuid, int amount) {
        setExtraSlots(uuid, getExtraSlots(uuid) - amount);
    }

    public void sendNotification(Player player) {
        int used = getUsedSlots(player);
        int max = getMaxSlots(player);
        
        String msg = plugin.getConfigManager().getConfig("messages.yml")
                .getString("soil-slot-notification", "&fCave Soil, Slot &e{used}&f/&e{max}");
        
        msg = msg.replace("{used}", String.valueOf(used))
                 .replace("{max}", String.valueOf(max));
        
        plugin.getConfigManager().sendPrefixedMessage(player, msg);
    }

    // ─── DB ───────────────────────────────────────────────────────────────

    private void saveAsync(org.bukkit.Location loc, UUID uuid, String soilId) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT OR REPLACE INTO player_soil_blocks (world, x, y, z, owner_uuid, soil_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, uuid.toString());
                ps.setString(6, soilId);
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
