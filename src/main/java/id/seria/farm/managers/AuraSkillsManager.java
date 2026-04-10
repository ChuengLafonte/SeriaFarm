package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

public class AuraSkillsManager {

    private final SeriaFarmPlugin plugin;
    private final Map<Material, SkillXP> xpMapping = new HashMap<>();

    public AuraSkillsManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        loadMapping();
    }

    private void loadMapping() {
        // MINING
        addMapping(Material.STONE, "mining", 0.2);
        addMapping(Material.COBBLESTONE, "mining", 0.2);
        addMapping(Material.ANDESITE, "mining", 0.2);
        addMapping(Material.DIORITE, "mining", 0.2);
        addMapping(Material.GRANITE, "mining", 0.2);
        addMapping(Material.COAL_ORE, "mining", 1.0);
        addMapping(Material.DEEPSLATE_COAL_ORE, "mining", 1.2);
        addMapping(Material.IRON_ORE, "mining", 1.8);
        addMapping(Material.DEEPSLATE_IRON_ORE, "mining", 2.2);
        addMapping(Material.COPPER_ORE, "mining", 3.5);
        addMapping(Material.DEEPSLATE_COPPER_ORE, "mining", 4.2);
        addMapping(Material.GOLD_ORE, "mining", 17.8);
        addMapping(Material.DEEPSLATE_GOLD_ORE, "mining", 21.4);
        addMapping(Material.REDSTONE_ORE, "mining", 5.9);
        addMapping(Material.DEEPSLATE_REDSTONE_ORE, "mining", 7.1);
        addMapping(Material.LAPIS_ORE, "mining", 30.6);
        addMapping(Material.DEEPSLATE_LAPIS_ORE, "mining", 36.7);
        addMapping(Material.DIAMOND_ORE, "mining", 47.3);
        addMapping(Material.DEEPSLATE_DIAMOND_ORE, "mining", 56.8);
        addMapping(Material.EMERALD_ORE, "mining", 100.0);
        addMapping(Material.DEEPSLATE_EMERALD_ORE, "mining", 120.0);
        addMapping(Material.AMETHYST_CLUSTER, "mining", 6.0);
        addMapping(Material.AMETHYST_BLOCK, "mining", 4.0);
        addMapping(Material.NETHER_QUARTZ_ORE, "mining", 1.8);
        addMapping(Material.NETHERRACK, "mining", 0.1);
        addMapping(Material.BLACKSTONE, "mining", 0.3);
        addMapping(Material.BASALT, "mining", 0.4);
        addMapping(Material.MAGMA_BLOCK, "mining", 0.7);
        addMapping(Material.NETHER_GOLD_ORE, "mining", 10.0);
        addMapping(Material.ANCIENT_DEBRIS, "mining", 500.0);
        addMapping(Material.OBSIDIAN, "mining", 10.0);
        addMapping(Material.DEEPSLATE, "mining", 0.4);

        // FARMING
        addMapping(Material.WHEAT, "farming", 3.5);
        addMapping(Material.POTATOES, "farming", 4.0);
        addMapping(Material.CARROTS, "farming", 4.5);
        addMapping(Material.BEETROOTS, "farming", 5.0);
        addMapping(Material.NETHER_WART, "farming", 3.7);
        addMapping(Material.PUMPKIN, "farming", 4.0);
        addMapping(Material.MELON, "farming", 4.0);
        addMapping(Material.SUGAR_CANE, "farming", 2.0);
        addMapping(Material.COCOA, "farming", 4.0);
        addMapping(Material.CACTUS, "farming", 6.0);
        addMapping(Material.BAMBOO, "farming", 0.25);
        addMapping(Material.KELP, "farming", 0.3);
        addMapping(Material.SWEET_BERRY_BUSH, "farming", 2.5);

        // EXCAVATION
        addMapping(Material.DIRT, "excavation", 0.3);
        addMapping(Material.GRASS_BLOCK, "excavation", 0.7);
        addMapping(Material.COARSE_DIRT, "excavation", 2.3);
        addMapping(Material.PODZOL, "excavation", 2.5);
        addMapping(Material.SAND, "excavation", 0.4);
        addMapping(Material.RED_SAND, "excavation", 0.7);
        addMapping(Material.GRAVEL, "excavation", 1.5);
        addMapping(Material.MYCELIUM, "excavation", 3.7);
        addMapping(Material.CLAY, "excavation", 2.4);
        addMapping(Material.SOUL_SAND, "excavation", 2.7);
        addMapping(Material.SOUL_SOIL, "excavation", 3.0);
        addMapping(Material.MUD, "excavation", 0.5);
    }

    private void addMapping(Material mat, String skill, double xp) {
        if (mat != null) {
            xpMapping.put(mat, new SkillXP(skill, xp));
        }
    }

    public void giveXP(Player player, Material material) {
        SkillXP info = xpMapping.get(material);
        if (info == null) return;

        // Command: sk xp add %player_name% <skill> <amount> -s
        String cmd = String.format("sk xp add %s %s %s -s", player.getName(), info.skill, info.xp);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private static class SkillXP {
        String skill;
        double xp;
        SkillXP(String skill, double xp) {
            this.skill = skill;
            this.xp = xp;
        }
    }
}
