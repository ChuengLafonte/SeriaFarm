package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
            String customDeny = getToolDenyMessage(section);
            if (customDeny != null && !customDeny.isEmpty()) {
                plugin.getConfigManager().sendPrefixedMessage(player, customDeny);
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission-to-break"));
            }
        }
        return hasTools;
    }

    public boolean canPlace(Player player, ConfigurationSection section, Block soilBlock) {
        // 1. Check Standard Permissions/AuraSkills/Tools (Reuse canBreak logic)
        if (!canBreak(player, section, true)) {
            return false;
        }

        // 2. Check Soil Requirements
        return checkSoil(player, soilBlock, section);
    }

    private boolean checkSoil(Player player, Block soilBlock, ConfigurationSection section) {
        if (section == null) return true;
        
        List<String> allowedSoils = section.getStringList("soil");
        if (allowedSoils.isEmpty()) return true;

        // Get actual soil ID if it's a managed composted soil
        String placedSoilId = plugin.getSoilSlotManager().getSoilId(soilBlock.getLocation());
        String vanillaMat = soilBlock.getType().name();

        for (String allowed : allowedSoils) {
            // Case 1: Match with Composted Soil ID (e.g. "composted_soil")
            if (placedSoilId != null && placedSoilId.equalsIgnoreCase(allowed)) return true;
            
            // Case 2: Match with Vanilla Material (e.g. "FARMLAND")
            if (vanillaMat.equalsIgnoreCase(allowed)) return true;
        }

        // If we reach here, soil is invalid
        String soilsStr = String.join(", ", allowedSoils);
        String msg = plugin.getConfigManager().getConfig("messages.yml")
                .getString("invalid-soil", "&cTanah tidak sesuai! &fTanaman ini membutuhkan tanah jenis: &e{soils}");
        
        plugin.getConfigManager().sendPrefixedMessage(player, msg.replace("{soils}", soilsStr));
        return false;
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

            // Use the OPTIMIZED lookup from HookManager
            int playerLevel = plugin.getHookManager().getAuraSkillLevel(player, skillName);
            if (!evaluateExpression(playerLevel, operator, level)) {
                if (sendMessage) {
                    if (denyMsg != null && !denyMsg.isEmpty()) {
                        String processed = denyMsg.replace("%skill%", skillName)
                                .replace("%level%", String.valueOf(level))
                                .replace("%operator%", operator)
                                .replace("%current%", String.valueOf(playerLevel));
                        plugin.getConfigManager().sendPrefixedMessage(player, processed);
                    } else {
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
        
        // Support both old flat list and new nested structure
        List<String> allowedTools;
        if (section.isList("requirements.tools.tool")) {
            allowedTools = section.getStringList("requirements.tools.tool");
        } else {
            allowedTools = section.getStringList("requirements.tools");
        }
        if (allowedTools.isEmpty()) return true;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        for (String toolStr : allowedTools) {
            if (isMatchingTool(heldItem, toolStr)) return true;
        }
        return false;
    }

    private String getToolDenyMessage(ConfigurationSection section) {
        if (section == null) return null;
        return section.getString("requirements.tools.deny");
    }

    private boolean isMatchingTool(ItemStack item, String toolStr) {
        if (item == null) return false;
        if (toolStr.startsWith("mmoitems-")) {
            String[] parts = toolStr.replace("mmoitems-", "").split(":");
            if (parts.length < 2) return false;
            // Use OPTIMIZED check from HookManager
            return plugin.getHookManager().isMMOItem(item, parts[0], parts[1]);
        } else {
            return item.getType() == Material.matchMaterial(toolStr.toUpperCase());
        }
    }

    private boolean evaluateExpression(int value, String operator, int threshold) {
        return switch (operator) {
            case ">=" -> value >= threshold;
            case "<=" -> value <= threshold;
            case ">" -> value > threshold;
            case "<" -> value < threshold;
            case "=" -> value == threshold;
            default -> value >= threshold;
        };
    }
}
