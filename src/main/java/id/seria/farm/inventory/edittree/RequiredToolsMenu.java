package id.seria.farm.inventory.edittree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import id.seria.farm.inventory.utils.LocalizedName;

import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.Map;

public class RequiredToolsMenu implements Listener {
    private final SeriaFarmPlugin plugin;

    public RequiredToolsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName, String regionName, String path) {
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        List<String> tools = config.getStringList(path + ".requirements.tools.tool");
        String deny = config.getString(path + ".requirements.tools.deny", "Alat yang digunakan tidak sesuai!");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%deny%", deny);
        Inventory inv = plugin.getGuiManager().createInventory("required-tools-menu", placeholders);

        // Metadata in slot 49: matName|regionName|path|deny
        ItemStack info = inv.getItem(49);
        if (info != null) LocalizedName.set(info, matName + "|" + regionName + "|" + path + "|" + deny);

        // Load existing tools into content slots
        int slot = 0;
        for (String toolStr : tools) {
            ItemStack item = parseToolItem(toolStr);
            if (item != null) {
                while (slot < 44 && (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR)) slot++;
                if (slot >= 44) break;
                applyLore(item);
                LocalizedName.set(item, "TOOL:" + toolStr);
                inv.setItem(slot, item);
                slot++;
            }
        }

        player.openInventory(inv);
    }

    private ItemStack parseToolItem(String toolStr) {
        // Attempt to get the real display item via HookManager (handles mmoitems-, ia:, vanilla, etc.)
        ItemStack item = plugin.getHookManager().getItem(toolStr);
        if (item.getType() != Material.STONE) return item;

        // Fallback: STONE means the item couldn't be built (e.g. MMOItem not in template registry).
        // Show a BARRIER labeled with the identifier so the slot isn't empty.
        String label = "&c[?] &f" + toolStr;
        return InvUtils.createItemStacks(Material.BARRIER, label,
            "&7Item tidak dapat dimuat.",
            "&7ID: &f" + toolStr,
            "",
            "&7Klik untuk menghapus dari daftar");
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof id.seria.farm.managers.GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("required-tools-menu")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        // Read state from slot 49 metadata: matName|regionName|path|deny
        ItemStack metaSource = event.getInventory().getItem(49);
        if (metaSource == null) return;
        String metaData = LocalizedName.get(metaSource);
        if (metaData == null || !metaData.contains("|")) return;
        String[] metaParts = metaData.split("\\|", 4);
        final String currentMat = metaParts[0];
        final String currentRegion = metaParts[1];
        final String currentPath = metaParts[2];
        final String currentDeny = metaParts.length > 3 ? metaParts[3] : "Alat yang digunakan tidak sesuai!";

        if (slot < event.getInventory().getSize() && slot >= 0) {
            ItemStack metaItem = event.getInventory().getItem(slot);
            String action = metaItem != null ? LocalizedName.get(metaItem) : null;
            if (action != null) {
                if (action.equals("cancel")) refreshEditMenu(player, currentMat, currentRegion);
                if (action.equals("save")) { saveData(player, event.getInventory(), currentPath, currentDeny); refreshEditMenu(player, currentMat, currentRegion); }
                if (action.equals("edit_deny")) {
                    id.seria.farm.listeners.ChatInputListener.requestInput(player,
                        "Deny Message", "Pesan penolakan alat",
                        input -> {
                            // Save deny immediately, re-open with updated deny
                            YamlConfiguration cfg = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
                            cfg.set(currentPath + ".requirements.tools.deny", input);
                            plugin.getConfigManager().saveConfig("crops.yml");
                            open(player, currentMat, currentRegion, currentPath);
                        },
                        () -> open(player, currentMat, currentRegion, currentPath)
                    );
                }
                return;
            }

            // Click in content zone to remove
            if (slot < 44 && clicked != null && clicked.getType() != Material.AIR) {
                event.getInventory().setItem(slot, null);
            }
        } else if (slot >= event.getInventory().getSize()) {
            // Player inventory click to add
            if (clicked != null && clicked.getType() != Material.AIR) {
                addTool(event.getInventory(), clicked.clone());
            }
        }
    }

    private void addTool(Inventory inv, ItemStack item) {
        // Identify BEFORE any meta manipulation to preserve MMOItems PDC access
        String toolId = identifyItem(item);
        for (int i = 0; i < 44; i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                ItemStack display = item.clone();
                display.setAmount(1);
                applyLore(display);
                // Store the identifier so saveData can read it without touching PDC again
                LocalizedName.set(display, "TOOL:" + toolId);
                inv.setItem(i, display);
                break;
            }
        }
    }

    private void refreshEditMenu(Player player, String matName, String regionName) {
        YamlConfiguration matConfig = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        player.openInventory(new EditMenu(plugin).emenu(player, matConfig, matName, null, regionName));
    }

    private void saveData(Player player, Inventory inv, String path, String deny) {
        List<String> tools = new ArrayList<>();
        for (int i = 0; i < 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            String tag = LocalizedName.get(item);
            if (tag != null && tag.startsWith("TOOL:")) {
                tools.add(tag.substring(5));
            }
        }
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        config.set(path + ".requirements.tools.tool", tools);
        config.set(path + ".requirements.tools.deny", deny);
        plugin.getConfigManager().saveConfig("crops.yml");
        plugin.getConfigManager().sendPrefixedMessage(player, "&aTools updated! (" + tools.size() + " tools)");
    }

    private String identifyItem(ItemStack item) {
        if (item == null) return "AIR";
        // Delegate to HookManager which uses NBT (MythicLib) for MMOItems detection
        String id = plugin.getHookManager().getItemIdentifier(item);
        if (id == null) return item.getType().name();
        // Convert HookManager "mi:TYPE:ID" format → "mmoitems-TYPE:ID" for RequirementEngine compatibility
        if (id.startsWith("mi:")) return "mmoitems-" + id.substring(3);
        return id; // vanilla material name or other plugin identifiers as-is
    }
}
