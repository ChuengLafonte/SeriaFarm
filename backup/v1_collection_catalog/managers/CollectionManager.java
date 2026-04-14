package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Bukkit;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CollectionManager {

    private final SeriaFarmPlugin plugin;
    // Cache: UUID -> (CropType -> Amount)
    private final Map<UUID, Map<String, Long>> collectionCache = new ConcurrentHashMap<>();
    
    // Milestones (Tier -> Requirement)
    private final long[] milestones = {
        0,          // Tier 0
        50,         // Tier I
        100,        // Tier II
        250,        // Tier III
        500,        // Tier IV
        1000,       // Tier V
        2500,       // Tier VI
        5000,       // Tier VII
        10000,      // Tier VIII
        25000,      // Tier IX
        50000,      // Tier X
        100000      // Tier XI
    };

    public CollectionManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void addCollection(UUID uuid, String cropType, int amount) {
        Map<String, Long> playerMap = collectionCache.computeIfAbsent(uuid, k -> new HashMap<>());
        long newTotal = playerMap.getOrDefault(cropType, 0L) + amount;
        playerMap.put(cropType, newTotal);

        // Async Save to DB
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_collections(uuid, crop_type, amount) VALUES(?,?,?) " +
                         "ON CONFLICT(uuid, crop_type) DO UPDATE SET amount = ?";
            try (PreparedStatement pstmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, cropType);
                pstmt.setLong(3, newTotal);
                pstmt.setLong(4, newTotal);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save collection for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public long getCollectionAmount(UUID uuid, String cropType) {
        Map<String, Long> playerMap = collectionCache.get(uuid);
        if (playerMap != null && playerMap.containsKey(cropType)) {
            return playerMap.get(cropType);
        }

        // Fallback to DB (Synchronous for API/Condition calls)
        String sql = "SELECT amount FROM player_collections WHERE uuid = ? AND crop_type = ?";
        try (PreparedStatement pstmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, cropType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long amount = rs.getLong("amount");
                    collectionCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(cropType, amount);
                    return amount;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load collection for " + uuid + ": " + e.getMessage());
        }
        return 0;
    }

    public int getTier(UUID uuid, String cropType) {
        long amount = getCollectionAmount(uuid, cropType);
        for (int i = milestones.length - 1; i >= 0; i--) {
            if (amount >= milestones[i]) return i;
        }
        return 0;
    }

    public long getRequirement(int tier) {
        if (tier < 0 || tier >= milestones.length) return -1;
        return milestones[tier];
    }
    
    public void clearCache(UUID uuid) {
        collectionCache.remove(uuid);
    }
}
