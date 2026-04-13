package id.seria.farm.utils;

import id.seria.farm.inventory.utils.StaticColors;
import net.kyori.adventure.text.Component;

public class ColorUtils {

    public static Component color(String message) {
        return StaticColors.getHexMsg(message);
    }
}
