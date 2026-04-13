package id.seria.farm;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import id.seria.farm.managers.*;
import id.seria.farm.hooks.HookManager;
import id.seria.farm.listeners.*;
import id.seria.farm.inventory.MainMenu;

public class SeriaFarmPlugin extends JavaPlugin {

    public static SeriaFarmPlugin instance;
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private RegenManager regenManager;
    private FarmManager farmManager;
    private VisualManager visualManager;
    private HookManager hookManager;
    private AuraSkillsManager auraSkillsManager;
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
        auraSkillsManager = new AuraSkillsManager(this);

        // Register Listeners
        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BlockBreakListener(this), this);
        pm.registerEvents(new InteractListener(this), this);
        pm.registerEvents(new ChatInputListener(), this);
        pm.registerEvents(new WandListener(), this);
        pm.registerEvents(new MainMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.maintree.ToggleMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.addtree.AddMenu(), this);
        pm.registerEvents(new id.seria.farm.inventory.addtree.AddBlocksMenu(), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.RegionEdit.RegionSelectionMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.RegionEdit.PreRegionMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.BlockMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.EditMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.ReplaceBlockMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.DropsMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.maintree.GlobalBlocksMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(this), this);
        pm.registerEvents(new RegionListener(this), this);
        pm.registerEvents(new GrowthListener(this), this);
        pm.registerEvents(new BlockPlaceListener(this), this);

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
    public AuraSkillsManager getAuraSkillsManager() { return auraSkillsManager; }
    public RequirementEngine getRequirementEngine() { return requirementEngine; }
}
