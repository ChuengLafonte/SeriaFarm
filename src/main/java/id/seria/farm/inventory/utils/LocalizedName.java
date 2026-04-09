package id.seria.farm.inventory.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import id.seria.farm.SeriaFarmPlugin;

public class LocalizedName {
    public static ItemStack set(ItemStack itemStack, String string) {
        NamespacedKey namespacedKey = SeriaFarmPlugin.key;
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
            persistentDataContainer.set(namespacedKey, PersistentDataType.STRING, string);
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }

    public static String get(ItemStack itemStack) {
        PersistentDataContainer persistentDataContainer;
        String string = "0";
        NamespacedKey namespacedKey = SeriaFarmPlugin.key;
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            persistentDataContainer = itemMeta.getPersistentDataContainer();
            if (persistentDataContainer.has(namespacedKey, PersistentDataType.STRING)) {
                string = persistentDataContainer.get(namespacedKey, PersistentDataType.STRING);
            }
        }
        return string;
    }
}
