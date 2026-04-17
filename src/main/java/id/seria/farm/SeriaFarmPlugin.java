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
    private GuiManager guiManager;
    private CustomPlantManager customPlantManager;
    private ExperienceManager experienceManager;
    private SoilSlotManager soilSlotManager;
    private WateringToolManager wateringToolManager;
    private CustomPlantHologramManager hologramManager;
    public static org.bukkit.NamespacedKey key;
    public static org.bukkit.NamespacedKey chanceKey;
    public static org.bukkit.NamespacedKey weightKey;

    @Override
    public void onEnable() {
        instance = this;
        key = new org.bukkit.NamespacedKey(this, "localized_name");
        chanceKey = new org.bukkit.NamespacedKey(this, "chance_value");
        weightKey = new org.bukkit.NamespacedKey(this, "weight_value");
        saveDefaultConfig();

        getLogger().info("========================================");
        getLogger().info("  SeriaFarm v" + getPluginMeta().getVersion());
        getLogger().info("  Initializing hybrid farm system...");
        getLogger().info("========================================");

        // Initialize Managers
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        experienceManager = new ExperienceManager(this);

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        hookManager = new HookManager(this);
        requirementEngine = new RequirementEngine(this);
        guiManager = new GuiManager(this);

        regenManager = new RegenManager(this);
        farmManager = new FarmManager(this);
        visualManager = new VisualManager(this);
        auraSkillsManager = new AuraSkillsManager(this);

        // Inject drop tables AFTER systems are ready
        configManager.injectDropTablesIntoCrops();

        // Custom plant system
        wateringToolManager = new WateringToolManager(this);
        soilSlotManager = new SoilSlotManager(this);
        soilSlotManager.loadAll();
        customPlantManager = new CustomPlantManager(this);
        customPlantManager.loadFromDatabase();
        hologramManager = new CustomPlantHologramManager(this);
        id.seria.farm.tasks.WaterDecayTask.start(this);
        id.seria.farm.tasks.GrowthTask.start(this);

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
        pm.registerEvents(new id.seria.farm.inventory.edittree.SproutBlockMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.DropsMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.RequiredToolsMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.RequiredSkillsMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.SkillDetailMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.edittree.VerticalGrowthMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.maintree.GlobalBlocksMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(this), this);
        pm.registerEvents(new RegionListener(this), this);
        pm.registerEvents(new GrowthListener(this), this);
        pm.registerEvents(new BlockPlaceListener(this), this);
        pm.registerEvents(new SoilListener(this), this);
        pm.registerEvents(new CropProtectionListener(this), this);
        pm.registerEvents(new CropPhysicsListener(this), this);
        pm.registerEvents(new id.seria.farm.inventory.watering.WateringToolsMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.watering.WateringSettingsMenu(this), this);
        pm.registerEvents(new id.seria.farm.inventory.watering.SoilRequirementMenu(this), this);
        // Cleanup holograms on player quit
        pm.registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
                hologramManager.cleanup(e.getPlayer().getUniqueId());
            }
        }, this);

        // Register BentoBox event listener only if plugin exists
        if (pm.isPluginEnabled("BentoBox")) {
            pm.registerEvents(new BentoBoxListener(this), this);
        }

        // Register AuraSkills event listener only if plugin exists
        if (pm.isPluginEnabled("AuraSkills")) {
            pm.registerEvents(new AuraSkillsListener(this), this);
        }

        // Register Commands
        id.seria.farm.commands.SFarmCommand sfarmCommand = new id.seria.farm.commands.SFarmCommand(this);
        getCommand("sfarm").setExecutor(sfarmCommand);
        getCommand("sfarm").setTabCompleter(sfarmCommand);

        getLogger().info("SeriaFarm enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (regenManager != null)
            regenManager.shutdown();
        if (databaseManager != null)
            databaseManager.close();
    }

    public static SeriaFarmPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public RegenManager getRegenManager() {
        return regenManager;
    }

    public FarmManager getFarmManager() {
        return farmManager;
    }

    public VisualManager getVisualManager() {
        return visualManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public AuraSkillsManager getAuraSkillsManager() {
        return auraSkillsManager;
    }

    public RequirementEngine getRequirementEngine() {
        return requirementEngine;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public CustomPlantManager getCustomPlantManager() {
        return customPlantManager;
    }

    public ExperienceManager getExperienceManager() {
        return experienceManager;
    }

    public SoilSlotManager getSoilSlotManager() {
        return soilSlotManager;
    }

    public WateringToolManager getWateringToolManager() {
        return wateringToolManager;
    }

    public CustomPlantHologramManager getHologramManager() {
        return hologramManager;
    }
}
