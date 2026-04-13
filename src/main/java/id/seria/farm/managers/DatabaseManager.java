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
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "world TEXT NOT NULL," +
                "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL," +
                "original_mat TEXT NOT NULL," +
                "restore_time BIGINT NOT NULL," +
                "block_data TEXT" +
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

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(regenTable);
            stmt.execute(cropsTable);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void saveRegenBlock(String world, int x, int y, int z, String material, long time) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO regenerating_blocks(world, x, y, z, original_mat, restore_time) VALUES(?,?,?,?,?,?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, world);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.setString(5, material);
                pstmt.setLong(6, time);
                pstmt.executeUpdate();
            } catch (SQLException e) { 
                plugin.getLogger().warning("DB Error (Async): " + e.getMessage()); 
            }
        });
    }

    public void removeRegenBlock(String world, int x, int y, int z) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "DELETE FROM regenerating_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, world);
                pstmt.setInt(2, x);
                pstmt.setInt(3, y);
                pstmt.setInt(4, z);
                pstmt.executeUpdate();
            } catch (SQLException e) { 
                plugin.getLogger().warning("DB Error (Async): " + e.getMessage()); 
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
}
