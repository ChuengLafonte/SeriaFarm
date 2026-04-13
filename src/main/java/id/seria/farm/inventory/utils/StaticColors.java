package id.seria.farm.inventory.utils;
 
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
public class StaticColors {
 
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
 
    /**
     * Parses a string into a Component, supporting:
     * - HEX: &#rrggbb
     * - Legacy: &c
     * - Legacy HEX: &x&r&r&g&g&b&b
     * - MiniMessage: <color>, <gradient>, etc.
     */
    public static Component getHexMsg(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
 
        // 1. Handle &#rrggbb -> <color:#rrggbb>
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<color:#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        String processed = sb.toString();
 
        Component result;
        // 2. Resolve final component
        if (processed.contains("&")) {
            // First, translate any legacy colors (including legacy HEX &x...) into a Component
            Component legacy = LegacyComponentSerializer.legacyAmpersand().deserialize(processed);
            
            // If the original string also had MiniMessage tags, we merge them precisely
            if (processed.contains("<")) {
                // Serializing legacy safely back to MiniMessage tags preserves formatting
                String mmString = MINI_MESSAGE.serialize(legacy).replace("\\<", "<").replace("\\>", ">");
                result = MINI_MESSAGE.deserialize(mmString);
            } else {
                result = legacy;
            }
        } else {
            // 3. Just MiniMessage
            result = MINI_MESSAGE.deserialize(processed);
        }
 
        // 4. Force no italics by default
        return result.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
