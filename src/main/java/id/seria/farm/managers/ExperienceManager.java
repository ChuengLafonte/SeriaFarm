package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ExperienceManager {

    private final SeriaFarmPlugin plugin;
    private String engine;
    private String skillId;

    public ExperienceManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.engine = plugin.getConfigManager().getConfig("config.yml").getString("settings.xp-engine", "VANILLA").toUpperCase();
        this.skillId = plugin.getConfigManager().getConfig("config.yml").getString("settings.xp-skill-id", "farming");
    }

    /**
     * Gives XP to a player based on the configured engine.
     * @param player The player receiving XP
     * @param amount The amount of XP
     */
    public void giveXP(Player player, double amount) {
        if (amount <= 0) return;

        switch (engine) {
            case "AURASKILLS":
                giveAuraSkillsXP(player, amount);
                break;
            case "MCMMO":
                giveMcMMOXP(player, amount);
                break;
            case "MMOCORE":
                giveMMOCoreXP(player, amount);
                break;
            case "VANILLA":
            default:
                player.giveExp((int) amount);
                break;
        }
    }

    private void giveAuraSkillsXP(Player player, double amount) {
        if (!plugin.getHookManager().isAuraSkillsEnabled()) {
            player.giveExp((int) amount);
            return;
        }
        try {
            dev.aurelium.auraskills.api.AuraSkillsApi api = dev.aurelium.auraskills.api.AuraSkillsApi.get();
            dev.aurelium.auraskills.api.user.SkillsUser user = api.getUser(player.getUniqueId());
            
            // Try with auraskills namespace
            dev.aurelium.auraskills.api.skill.Skill skill = api.getGlobalRegistry().getSkill(dev.aurelium.auraskills.api.registry.NamespacedId.of("auraskills", skillId));
            
            if (skill != null && user != null) {
                user.addSkillXp(skill, amount);
            } else {
                // If API cannot find the skill with 'auraskills' namespace, fallback to console command
                executeAuraSkillsCommand(player, amount);
            }
        } catch (NoClassDefFoundError | Exception e) {
            executeAuraSkillsCommand(player, amount);
        }
    }

    private void executeAuraSkillsCommand(Player player, double amount) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("sk xp add %s %s %s -s", player.getName(), skillId, (int) amount));
    }

    private void giveMcMMOXP(Player player, double amount) {
        // Use reflection for mcMMO to avoid direct dependency
        try {
            Class<?> experienceApiClass = Class.forName("com.gmail.nunoiv.mcmmo.api.ExperienceAPI");
            experienceApiClass.getMethod("addRawXP", Player.class, String.class, float.class, String.class)
                    .invoke(null, player, skillId, (float) amount, "UNKNOWN");
        } catch (Exception e) {
            // Fallback to command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("addxp %s %s %s", player.getName(), skillId, (int) amount));
        }
    }

    private void giveMMOCoreXP(Player player, double amount) {
        // Use reflection for MMOCore
        try {
            Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            Object playerData = playerDataClass.getMethod("get", Player.class).invoke(null, player);
            Object stats = playerDataClass.getMethod("getStats").invoke(playerData);
            
            Class<?> experienceTypeClass = Class.forName("net.Indyuce.mmocore.api.experience.ExperienceType");
            Object expType = experienceTypeClass.getField(skillId.toUpperCase()).get(null);
            
            stats.getClass().getMethod("addExperience", experienceTypeClass, double.class).invoke(stats, expType, amount);
        } catch (Exception e) {
            // Fallback to command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("mmocore admin exp add %s %s %s", player.getName(), skillId, (int) amount));
        }
    }
}
