package id.seria.farm;

import org.bukkit.plugin.java.JavaPlugin;
import id.seria.farm.managers.*;
import id.seria.farm.hooks.HookManager;
import id.seria.farm.listeners.*;
import id.seria.farm.inventory.MainMenu;

public class SeriaFarmPlugin extends JavaPlugin {

    public static SeriaFarmPlugin instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private RegenManager regenManager;
    private FarmManager farmManager;
    private VisualManager visualManager;
    private HookManager hookManager;
    private RequirementEngine requirementEngine;
    public static org.bukkit.NamespacedKey key;
    public static org.bukkit.NamespacedKey chanceKey;

    @Override
    public void onEnable() {
        instance = this;
        key = new org.bukkit.NamespacedKey(this, "localized_name");
        chanceKey = new org.bukkit.NamespacedKey(this, "chance_value");
        saveDefaultConfig();

        getLogger().info("========================================");
        getLogger().info("  SeriaFarm v" + getPluginMeta().getVersion());
        getLogger().info("  Initializing hybrid farm system...");
        getLogger().info("========================================");

        // Initialize Managers
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        hookManager = new HookManager(this);
        requirementEngine = new RequirementEngine(this);

        regenManager = new RegenManager(this);
        farmManager = new FarmManager(this);
        visualManager = new VisualManager(this);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(), this);
        getServer().getPluginManager().registerEvents(new WandListener(), this);
        getServer().getPluginManager().registerEvents(new MainMenu(this), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.maintree.ToggleMenu(), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.addtree.AddMenu(), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.addtree.AddBlocksMenu(), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.edittree.RegionEdit.RegionSelectionMenu(this), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.edittree.RegionEdit.PreRegionMenu(this), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.edittree.BlockMenu(this), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.edittree.EditMenu(this), this);
        getServer().getPluginManager().registerEvents(new id.seria.farm.inventory.edittree.ReplaceBlockMenu(this), this);
        getServer().getPluginManager().registerEvents(new RegionListener(this), this);

        // Register Commands
        id.seria.farm.commands.SFarmCommand sfarmCommand = new id.seria.farm.commands.SFarmCommand(this);
        getCommand("sfarm").setExecutor(sfarmCommand);
        getCommand("sfarm").setTabCompleter(sfarmCommand);

        getLogger().info("SeriaFarm enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (regenManager != null) regenManager.shutdown();
        if (databaseManager != null) databaseManager.close();
    }

    public static SeriaFarmPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public RegenManager getRegenManager() { return regenManager; }
    public FarmManager getFarmManager() { return farmManager; }
    public VisualManager getVisualManager() { return visualManager; }
    public HookManager getHookManager() { return hookManager; }
    public RequirementEngine getRequirementEngine() { return requirementEngine; }
}
