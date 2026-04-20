package id.seria.farm.inventory.watering;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.listeners.ChatInputListener;
import id.seria.farm.managers.GuiManager;
import id.seria.farm.managers.WateringToolManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin GUI for registering and configuring custom watering tools.
 *
 * UX Pattern (mirrors RequiredToolsMenu):
 *   - CLICK item in PLAYER INVENTORY  → add to menu slots (auto-fill next free slot)
 *   - CLICK existing item in MENU SLOTS → open edit dialog (per-use, capacity, empty-item)
 *   - RIGHT-CLICK existing item in MENU SLOTS → remove immediately
 *   - SAVE → write all tools to config.yml
 *   - CANCEL → return to MainMenu without saving
 *
 * Layout:
 *   Row 1: border (slots 0-9)
 *   Row 2: [B] TOOL TOOL TOOL TOOL TOOL TOOL TOOL [B][B]  (slots 10-16, 17-18 border)
 *   Row 3: [B] TOOL TOOL TOOL TOOL TOOL TOOL TOOL [B][B]  (slots 19-25, 26-27 border)
 *   Row 4: [B] TOOL TOOL TOOL TOOL TOOL TOOL TOOL [B][B]  (slots 28-34, 35-36 border)
 *   Row 5: [B] TOOL TOOL TOOL TOOL TOOL TOOL TOOL [B](44)  (slots 37-43, border 36+44)  ← unused, considered free
 *   Row 6: [B][B][B] SAVE INFO CANCEL [B][B][B]  (slots 45-53)
 */
public class WateringToolsMenu implements Listener {

    // All usable item slots (tools area)
    private static final int TOOL_ZONE_MAX = 44; // slots 0–43 are "content area"

    private final SeriaFarmPlugin plugin;

    public WateringToolsMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory open(Player player) {
        Inventory inv = plugin.getGuiManager().createInventory("watering-tools-menu", null);

        // Load existing tools from config into slots
        List<WateringToolManager.WateringTool> tools = plugin.getWateringToolManager().getAllTools();
        int slot = 0;
        for (WateringToolManager.WateringTool tool : tools) {
            // Find next available non-border slot
            while (slot < TOOL_ZONE_MAX) {
                if (isBorder(slot)) { slot++; continue; }
                ItemStack existing = inv.getItem(slot);
                if (existing == null || existing.getType() == Material.AIR) break;
                slot++;
            }
            if (slot >= TOOL_ZONE_MAX) break;

            ItemStack item = plugin.getHookManager().getItem(tool.identifier());
            if (item == null || item.getType() == Material.AIR) item = new ItemStack(Material.STONE);
            item.setAmount(1);
            applyToolLore(item, tool.perUse(), tool.capacity(), tool.emptyItem());
            LocalizedName.set(item, buildTag(tool));
            inv.setItem(slot, item);
            slot++;
        }

        return inv;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("watering-tools-menu")) return;

        // Always cancel — we handle item movement manually
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        int invSize = event.getInventory().getSize(); // 54
        ItemStack clicked = event.getCurrentItem();

        // ─── Clicks inside the GUI ────────────────────────────────────────
        if (rawSlot >= 0 && rawSlot < invSize) {
            ItemStack metaItem = event.getInventory().getItem(rawSlot);
            String action = metaItem != null ? LocalizedName.get(metaItem) : null;

            // Action buttons (save, cancel, info)
            if ("save".equals(action)) {
                saveTools(player, event.getInventory());
                player.openInventory(new MainMenu(plugin).mainmenu(player));
                return;
            }
            if ("cancel".equals(action)) {
                player.openInventory(new MainMenu(plugin).mainmenu(player));
                return;
            }
            if ("info".equals(action)) return;

            // Content zone: existing tool slots
            if (rawSlot < TOOL_ZONE_MAX && !isBorder(rawSlot) && metaItem != null && !metaItem.getType().isAir()) {
                if (event.isRightClick()) {
                    // Right-click = remove
                    event.getInventory().setItem(rawSlot, null);
                } else {
                    // Left-click = edit this tool
                    if (action != null && action.startsWith("tool:")) {
                        openEditDialog(player, event.getInventory(), rawSlot, action);
                    }
                }
            }
            return;
        }

        // ─── Clicks in PLAYER inventory (rawSlot >= invSize) ─────────────
        if (rawSlot >= invSize && clicked != null && !clicked.getType().isAir()) {
            addTool(event.getInventory(), clicked);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private void addTool(Inventory inv, ItemStack item) {
        String identifier = plugin.getHookManager().getItemIdentifier(item);
        if (identifier == null || identifier.isEmpty()) identifier = item.getType().name();

        // Check if already in the menu
        for (int i = 0; i < TOOL_ZONE_MAX; i++) {
            if (isBorder(i)) continue;
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) continue;
            String tag = LocalizedName.get(existing);
            if (tag != null && tag.startsWith("tool:") && tag.contains(identifier)) return; // already added
        }

        // Find free slot
        for (int i = 0; i < TOOL_ZONE_MAX; i++) {
            if (isBorder(i)) continue;
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                WateringToolManager.WateringTool defaults = new WateringToolManager.WateringTool(
                        identifier, identifier, 3, 1, "BUCKET"
                );
                ItemStack display = item.clone();
                display.setAmount(1);
                applyToolLore(display, defaults.perUse(), defaults.capacity(), defaults.emptyItem());
                LocalizedName.set(display, buildTag(defaults));
                inv.setItem(i, display);
                return;
            }
        }
    }

    private void openEditDialog(Player player, Inventory inv, int slot, String currentTag) {
        // Parse tag: tool:<identifier>:<perUse>:<capacity>:<emptyItem>
        String[] parts = currentTag.split(":", 5);
        String identifier = parts.length > 1 ? parts[1] : "";
        String currentPerUse = parts.length > 2 ? parts[2] : "3";
        String currentCap = parts.length > 3 ? parts[3] : "1";
        String currentEmpty = parts.length > 4 ? parts[4] : "BUCKET";

        player.closeInventory();
        ChatInputListener.requestInput(player,
            "Per-Use (" + identifier + ")",
            "Berapa level air ditambah per klik? Sekarang: " + currentPerUse,
            perUseInput -> {
                int perUse;
                try { perUse = Integer.parseInt(perUseInput.trim()); } catch (Exception e) { perUse = 3; }
                final int pu = perUse;
                ChatInputListener.requestInput(player, "Capacity",
                    "Kapasitas max sebelum kosong? Sekarang: " + currentCap,
                    capInput -> {
                        int cap;
                        try { cap = Integer.parseInt(capInput.trim()); } catch (Exception e) { cap = 1; }
                        final int c = cap;
                        ChatInputListener.requestInput(player, "Empty-Item",
                            "Item saat kosong? (misal: BUCKET atau mi:MATERIAL:CAN_EMPTY) Sekarang: " + currentEmpty,
                            emptyInput -> {
                                // Rebuild the slot item
                                WateringToolManager.WateringTool updated =
                                    new WateringToolManager.WateringTool(identifier, identifier, pu, c, emptyInput.trim());
                                // But we need to update the in-flight inventory too
                                ItemStack existing = inv.getItem(slot);
                                if (existing != null) {
                                    applyToolLore(existing, pu, c, emptyInput.trim());
                                    LocalizedName.set(existing, buildTag(updated));
                                    inv.setItem(slot, existing);
                                }
                                // Save immediately
                                saveTools(player, inv);
                                player.openInventory(open(player));
                                plugin.getConfigManager().sendPrefixedMessage(player, "&aTool diperbarui!");
                            }, () -> player.openInventory(open(player)));
                    }, () -> player.openInventory(open(player)));
            }, () -> player.openInventory(open(player)));
    }

    private void saveTools(Player player, Inventory inv) {
        List<WateringToolManager.WateringTool> tools = new ArrayList<>();
        for (int i = 0; i < TOOL_ZONE_MAX; i++) {
            if (isBorder(i)) continue;
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            String tag = LocalizedName.get(item);
            if (tag == null || !tag.startsWith("tool:")) {
                // Newly dragged item without prior config — assign defaults
                String id = plugin.getHookManager().getItemIdentifier(item);
                if (id == null) id = item.getType().name();
                tools.add(new WateringToolManager.WateringTool(id, id, 3, 1, "BUCKET"));
            } else {
                String[] parts = tag.split(":", 5);
                String identifier = parts.length > 1 ? parts[1] : item.getType().name();
                int perUse = parts.length > 2 ? parseInt(parts[2], 3) : 3;
                int capacity = parts.length > 3 ? parseInt(parts[3], 1) : 1;
                String emptyItem = parts.length > 4 ? parts[4] : "BUCKET";
                tools.add(new WateringToolManager.WateringTool(identifier, identifier, perUse, capacity, emptyItem));
            }
        }
        plugin.getWateringToolManager().saveTools(tools);
    }

    private void applyToolLore(ItemStack item, int perUse, int capacity, String emptyItem) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("§7Per-Use: §f" + perUse));
        lore.add(net.kyori.adventure.text.Component.text("§7Capacity: §f" + capacity));
        lore.add(net.kyori.adventure.text.Component.text("§7Empty-Item: §f" + emptyItem));
        lore.add(net.kyori.adventure.text.Component.empty());
        lore.add(net.kyori.adventure.text.Component.text("§eL-Click §7to edit  §cR-Click §7to remove"));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private String buildTag(WateringToolManager.WateringTool t) {
        return "tool:" + t.identifier() + ":" + t.perUse() + ":" + t.capacity() + ":" + t.emptyItem();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private boolean isBorder(int slot) {
        int[] cancelledSlots = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int s : cancelledSlots) {
            if (s == slot) return true;
        }
        return false;
    }
}
