package id.seria.farm.utils;

import id.seria.farm.inventory.utils.StaticColors;
import net.kyori.adventure.text.Component;

public class RarityUtils {

    public enum Rarity {
        JUNK("Vanilla", "&7★★★★★"),
        COMMON("> 10%", "<#FFF800>★&7★★★★"),
        RARE("5% - 10%", "<#FFF800>★★&7★★★"),
        EPIC("2% - 5%", "<#FFF800>★★★&7★★"),
        LEGENDARY("0.5% - 2%", "<#FFF800>★★★★&7★"),
        MYTHICAL("0.1% - 0.5%", "<#FFF800>★★★★★"),
        DIVINE("< 0.1%", "<#00FF98>★<#01E8B2>★<#02D0CC>★<#03B9E5>★<#04A1FF>★");

        private final String range;
        private final String starLore;

        Rarity(String range, String starLore) {
            this.range = range;
            this.starLore = starLore;
        }

        public String getRange() { return range; }
        public String getStarLore() { return starLore; }
        public Component getStarComponent() {
            return StaticColors.getHexMsg(starLore);
        }
    }

    public static Rarity getRarity(double chance) {
        if (chance >= 100.0) return Rarity.JUNK;
        if (chance < 0.1) return Rarity.DIVINE;
        if (chance < 0.5) return Rarity.MYTHICAL;
        if (chance < 2.0) return Rarity.LEGENDARY;
        if (chance < 5.0) return Rarity.EPIC;
        if (chance < 10.0) return Rarity.RARE;
        return Rarity.COMMON;
    }
}
