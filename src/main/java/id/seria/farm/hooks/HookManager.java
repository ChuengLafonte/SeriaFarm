package id.seria.farm.hooks;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class HookManager {

    private boolean worldGuardEnabled = false;
    private boolean mmoItemsEnabled = false;
    private boolean auraSkillsEnabled = false;
    private boolean itemsAdderEnabled = false;
    private boolean oraxenEnabled = false;
    private boolean nexoEnabled = false;

    public HookManager(SeriaFarmPlugin plugin) {
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

    public ItemStack getItem(String identifier) {
        if (identifier == null || identifier.isEmpty()) return new ItemStack(org.bukkit.Material.STONE);

        if (identifier.startsWith("ia:")) return getItemsAdderItem(identifier.substring(3));
        if (identifier.startsWith("nx:")) return getNexoItem(identifier.substring(3));
        if (identifier.startsWith("ox:")) return getOraxenItem(identifier.substring(3));
        if (identifier.startsWith("mi:")) {
            String[] parts = identifier.substring(3).split(":");
            if (parts.length >= 2) return getMMOItemStack(parts[0], parts[1]);
        }

        String finalId = identifier.toUpperCase();
        
        // Manual mapping for common blocks that aren't items in modern Bukkit
        if (finalId.equals("POTATOES")) finalId = "POTATO";
        else if (finalId.equals("CARROTS")) finalId = "CARROT";
        else if (finalId.equals("BEETROOTS")) finalId = "BEETROOT";
        else if (finalId.equals("WHEAT")) finalId = "WHEAT_SEEDS";
        else if (finalId.equals("COCOA")) finalId = "COCOA_BEANS";

        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(finalId);
        if (mat == null) mat = org.bukkit.Material.matchMaterial(identifier.toUpperCase());
        
        if (mat == null || !mat.isItem()) {
            return new ItemStack(org.bukkit.Material.STONE);
        }
        
        return new ItemStack(mat);
    }

    public String getItemIdentifier(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return null;

        // Try ItemsAdder
        if (itemsAdderEnabled) {
            try {
                Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object customStack = api.getMethod("byItemStack", ItemStack.class).invoke(null, item);
                if (customStack != null) return "ia:" + api.getMethod("getNamespacedID").invoke(customStack);
            } catch (Exception ignored) {}
        }

        // Try Nexo
        if (nexoEnabled) {
            try {
                Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
                Object id = api.getMethod("getId", ItemStack.class).invoke(null, item);
                if (id != null) return "nx:" + id;
            } catch (Exception ignored) {}
        }

        // Try Oraxen
        if (oraxenEnabled) {
            try {
                Class<?> api = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                Object id = api.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
                if (id != null) return "ox:" + id;
            } catch (Exception ignored) {}
        }

        // Try MMOItems (Simple NBT check or API)
        if (mmoItemsEnabled) {
            try {
                Class<?> api = Class.forName("net.Indyuce.mmoitems.api.Type");
                Object type = api.getMethod("get", ItemStack.class).invoke(null, item);
                if (type != null) {
                    Class<?> itemClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
                    Object nbtItem = itemClass.getMethod("get", ItemStack.class).invoke(null, item);
                    String id = (String) itemClass.getMethod("getString", String.class).invoke(nbtItem, "MMOITEMS_ITEM_ID");
                    if (id != null) return "mi:" + type.toString() + ":" + id;
                }
            } catch (Exception ignored) {}
        }

        return item.getType().name();
    }

    private ItemStack getItemsAdderItem(String id) {
        if (!itemsAdderEnabled) return new ItemStack(org.bukkit.Material.STONE);
        try {
            Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = api.getMethod("getInstance", String.class).invoke(null, id);
            if (customStack != null) return (ItemStack) api.getMethod("getItemStack").invoke(customStack);
        } catch (Exception ignored) {}
        return new ItemStack(org.bukkit.Material.STONE);
    }

    private ItemStack getNexoItem(String id) {
        if (!nexoEnabled) return new ItemStack(org.bukkit.Material.STONE);
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object item = api.getMethod("getItem", String.class).invoke(null, id);
            if (item != null) return (ItemStack) item;
        } catch (Exception ignored) {}
        return new ItemStack(org.bukkit.Material.STONE);
    }

    private ItemStack getOraxenItem(String id) {
        if (!oraxenEnabled) return new ItemStack(org.bukkit.Material.STONE);
        try {
            Class<?> api = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object item = api.getMethod("getItemById", String.class).invoke(null, id);
            if (item != null) return (ItemStack) item;
        } catch (Exception ignored) {}
        return new ItemStack(org.bukkit.Material.STONE);
    }

    private ItemStack getMMOItemStack(String type, String id) {
        if (!mmoItemsEnabled) return new ItemStack(org.bukkit.Material.STONE);
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object mmoItemsInstance = mmoItemsClass.getMethod("get").invoke(null);
            Object itemType = mmoItemsClass.getMethod("getType", String.class).invoke(mmoItemsInstance, type.toUpperCase());
            if (itemType == null) return new ItemStack(org.bukkit.Material.STONE);
            
            Object mmoItem = mmoItemsClass.getMethod("getItem", itemType.getClass(), String.class).invoke(mmoItemsInstance, itemType, id.toUpperCase());
            if (mmoItem != null) return (ItemStack) mmoItem.getClass().getMethod("build").invoke(mmoItem);
        } catch (Exception ignored) {}
        return new ItemStack(org.bukkit.Material.STONE);
    }

    public void giveMMOItem(Player player, String type, String id) {
        ItemStack item = getMMOItemStack(type, id);
        if (item != null && item.getType() != org.bukkit.Material.STONE) {
            player.getInventory().addItem(item);
        }
    }
}
