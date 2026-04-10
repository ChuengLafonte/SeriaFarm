package id.seria.farm.inventory.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InvUtils {

    public static ItemStack createItemStacks(Material material, String name, String... loreLines) {
        if (material == null) return new ItemStack(Material.BEDROCK);
        if (material.isAir()) material = Material.DRAGON_BREATH;
        return applyMeta(new ItemStack(getSingleMaterial(material), 1), name, loreLines);
    }

    public static ItemStack applyMeta(ItemStack item, String name, String... loreLines) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StaticColors.getHexMsg(name));
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                if (line != null && !line.isEmpty()) {
                    lore.add(StaticColors.getHexMsg(line));
                }
            }
            meta.setLore(lore);
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
