package id.seria.farm.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String color(String message) {
        if (message == null) return "";
        
        // Handle Hex Colors
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(builder, ChatColor.of("#" + color).toString());
        }
        matcher.appendTail(builder);
        
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }
}
