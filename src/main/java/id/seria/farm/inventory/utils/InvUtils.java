package id.seria.farm.inventory.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InvUtils {

    public static ItemStack createItemStacks(Material material, Object name, Object... loreLines) {
        if (material == null) return new ItemStack(Material.BEDROCK);
        if (material.isAir()) material = Material.DRAGON_BREATH;
        return applyMeta(new ItemStack(getSingleMaterial(material), 1), name, loreLines);
    }

    public static ItemStack applyMeta(ItemStack item, Object name, Object... loreLines) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name instanceof net.kyori.adventure.text.Component) {
                meta.displayName((net.kyori.adventure.text.Component) name);
            } else if (name instanceof String) {
                meta.displayName(StaticColors.getHexMsg((String) name));
            }
 
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (Object line : loreLines) {
                if (line == null) continue;
                if (line instanceof net.kyori.adventure.text.Component) {
                    lore.add((net.kyori.adventure.text.Component) line);
                } else if (line instanceof String) {
                    String str = (String) line;
                    if (!str.isEmpty()) {
                        lore.add(StaticColors.getHexMsg(str));
                    }
                }
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static Material getSingleMaterial(Material material) {
        String name = material.toString();
        name = getSingleCrop(name);
        Material mat = Material.getMaterial(name);
        return mat != null ? mat : material;
    }

    public static String getSingleCrop(String string) {
        Map<String, String> map = Map.of(
            "POTATOES", "POTATO", 
            "CARROTS", "CARROT", 
            "BEETROOTS", "BEETROOT", 
            "COCOA", "COCOA_BEANS", 
            "SWEET_BERRY_BUSH", "SWEET_BERRIES", 
            "WATER", "WATER_BUCKET", 
            "LAVA", "LAVA_BUCKET"
        );
        return map.getOrDefault(string, string);
    }

    public static String extractStr(String string) {
        if (string == null) return null;
        int n = string.indexOf("[") + 1;
        int n2 = string.indexOf("]");
        if (n > 0 && n2 > 0 && n < n2) {
            return string.substring(n, n2).trim();
        }
        return null;
    }

    public static String getFriendlyName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    public static void stripTechnicalLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // 1. Remove Lore
        if (meta.hasLore()) {
            List<net.kyori.adventure.text.Component> lore = meta.lore();
            if (lore != null) {
                lore.removeIf(line -> {
                    String plain = id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(line);
                    return plain.contains("Chance:") || 
                           plain.contains("Weight:") || 
                           plain.contains("Click:") || 
                           plain.contains("Set Weight") ||
                           plain.contains("Remove");
                });
                while (!lore.isEmpty() && id.seria.farm.SeriaFarmPlugin.MINI_MESSAGE.serialize(lore.get(lore.size() - 1)).trim().isEmpty()) {
                    lore.remove(lore.size() - 1);
                }
                meta.lore(lore);
            }
        }

        // 2. Remove Technical NBT to allow stacking
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(id.seria.farm.SeriaFarmPlugin.chanceKey);
        pdc.remove(id.seria.farm.SeriaFarmPlugin.weightKey);
        pdc.remove(id.seria.farm.SeriaFarmPlugin.key);
        
        item.setItemMeta(meta);
    }
}
