package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RequiredToolsMenu implements Listener, InventoryHolder {

    private final SeriaFarmPlugin plugin;
    private String matName;
    private String regionName;
    private String path;

    public RequiredToolsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String path) {
        this.matName = matName;
        this.regionName = regionName;
        this.path = path;

        Inventory inv = Bukkit.createInventory(this, 54, StaticColors.getHexMsg("&#9370db&lRequired Tools Editor"));

        // Setup Border
        ItemStack glass = InvUtils.createItemStacks(Material.PURPLE_STAINED_GLASS_PANE, " ", "");
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,49,51,52,53}) {
            inv.setItem(i, glass);
        }

        inv.setItem(48, InvUtils.createItemStacks(Material.LIME_STAINED_GLASS_PANE, "&a&lSAVE &f& EXIT", "&7Apply requirements to config"));
        inv.setItem(50, InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL", "&7Discard changes"));

        // Load existing tools
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        List<String> tools = config.getStringList(path + ".requirements.tools");
        
        int slot = 10;
        List<Integer> contentSlots = getContentSlots();
        for (String toolStr : tools) {
            ItemStack item = parseToolItem(toolStr);
            if (item != null) {
                while (!contentSlots.contains(slot) && slot < 44) slot++;
                if (slot >= 44) break;
                
                applyLore(item);
                inv.setItem(slot, item);
                slot++;
            }
        }

        player.openInventory(inv);
    }

    private List<Integer> getContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 10; i <= 16; i++) slots.add(i);
        for (int i = 19; i <= 25; i++) slots.add(i);
        for (int i = 28; i <= 34; i++) slots.add(i);
        for (int i = 37; i <= 43; i++) slots.add(i);
        return slots;
    }

    private ItemStack parseToolItem(String toolStr) {
        if (toolStr.startsWith("mmoitems-")) {
            String[] parts = toolStr.replace("mmoitems-", "").split(":");
            if (parts.length >= 2) {
                return plugin.getHookManager().getItem(parts[1]); // Fallback to MMOItem lookup
            }
        }
        Material mat = Material.matchMaterial(toolStr.toUpperCase());
        return mat != null ? new ItemStack(mat) : null;
    }

    private void applyLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&7- &fRequired Tool"));
            lore.add(Component.empty());
            lore.add(StaticColors.getHexMsg("&7Click to &cRemove"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 54, Component.text("Required Tools"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RequiredToolsMenu)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        if (slot == 50) { // Cancel
            refreshEditMenu(player);
            return;
        }

        if (slot == 48) { // Save
            saveData(player, event.getInventory());
            refreshEditMenu(player);
            return;
        }

        // Logic for adding/removing
        if (slot < 54 && getContentSlots().contains(slot)) {
            if (clicked != null && clicked.getType() != Material.AIR) {
                event.getInventory().setItem(slot, null);
            }
        } else if (slot >= 54) {
            // Click item in player inventory to add
            if (clicked != null && clicked.getType() != Material.AIR) {
                addTool(event.getInventory(), clicked.clone());
            }
        }
    }

    private void addTool(Inventory inv, ItemStack item) {
        List<Integer> contentSlots = getContentSlots();
        for (int slot : contentSlots) {
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                ItemStack clean = item.clone();
                clean.setAmount(1);
                applyLore(clean);
                inv.setItem(slot, clean);
                break;
            }
        }
    }

    private void refreshEditMenu(Player player) {
        YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        File matFile = plugin.getConfigManager().getConfigFile("crops.yml");
        player.openInventory(new EditMenu(plugin).emenu(player, matConfig, this.matName, matFile, this.regionName));
    }

    private void saveData(Player player, Inventory inv) {
        List<String> tools = new ArrayList<>();
        for (int slot : getContentSlots()) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                tools.add(identifyItem(item));
            }
        }

        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        config.set(this.path + ".requirements.tools", tools);
        plugin.getConfigManager().saveConfig("crops.yml");
        player.sendMessage(StaticColors.getHexMsg("&6&lSeriaFarm &8» &aRequirements updated!"));
    }

    private String identifyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item != null ? item.getType().name() : "AIR";
        
        // Check for MMOItems
        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey("mmoitems", "type");
        org.bukkit.NamespacedKey idKey = new org.bukkit.NamespacedKey("mmoitems", "id");
        
        if (pdc.has(typeKey, org.bukkit.persistence.PersistentDataType.STRING) && 
            pdc.has(idKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            return "mmoitems-" + pdc.get(typeKey, org.bukkit.persistence.PersistentDataType.STRING) + ":" + 
                    pdc.get(idKey, org.bukkit.persistence.PersistentDataType.STRING);
        }
        
        return item.getType().name();
    }
}
