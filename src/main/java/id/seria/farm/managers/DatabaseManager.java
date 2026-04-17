package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final SeriaFarmPlugin plugin;
    private Connection connection;

    public DatabaseManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String url = "jdbc:sqlite:" + dataFolder + File.separator + "database.db";
            connection = DriverManager.getConnection(url);
            createTables();
            plugin.getLogger().info("SQLite database connected for SeriaFarm.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        String regenTable = "CREATE TABLE IF NOT EXISTS regenerating_blocks (" +
                "world TEXT NOT NULL," +
                "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL," +
                "original_mat TEXT NOT NULL," +
                "original_data TEXT," +
                "restore_time BIGINT NOT NULL," +
                "material_key TEXT," +
                "is_growth INTEGER DEFAULT 0," +
                "growth_mode TEXT," +
                "max_stage INTEGER DEFAULT 0," +
                "current_stage INTEGER DEFAULT 0," +
                "replace_blocks TEXT," +
                "delay_blocks TEXT," +
                "PRIMARY KEY (world, x, y, z)" +
                ");";

        String cropsTable = "CREATE TABLE IF NOT EXISTS planted_crops (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "world TEXT NOT NULL," +
                "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL," +
                "owner_uuid TEXT NOT NULL," +
                "crop_type TEXT NOT NULL," +
                "planted_at BIGINT NOT NULL," +
                "watering_level INT DEFAULT 0," +
                "fertilizer_type TEXT" +
                ");";

        String customPlantsTable = "CREATE TABLE IF NOT EXISTS custom_plants (" +
                "world TEXT NOT NULL," +
                "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL," +
                "owner_uuid TEXT NOT NULL," +
                "crop_key TEXT NOT NULL," +
                "watering_level INT DEFAULT 0," +
                "planted_at BIGINT NOT NULL," +
                "no_water_since BIGINT DEFAULT 0," +
                "is_rotten INTEGER DEFAULT 0," +
                "PRIMARY KEY (world, x, y, z)" +
                ");";

        String soilSlotsTable = "CREATE TABLE IF NOT EXISTS player_soil_blocks (" +
                "world TEXT NOT NULL," +
                "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL," +
                "owner_uuid TEXT NOT NULL," +
                "soil_id TEXT," +
                "PRIMARY KEY (world, x, y, z)" +
                ");";

        String playerStatsTable = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "uuid TEXT PRIMARY KEY," +
                "extra_soil_slots INTEGER DEFAULT 0" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(regenTable);
            stmt.execute(cropsTable);
            stmt.execute(customPlantsTable);
            stmt.execute(soilSlotsTable);
            stmt.execute(playerStatsTable);
            
            // Migration: Ensure new columns exist in regenerating_blocks
            String[] newCols = {
                "original_data TEXT", "material_key TEXT", "is_growth INTEGER DEFAULT 0",
                "growth_mode TEXT", "max_stage INTEGER DEFAULT 0", "current_stage INTEGER DEFAULT 0",
                "replace_blocks TEXT", "delay_blocks TEXT"
            };
            for (String col : newCols) {
                try {
                    stmt.execute("ALTER TABLE regenerating_blocks ADD COLUMN " + col + ";");
                } catch (SQLException ignored) {}
            }

            try {
                stmt.execute("ALTER TABLE player_soil_blocks ADD COLUMN soil_id TEXT;");
            } catch (SQLException ignored) {}
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /** Saves a full RegenBlock state for crash recovery. */
    public void saveRegeneratingBlock(id.seria.farm.models.RegenBlock regen) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT OR REPLACE INTO regenerating_blocks " +
                    "(world, x, y, z, original_mat, original_data, restore_time, material_key, " +
                    "is_growth, growth_mode, max_stage, current_stage, replace_blocks, delay_blocks) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, regen.getLocation().getWorld().getName());
                ps.setInt(2,    regen.getLocation().getBlockX());
                ps.setInt(3,    regen.getLocation().getBlockY());
                ps.setInt(4,    regen.getLocation().getBlockZ());
                ps.setString(5, regen.getOriginalMaterial().name());
                ps.setString(6, regen.getOriginalData().getAsString());
                ps.setLong(7,   regen.getRestoreTime());
                ps.setString(8, regen.getMaterialKey());
                ps.setInt(9,    regen.isGrowth() ? 1 : 0);
                ps.setString(10, regen.getGrowthMode());
                ps.setInt(11,   regen.getMaxStage());
                ps.setInt(12,   regen.getCurrentStage());
                
                String rb = regen.getReplaceBlocks() != null ? String.join(";", regen.getReplaceBlocks()) : "";
                String db = regen.getDelayBlocks() != null ? String.join(";", regen.getDelayBlocks()) : "";
                
                ps.setString(13, rb);
                ps.setString(14, db);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (save regen): " + e.getMessage());
            }
        });
    }

    public void removeRegeneratingBlock(org.bukkit.Location loc) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "DELETE FROM regenerating_blocks WHERE world=? AND x=? AND y=? AND z=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2,    loc.getBlockX());
                ps.setInt(3,    loc.getBlockY());
                ps.setInt(4,    loc.getBlockZ());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (remove regen): " + e.getMessage());
            }
        });
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database: " + e.getMessage());
        }
    }

    // ─── Player Stats ─────────────────────────────────────────────────────

    public int getExtraSlots(java.util.UUID uuid) {
        String sql = "SELECT extra_soil_slots FROM player_stats WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("extra_soil_slots");
        } catch (SQLException e) {
            plugin.getLogger().warning("DB Error (get extra slots): " + e.getMessage());
        }
        return 0;
    }

    public void setExtraSlots(java.util.UUID uuid, int amount) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT OR REPLACE INTO player_stats (uuid, extra_soil_slots) VALUES (?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB Error (set extra slots): " + e.getMessage());
            }
        });
    }
}
