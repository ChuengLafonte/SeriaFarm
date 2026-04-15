package id.seria.farm.inventory.watering;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.maintree.GlobalBlockEditMenu;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.managers.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
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
 * Drag & Drop GUI for configuring valid soil blocks per crop.
 *
 * Built manually (same as RequiredToolsMenu) — NOT dependent on gui.yml key.
 *
 * UX:
 *   - Click item from player inventory → add soil block
 *   - Right-click entry in menu        → remove
 *   - Click SAVE                       → write to crops.yml
 *   - Click CANCEL                     → back to plant editor
 */
public class SoilRequirementMenu implements Listener {

    private static final int INV_SIZE = 54;
    private static final int CONTENT_MAX = 44; // slots 0-43 are content
    // Fixed button slots
    private static final int SLOT_SAVE   = 48;
    private static final int SLOT_INFO   = 49;
    private static final int SLOT_CANCEL = 50;
    private static final int SLOT_META   = 53; // hidden matName marker

    private final SeriaFarmPlugin plugin;

    public SoilRequirementMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String matName) {
        String section = matName.contains(":") ? matName.split(":")[0] : "global";
        String cropKey = matName.contains(":") ? matName.split(":")[1] : matName;
        String path = "crops." + section + "." + cropKey;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        List<String> soils = config.getStringList(path + ".soil");

        // Build inventory manually — no GuiManager dependency
        GuiManager.MenuHolder holder = new GuiManager.MenuHolder("soil-requirement-menu");
        Inventory inv = Bukkit.createInventory(holder, INV_SIZE,
                StaticColors.getHexMsg("&8&l| &8Soil Requirement"));

        // Border filler
        ItemStack border = InvUtils.createItemStacks(Material.BLACK_STAINED_GLASS_PANE, " ");
        int[] borderSlots = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,51,52};
        for (int s : borderSlots) inv.setItem(s, border);

        // Action buttons
        ItemStack saveItem = InvUtils.createItemStacks(Material.LIME_STAINED_GLASS_PANE,
                "&a&lSAVE & EXIT", "&7Save soil block list");
        LocalizedName.set(saveItem, "save");
        inv.setItem(SLOT_SAVE, saveItem);

        ItemStack cancelItem = InvUtils.createItemStacks(Material.RED_STAINED_GLASS_PANE,
                "&c&lCANCEL", "&7Discard changes");
        LocalizedName.set(cancelItem, "cancel");
        inv.setItem(SLOT_CANCEL, cancelItem);

        inv.setItem(SLOT_INFO, InvUtils.createItemStacks(Material.FARMLAND,
                "&bSoil Blocks",
                "&7Click items from your inventory",
                "&7to add valid soil blocks here.",
                "&7R-Click a placed item to remove it."));

        // Hidden metadata item (slot 53)
        ItemStack meta = new ItemStack(Material.PAPER);
        LocalizedName.set(meta, matName);
        inv.setItem(SLOT_META, meta);

        // Populate existing soil entries
        int slotIdx = 0;
        for (String soilId : soils) {
            // Find next free content slot
            while (slotIdx < CONTENT_MAX && inv.getItem(slotIdx) != null
                    && inv.getItem(slotIdx).getType() != Material.AIR) {
                slotIdx++;
            }
            if (slotIdx >= CONTENT_MAX) break;

            ItemStack display = plugin.getHookManager().getItem(soilId);
            if (display == null || display.getType() == Material.AIR || display.getType() == Material.STONE) {
                display = InvUtils.createItemStacks(Material.FARMLAND, "&f" + soilId,
                        "&7ID: &f" + soilId, "", "&cR-Click to remove");
            } else {
                applySoilLore(display, soilId);
            }
            LocalizedName.set(display, "SOIL:" + soilId);
            inv.setItem(slotIdx, display);
            slotIdx++;
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("soil-requirement-menu")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        // Read matName from hidden slot
        ItemStack metaItem = event.getInventory().getItem(SLOT_META);
        if (metaItem == null) return;
        String matName = LocalizedName.get(metaItem);
        if (matName == null) return;
        String section = matName.contains(":") ? matName.split(":")[0] : "global";
        String cropKey = matName.contains(":") ? matName.split(":")[1] : matName;
        String path = "crops." + section + "." + cropKey;

        // ── Clicks inside the GUI ────────────────────────────────────────
        if (rawSlot >= 0 && rawSlot < INV_SIZE) {
            if (rawSlot == SLOT_SAVE) {
                saveData(player, event.getInventory(), path, matName);
                return;
            }
            if (rawSlot == SLOT_CANCEL) {
                player.openInventory(new GlobalBlockEditMenu(plugin).open(player, matName));
                return;
            }
            // Content zone: right-click to remove
            if (rawSlot < CONTENT_MAX && clicked != null && !clicked.getType().isAir()) {
                if (event.isRightClick()) {
                    event.getInventory().setItem(rawSlot, null);
                }
            }
            return;
        }

        // ── Player inventory click → add soil block ──────────────────────
        if (rawSlot >= INV_SIZE && clicked != null && !clicked.getType().isAir()) {
            addSoil(event.getInventory(), clicked);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private void addSoil(Inventory inv, ItemStack item) {
        String soilId = plugin.getHookManager().getItemIdentifier(item);
        if (soilId == null || soilId.isEmpty()) soilId = item.getType().name();

        // Deduplication
        for (int i = 0; i < CONTENT_MAX; i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) continue;
            String tag = LocalizedName.get(existing);
            if (tag != null && tag.equalsIgnoreCase("SOIL:" + soilId)) return;
        }

        // Insert into first free content slot
        for (int i = 0; i < CONTENT_MAX; i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                ItemStack display = item.clone();
                display.setAmount(1);
                applySoilLore(display, soilId);
                LocalizedName.set(display, "SOIL:" + soilId);
                inv.setItem(i, display);
                return;
            }
        }
    }

    private void saveData(Player player, Inventory inv, String path, String matName) {
        List<String> soils = new ArrayList<>();
        for (int i = 0; i < CONTENT_MAX; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            String tag = LocalizedName.get(item);
            if (tag != null && tag.startsWith("SOIL:")) {
                soils.add(tag.substring(5));
            }
        }
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        config.set(path + ".soil", soils);
        plugin.getConfigManager().saveConfig("crops.yml");
        plugin.getConfigManager().sendPrefixedMessage(player, "&aSoil updated! (" + soils.size() + " blocks)");
        player.openInventory(new GlobalBlockEditMenu(plugin).open(player, matName));
    }

    private void applySoilLore(ItemStack item, String soilId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(StaticColors.getHexMsg("&7ID: &f" + soilId));
        lore.add(Component.empty());
        lore.add(Component.text("R-Click to remove", NamedTextColor.RED));
        meta.lore(lore);
        item.setItemMeta(meta);
    }
}
