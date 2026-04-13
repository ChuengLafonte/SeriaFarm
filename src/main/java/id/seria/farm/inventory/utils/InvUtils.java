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
}
