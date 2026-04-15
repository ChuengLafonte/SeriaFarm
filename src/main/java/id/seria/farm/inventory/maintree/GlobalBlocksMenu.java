package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.LocalizedName;
import id.seria.farm.inventory.utils.PageUtil;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import id.seria.farm.managers.GuiManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class GlobalBlocksMenu implements Listener {
    private final SeriaFarmPlugin plugin;
    private int page;
    private String type = "global";

    public GlobalBlocksMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public int getPage() { return page; }

    public Inventory blockmenu(Player player, int page, String type) {
        this.page = page;
        this.type = type;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        
        Map<String, String> placeholders = new HashMap<>();

        ConfigurationSection targetSection = config.getConfigurationSection("crops." + type);
        List<String> materials = new ArrayList<>();
        if (targetSection != null) targetSection.getKeys(false).forEach(k -> materials.add(type + ":" + k));
        materials.sort(Comparator.naturalOrder());

        placeholders.put("%count%", String.valueOf(materials.size()));
        placeholders.put("%page%", String.valueOf(page));

        Inventory inventory = plugin.getGuiManager().createInventory("catalog-menu", placeholders);

        // Fill dynamic content in available slots
        int slot = 0;
        List<String> pageItems = PageUtil.getpageitems(materials, page, 21);
        
        for (String matKey : pageItems) {
            // Find next empty slot (AIR/Null) that isn't a filler or specific item
            while (slot < inventory.getSize() && inventory.getItem(slot) != null && inventory.getItem(slot).getType() != Material.AIR) {
                slot++;
            }
            if (slot >= inventory.getSize()) break;

            // Resolve config path from prefix (global: or garden:)
            String[] parts = matKey.split(":", 2);
            String prefix = parts[0];           // "global" or "garden"
            String materialKey = parts[1];
            String path = "crops." + prefix + "." + materialKey;

            int xp = config.getInt(path + ".rewards.xp", 0);
            int delay = config.getInt(path + ".regen-delay", 20);
            ConfigurationSection rewards = config.getConfigurationSection(path + ".rewards");
            int dropsCount = (rewards != null) ? rewards.getKeys(false).size() : 0;
            if (rewards != null && rewards.contains("xp")) dropsCount--;

            String rarity = "&#2ecc71Common";
            if (delay >= 120) rarity = "&#f1c40fLegendary";
            else if (delay >= 60) rarity = "&#9b59b6Epic";
            else if (delay >= 30) rarity = "&#3498dbRare";

            int popularityScore = Math.abs(materialKey.hashCode() % 100);
            String popularity = popularityScore > 80 ? "&aHighly Popular" : (popularityScore > 40 ? "&eTrending" : "&7Stable");

            // ── Icon resolution (priority: material field → item remap → seed-item → fallback WHEAT)
            String materialStr = config.getString(path + ".material", materialKey).toUpperCase();
            Material vanillaMat = Material.matchMaterial(materialStr);
            // Some crop materials are block-only (POTATOES, CARROTS, BEETROOTS) — remap to item equivalent
            if (vanillaMat != null && !vanillaMat.isItem()) {
                vanillaMat = switch (vanillaMat.name()) {
                    case "POTATOES"    -> Material.POTATO;
                    case "CARROTS"     -> Material.CARROT;
                    case "BEETROOTS"   -> Material.BEETROOT;
                    case "COCOA_BEANS" -> Material.COCOA_BEANS;
                    case "NETHER_WART_BLOCK" -> Material.NETHER_WART;
                    default            -> null; // unknown block-only material
                };
            }
            ItemStack icon;
            if (vanillaMat != null && vanillaMat.isItem()) {
                icon = new ItemStack(vanillaMat);
            } else {
                // Try seed-item via HookManager (MMOItems / ItemsAdder / vanilla)
                String seedItemId = config.getString(path + ".seed-item", null);
                icon = seedItemId != null ? plugin.getHookManager().getItem(seedItemId) : null;
                if (icon == null || icon.getType() == Material.AIR || icon.getType() == Material.STONE) {
                    icon = new ItemStack(Material.WHEAT); // safe visual fallback
                }
            }
            List<Object> lore = new ArrayList<>(Arrays.asList(
                "&8World Species • Wiki",
                "",
                StaticColors.getHexMsg("&#E7CBB3Rarity: " + rarity),
                StaticColors.getHexMsg("&#E7CBB3Popularity: " + popularity),
                "",
                StaticColors.getHexMsg("&#E7CBB3Growth Duration: &f" + delay + "s"),
                StaticColors.getHexMsg("&#E7CBB3XP Reward: &f" + xp),
                StaticColors.getHexMsg("&#E7CBB3Unique Drops: &f" + Math.max(0, dropsCount)),
                ""
            ));

            if (player.hasPermission("seriafarm.admin")) {
                lore.add("&eClick to Edit Settings");
                lore.add("&cShift-Click to Remove");
            } else {
                lore.add("&7Discover this plant in the fields!");
            }

            ConfigurationSection skills = config.getConfigurationSection(path + ".requirements.skills");
            if (skills != null && !skills.getKeys(false).isEmpty()) {
                lore.add("");
                lore.add("&d&lRequirements:");
                for (String key : skills.getKeys(false)) {
                    String skill = skills.getString(key + ".skill", "Farming");
                    String op = skills.getString(key + ".operator", ">=");
                    int level = skills.getInt(key + ".level", 0);
                    lore.add("&7- " + skill + " " + op + " " + level);
                }
            }

            String displayName = config.getString(path + ".display-name", materialKey.replace("_", " "));
            ItemStack item = InvUtils.applyMeta(icon, 
                StaticColors.getHexMsg("&#2ecc71&l" + displayName), 
                lore.toArray());
            
            LocalizedName.set(item, matKey); // This matKey is used for logic, NOT for Action Key
            inventory.setItem(slot, item);
            slot++;
        }

        return inventory;
    }



    @EventHandler
    public void oninvcclick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiManager.MenuHolder holder)) return;
        if (!holder.getMenuKey().equals("catalog-menu")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String action = LocalizedName.get(clicked);
        if (action == null) return; // border pane or item with no action tag
        
        if (action.equals("open_add_blocks")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.addtree.AddBlocksMenu().addblocks_menu(player, type)));
            return;
        }
        if (action.equals("prev_page")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, Math.max(1, page - 1), type)));
            return;
        }
        if (action.equals("next_page")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, page + 1, type)));
            return;
        }
        if (action.equals("close_menu")) {
            player.closeInventory();
            return;
        }

        // Logic for crop items — action is "global:key" or "garden:key"
        if (action.startsWith("global:") || action.startsWith("garden:")) {
            if (!player.hasPermission("seriafarm.admin")) return;
            YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
            String[] ap = action.split(":", 2);
            String section = ap[0]; // "global" or "garden"
            String subKey  = ap[1];

            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                config.set("crops." + section + "." + subKey, null);
                plugin.getConfigManager().saveConfig("crops.yml");
                plugin.getConfigManager().sendPrefixedMessage(player, "&cDeleted &f" + section + "/" + subKey);
                player.openInventory(blockmenu(player, page, type));
            } else {
                // Pass full "global:key" or "garden:key" as matName —
                // GlobalBlockEditMenu resolves the config path from this prefix.
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, action)));
            }
        }
    }
}

