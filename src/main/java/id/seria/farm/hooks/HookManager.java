package id.seria.farm.hooks;

import id.seria.farm.SeriaFarmPlugin;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class HookManager {

    private boolean worldGuardEnabled = false;
    private boolean mmoItemsEnabled = false;
    private boolean auraSkillsEnabled = false;
    private boolean itemsAdderEnabled = false;
    private boolean oraxenEnabled = false;
    private boolean nexoEnabled = false;

    // Reflection Cache to avoid expensive dynamic lookups in hot loops
    private final Map<String, Class<?>> classCache = new HashMap<>();
    private final Map<String, Method> methodCache = new HashMap<>();

    public HookManager(SeriaFarmPlugin plugin) {
        this.worldGuardEnabled = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        this.mmoItemsEnabled = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        this.auraSkillsEnabled = Bukkit.getPluginManager().isPluginEnabled("AuraSkills");
        this.itemsAdderEnabled = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        this.oraxenEnabled = Bukkit.getPluginManager().isPluginEnabled("Oraxen");
        this.nexoEnabled = Bukkit.getPluginManager().isPluginEnabled("Nexo");
        
        if (mmoItemsEnabled) preCacheMMOItems();
        if (auraSkillsEnabled) preCacheAuraSkills();
    }

    private void preCacheMMOItems() {
        // MMOItems API is compile-time available — no reflection caching needed for core operations
    }


    private void preCacheAuraSkills() {
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            classCache.put("AuraSkillsApi", apiClass);
            methodCache.put("AuraSkillsApi.get", apiClass.getMethod("get"));
            methodCache.put("AuraSkillsApi.getUser", apiClass.getMethod("getUser", java.util.UUID.class));
            methodCache.put("AuraSkillsApi.getGlobalRegistry", apiClass.getMethod("getGlobalRegistry"));
            
            Class<?> nIdClass = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedId");
            classCache.put("NamespacedId", nIdClass);
            methodCache.put("NamespacedId.of", nIdClass.getMethod("of", String.class, String.class));
            
            Class<?> registryClass = Class.forName("dev.aurelium.auraskills.api.registry.GlobalRegistry");
            methodCache.put("GlobalRegistry.getSkill", registryClass.getMethod("getSkill", nIdClass));
        } catch (Exception ignored) {}
    }

    public boolean isWorldGuardEnabled() { return worldGuardEnabled; }
    public boolean isMMOItemsEnabled() { return mmoItemsEnabled; }
    public boolean isAuraSkillsEnabled() { return auraSkillsEnabled; }
    public boolean isItemsAdderEnabled() { return itemsAdderEnabled; }
    public boolean isOraxenEnabled() { return oraxenEnabled; }
    public boolean isNexoEnabled() { return nexoEnabled; }

    /**
     * Highly optimized skill level lookup using cached reflection.
     */
    public int getAuraSkillLevel(Player player, String skillName) {
        if (!auraSkillsEnabled) return 0;
        try {
            Object api = methodCache.get("AuraSkillsApi.get").invoke(null);
            Object user = methodCache.get("AuraSkillsApi.getUser").invoke(api, player.getUniqueId());
            if (user == null) return 0;

            Object registry = methodCache.get("AuraSkillsApi.getGlobalRegistry").invoke(api);
            Object nId = methodCache.get("NamespacedId.of").invoke(null, "auraskills", skillName.toLowerCase());
            
            Object skillObj = methodCache.get("GlobalRegistry.getSkill").invoke(registry, nId);
            if (skillObj == null) return 0;

            // skillsUser.getSkillLevel(Skill)
            Method getLevelMethod = user.getClass().getMethod("getSkillLevel", skillObj.getClass().getInterfaces()[0]);
            return (int) getLevelMethod.invoke(user, skillObj);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Fast check if an item is a specific MMOItem using cached NBT lookups.
     */
    public boolean isMMOItem(ItemStack item, String type, String id) {
        if (item == null || !item.hasItemMeta() || !mmoItemsEnabled) return false;
        try {
            String itemType = MMOItems.getTypeName(item);
            String itemId = MMOItems.getID(item);
            return type.equalsIgnoreCase(itemType) && id.equalsIgnoreCase(itemId);
        } catch (Exception e) {
            return false;
        }
    }

    public ItemStack getItem(String identifier) {
        if (identifier == null || identifier.isEmpty()) return new ItemStack(org.bukkit.Material.STONE);

        if (identifier.startsWith("ia:")) return getItemsAdderItem(identifier.substring(3));
        if (identifier.startsWith("nx:")) return getNexoItem(identifier.substring(3));
        if (identifier.startsWith("ox:")) return getOraxenItem(identifier.substring(3));
        
        String mmoId = null;
        if (identifier.startsWith("mi:")) mmoId = identifier.substring(3);
        else if (identifier.startsWith("mmoitems-")) mmoId = identifier.substring(9);

        if (mmoId != null) {
            String[] parts = mmoId.split(":");
            if (parts.length >= 2) return getMMOItemStack(parts[0], parts[1]);
        }

        String finalId = identifier.toUpperCase();
        if (finalId.equals("POTATOES")) finalId = "POTATO";
        else if (finalId.equals("CARROTS")) finalId = "CARROT";
        else if (finalId.equals("BEETROOTS")) finalId = "BEETROOT";
        else if (finalId.equals("WHEAT")) finalId = "WHEAT_SEEDS";
        else if (finalId.equals("COCOA")) finalId = "COCOA_BEANS";

        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(finalId);
        if (mat == null) mat = org.bukkit.Material.matchMaterial(identifier.toUpperCase());
        
        if (mat == null || !mat.isItem()) return new ItemStack(org.bukkit.Material.STONE);
        return new ItemStack(mat);
    }

    public String getItemIdentifier(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return null;

        if (itemsAdderEnabled) {
            try {
                Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object customStack = api.getMethod("byItemStack", ItemStack.class).invoke(null, item);
                if (customStack != null) return "ia:" + api.getMethod("getNamespacedID").invoke(customStack);
            } catch (Exception ignored) {}
        }

        if (nexoEnabled) {
            try {
                Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
                Object id = api.getMethod("getId", ItemStack.class).invoke(null, item);
                if (id != null) return "nx:" + id;
            } catch (Exception ignored) {}
        }

        if (oraxenEnabled) {
            try {
                Class<?> api = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                Object id = api.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
                if (id != null) return "ox:" + id;
            } catch (Exception ignored) {}
        }

        if (mmoItemsEnabled) {
            try {
                String id = MMOItems.getID(item);
                String type = MMOItems.getTypeName(item);
                if (id != null && !id.isEmpty()) return "mi:" + type + ":" + id;
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
            // MMOItems.plugin.getTypes().get(typeName) → Type object
            Type itemType = MMOItems.plugin.getTypes().get(type.toUpperCase());
            if (itemType == null) return new ItemStack(org.bukkit.Material.STONE);
            // MMOItems.plugin.getItem(Type, String) → ItemStack directly
            ItemStack result = MMOItems.plugin.getItem(itemType, id.toUpperCase());
            if (result != null) return result;
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
