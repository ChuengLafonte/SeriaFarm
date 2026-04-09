package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class RequirementEngine {

    private final SeriaFarmPlugin plugin;

    public RequirementEngine(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canBreak(Player player, ConfigurationSection section) {
        // 1. Check AuraSkills Requirements
        if (!checkAuraSkills(player, section.getConfigurationSection("requirements.auraskills"))) {
            return false;
        }

        // 2. Check Tool Requirements
        return checkTools(player, section.getConfigurationSection("requirements.tools"));
    }

    private boolean checkAuraSkills(Player player, ConfigurationSection section) {
        if (section == null) return true;
        if (!plugin.getHookManager().isAuraSkillsEnabled()) return true;

        for (String skillKey : section.getKeys(false)) {
            String requirement = section.getString(skillKey); // e.g., ">= 10"
            if (requirement == null) continue;

            int playerLevel = getAuraSkillLevel(player, skillKey);
            if (!evaluateExpression(playerLevel, requirement)) return false;
        }
        return true;
    }

    private boolean checkTools(Player player, ConfigurationSection section) {
        if (section == null) return true;
        List<String> allowedTools = section.getStringList("allowed");
        if (allowedTools.isEmpty()) return true;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        for (String toolStr : allowedTools) {
            if (isMatchingTool(heldItem, toolStr)) return true;
        }
        return false;
    }

    private boolean isMatchingTool(ItemStack item, String toolStr) {
        if (item == null) return false;
        if (toolStr.startsWith("mmoitems-")) {
            String[] parts = toolStr.replace("mmoitems-", "").split(":");
            if (parts.length < 2) return false;
            return isMMOItem(item, parts[0], parts[1]);
        } else {
            return item.getType() == Material.matchMaterial(toolStr.toUpperCase());
        }
    }

    private boolean evaluateExpression(int value, String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith(">=")) return value >= Integer.parseInt(trimmed.substring(2).trim());
        if (trimmed.startsWith("<=")) return value <= Integer.parseInt(trimmed.substring(2).trim());
        if (trimmed.startsWith(">")) return value > Integer.parseInt(trimmed.substring(1).trim());
        if (trimmed.startsWith("<")) return value < Integer.parseInt(trimmed.substring(1).trim());
        if (trimmed.startsWith("=")) return value == Integer.parseInt(trimmed.substring(1).trim());
        // Default to >= if only number is provided
        try { return value >= Integer.parseInt(trimmed); } catch (Exception e) { return false; }
    }

    // Hook Logic using Reflection
    private int getAuraSkillLevel(Player player, String skill) {
        if (!plugin.getHookManager().isAuraSkillsEnabled()) return 0;
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Object apiInstance = apiClass.getMethod("get").invoke(null);
            Object user = apiClass.getMethod("getUser", java.util.UUID.class).invoke(apiInstance, player.getUniqueId());
            if (user == null) return 0;
            
            Object registry = apiClass.getMethod("getGlobalRegistry").invoke(apiInstance);
            Object skillObj = registry.getClass().getMethod("getSkill", String.class).invoke(registry, skill.toLowerCase());
            if (skillObj == null) return 0;
            
            return (int) user.getClass().getMethod("getSkillLevel", skillObj.getClass()).invoke(user, skillObj);
        } catch (Exception e) { return 0; }
    }

    private boolean isMMOItem(ItemStack item, String type, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        try {
            Class<?> nbtItemClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            Object nbtItem = nbtItemClass.getMethod("get", ItemStack.class).invoke(null, item);
            String mmoId = (String) nbtItemClass.getMethod("getString", String.class).invoke(nbtItem, "MMOITEMS_ITEM_ID");
            String mmoType = (String) nbtItemClass.getMethod("getString", String.class).invoke(nbtItem, "MMOITEMS_ITEM_TYPE");
            return type.equalsIgnoreCase(mmoType) && id.equalsIgnoreCase(mmoId);
        } catch (Exception e) {
            // Fallback to PersistentDataContainer
            org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey("mmoitems", "type");
            org.bukkit.NamespacedKey idKey = new org.bukkit.NamespacedKey("mmoitems", "id");
            return type.equalsIgnoreCase(pdc.get(typeKey, org.bukkit.persistence.PersistentDataType.STRING)) &&
                   id.equalsIgnoreCase(pdc.get(idKey, org.bukkit.persistence.PersistentDataType.STRING));
        }
    }
}
