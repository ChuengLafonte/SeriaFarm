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
        return canBreak(player, section, true);
    }

    public boolean canBreak(Player player, ConfigurationSection section, boolean sendMessage) {
        if (section == null) return true;

        // 1. Check AuraSkills Requirements (Nested Section)
        if (!checkAuraSkills(player, section.getConfigurationSection("requirements.skills"), sendMessage)) {
            return false;
        }

        // 2. Check Tool Requirements
        boolean hasTools = checkTools(player, section);
        if (!hasTools && sendMessage) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission-to-break"));
        }
        return hasTools;
    }

    // Alias for planting/placing checks
    public boolean canPlace(Player player, ConfigurationSection section) {
        return canBreak(player, section, true);
    }

    private boolean checkAuraSkills(Player player, ConfigurationSection section, boolean sendMessage) {
        if (section == null) return true;
        if (!plugin.getHookManager().isAuraSkillsEnabled()) return true;

        for (String key : section.getKeys(false)) {
            ConfigurationSection sub = section.getConfigurationSection(key);
            if (sub == null) continue;

            String skillName = sub.getString("skill");
            String operator = sub.getString("operator", ">=");
            int level = sub.getInt("level", 0);
            String denyMsg = sub.getString("deny");

            if (skillName == null) continue;

            int playerLevel = getAuraSkillLevel(player, skillName);
            if (!evaluateExpression(playerLevel, operator + " " + level)) {
                if (sendMessage) {
                    if (denyMsg != null && !denyMsg.isEmpty()) {
                        String processed = denyMsg.replace("%skill%", skillName)
                                .replace("%level%", String.valueOf(level))
                                .replace("%operator%", operator)
                                .replace("%current%", String.valueOf(playerLevel));
                        plugin.getConfigManager().sendPrefixedMessage(player, processed);
                    } else {
                        // Fallback to generic message but include level if possible?
                        // For now just generic.
                        player.sendMessage(plugin.getConfigManager().getMessage("no-permission-to-break"));
                    }
                }
                return false;
            }
        }
        return true;
    }

    private boolean checkTools(Player player, ConfigurationSection section) {
        if (section == null) return true;
        
        // Unify to the list format used by the GUI
        List<String> allowedTools = section.getStringList("requirements.tools");
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
            
            // Use NamespacedId for robustness
            Class<?> nIdClass = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedId");
            Object nId = nIdClass.getMethod("of", String.class, String.class).invoke(null, "auraskills", skill.toLowerCase());
            
            Object skillObj = registry.getClass().getMethod("getSkill", nIdClass).invoke(registry, nId);
            if (skillObj == null) return 0;
            
            Class<?> skillInterface = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
            return (int) user.getClass().getMethod("getSkillLevel", skillInterface).invoke(user, skillObj);
        } catch (Exception e) {
            return 0;
        }
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
