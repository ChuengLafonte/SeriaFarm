package id.seria.farm.hooks;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class HookManager {

    private final SeriaFarmPlugin plugin;
    private boolean worldGuardEnabled = false;
    private boolean mmoItemsEnabled = false;
    private boolean auraSkillsEnabled = false;
    private boolean itemsAdderEnabled = false;
    private boolean oraxenEnabled = false;
    private boolean nexoEnabled = false;

    public HookManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        this.worldGuardEnabled = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        this.mmoItemsEnabled = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        this.auraSkillsEnabled = Bukkit.getPluginManager().isPluginEnabled("AuraSkills");
        this.itemsAdderEnabled = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        this.oraxenEnabled = Bukkit.getPluginManager().isPluginEnabled("Oraxen");
        this.nexoEnabled = Bukkit.getPluginManager().isPluginEnabled("Nexo");
    }

    public boolean isWorldGuardEnabled() { return worldGuardEnabled; }
    public boolean isMMOItemsEnabled() { return mmoItemsEnabled; }
    public boolean isAuraSkillsEnabled() { return auraSkillsEnabled; }
    public boolean isItemsAdderEnabled() { return itemsAdderEnabled; }
    public boolean isOraxenEnabled() { return oraxenEnabled; }
    public boolean isNexoEnabled() { return nexoEnabled; }

    // WorldGuard check (Simplified)
    public void giveMMOItem(Player player, String type, String id) {
        if (!mmoItemsEnabled) return;
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object mmoItemsInstance = mmoItemsClass.getMethod("get").invoke(null);
            Object itemType = mmoItemsClass.getMethod("getType", String.class).invoke(mmoItemsInstance, type.toUpperCase());
            if (itemType == null) return;
            
            Object mmoItem = mmoItemsClass.getMethod("getItem", itemType.getClass(), String.class).invoke(mmoItemsInstance, itemType, id.toUpperCase());
            if (mmoItem != null) {
                ItemStack item = (ItemStack) mmoItem.getClass().getMethod("build").invoke(mmoItem);
                player.getInventory().addItem(item);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give MMOItem " + type + ":" + id);
        }
    }
}
