package id.seria.farm.utils;

import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class WandUtils {

    public static ItemStack getWand() {
        ItemStack wand = new ItemStack(Material.STONE_AXE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(StaticColors.getHexMsg("&eREGEN WAND"));
            meta.setCustomModelData(20);
            wand.setItemMeta(meta);
        }
        return wand;
    }
}
