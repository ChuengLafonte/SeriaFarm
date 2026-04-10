package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final SeriaFarmPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    public ConfigManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        String[] files = {"config.yml", "materials.yml", "crops.yml", "gui.yml", "messages.yml", "regions.yml"};
        for (String fileName : files) {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }
            configs.put(fileName, YamlConfiguration.loadConfiguration(file));
            configFiles.put(fileName, file);
        }
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    public File getConfigFile(String name) {
        return configFiles.get(name);
    }

    public void saveConfig(String name) {
        try {
            configs.get(name).save(configFiles.get(name));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save config " + name + ": " + e.getMessage());
        }
    }

    public void reloadConfigs() {
        configs.clear();
        configFiles.clear();
        loadConfigs();
    }

    // Helper to get formatted messages
    public String getMessage(String path) {
        FileConfiguration msgConfig = getConfig("messages.yml");
        String msg = msgConfig.getString(path);
        
        if (msg == null) {
            // Fallback to internal resource
            java.io.InputStream internalStream = plugin.getResource("messages.yml");
            if (internalStream != null) {
                FileConfiguration internalConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(internalStream));
                msg = internalConfig.getString(path, path);
            } else {
                msg = path;
            }
        }
        
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
}
