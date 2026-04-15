package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.StaticColors;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final SeriaFarmPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();
    private FileConfiguration internalMessagesConfig = null;

    public ConfigManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        String[] files = {"config.yml", "crops.yml", "gui.yml", "messages.yml", "regions.yml"};
        for (String fileName : files) {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }
            configs.put(fileName, YamlConfiguration.loadConfiguration(file));
            configFiles.put(fileName, file);
        }
        
        // Handle migration from materials.yml if crops.yml is missing its core structure
        handleMigration();
    }

    private void handleMigration() {
        FileConfiguration cropsConfig = getConfig("crops.yml");
        if (cropsConfig.contains("crops.global")) return; // Already has new structure

        File materialsFile = new File(plugin.getDataFolder(), "materials.yml");
        if (!materialsFile.exists()) return;

        plugin.getLogger().warning("Detected legacy materials.yml. Migrating data to crops.yml...");
        FileConfiguration materialsConfig = YamlConfiguration.loadConfiguration(materialsFile);
        
        ConfigurationSection blocks = materialsConfig.getConfigurationSection("blocks");
        if (blocks != null) {
            cropsConfig.set("crops", blocks);
            saveConfig("crops.yml");
            plugin.getLogger().info("Successfully migrated blocks from materials.yml to crops.yml.");
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
        internalMessagesConfig = null;
        loadConfigs();
        
        // Refresh dependent caches
        if (plugin.getRegenManager() != null) {
            plugin.getRegenManager().refreshCaches();
        }
        injectDropTablesIntoCrops();
    }

    public void injectDropTablesIntoCrops() {
        FileConfiguration config = getConfig("crops.yml");
        ConfigurationSection tables = config.getConfigurationSection("drop-tables");
        ConfigurationSection globalCrops = config.getConfigurationSection("crops.global");

        if (tables == null) return;
        if (globalCrops == null) {
            plugin.getLogger().warning("Could not inject drop tables: 'crops.global' section missing in crops.yml!");
            return;
        }

        if (plugin.getRegenManager() == null) {
            plugin.getLogger().severe("RegenManager not initialized! Cannot inject drop tables.");
            return;
        }

        int count = 0;
        for (String tableKey : tables.getKeys(false)) {
            // Find matching crop in global
            String targetCrop = null;
            for (String cropKey : globalCrops.getKeys(false)) {
                if (plugin.getRegenManager().isMatch(tableKey, "", cropKey)) {
                    targetCrop = cropKey;
                    break;
                }
            }

            if (targetCrop == null) continue;

            // Merge drops
            List<Map<String, Object>> dropsList = new java.util.ArrayList<>();
            List<?> tableDrops = tables.getList(tableKey + ".drops");
            
            if (tableDrops != null) {
                for (Object obj : tableDrops) {
                    if (!(obj instanceof Map)) continue;
                    Map<?, ?> drop = (Map<?, ?>) obj;
                    String matStr = (String) drop.get("material");
                    double chance = drop.containsKey("chance") ? ((Number) drop.get("chance")).doubleValue() : 100.0;
                    
                    ItemStack item = plugin.getHookManager().getItem(matStr);
                    if (item == null || item.getType().isAir()) continue;

                    Map<String, Object> serializedDrop = new java.util.HashMap<>();
                    serializedDrop.put("item", item);
                    serializedDrop.put("chance", chance);
                    if (drop.containsKey("farming-level")) serializedDrop.put("farming-level", drop.get("farming-level"));
                    if (drop.containsKey("weight")) serializedDrop.put("weight", drop.get("weight"));
                    
                    dropsList.add(serializedDrop);
                }
            }

            if (!dropsList.isEmpty()) {
                config.set("crops.global." + targetCrop + ".rewards.drops", dropsList);
                count++;
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Injected " + count + " drop tables into global crops.");
        }
    }

    // Helper to get formatted messages
    public Component getMessage(String path) {
        FileConfiguration msgConfig = getConfig("messages.yml");
        String prefix = msgConfig.getString("prefix", "");
        String msg = msgConfig.getString(path);
        
        if (msg == null) {
            // Fallback to internal resource
            if (internalMessagesConfig == null) {
                java.io.InputStream internalStream = plugin.getResource("messages.yml");
                if (internalStream != null) {
                    internalMessagesConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(internalStream));
                }
            }
            
            if (internalMessagesConfig != null) {
                msg = internalMessagesConfig.getString(path, path);
            } else {
                msg = path;
            }
        }
        
        return StaticColors.getHexMsg(prefix + msg);
    }

    public void sendPrefixedMessage(org.bukkit.entity.Player player, String message) {
        FileConfiguration msgConfig = getConfig("messages.yml");
        String prefix = msgConfig.getString("prefix", "");
        player.sendMessage(StaticColors.getHexMsg(prefix + message));
    }
}
