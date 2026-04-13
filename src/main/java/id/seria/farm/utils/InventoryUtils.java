package id.seria.farm.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

public class InventoryUtils {

    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(id.seria.farm.inventory.utils.StaticColors.getHexMsg(name));
            List<net.kyori.adventure.text.Component> coloredLore = new ArrayList<>();
            for (String line : lore) {
                if (line != null && !line.isEmpty()) {
                    coloredLore.add(id.seria.farm.inventory.utils.StaticColors.getHexMsg(line));
                }
            }
            meta.lore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItemWithModel(Material material, String name, int modelData, String... lore) {
        ItemStack item = createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }
}
