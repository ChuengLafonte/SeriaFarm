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

    public GlobalBlocksMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public int getPage() { return page; }

    public Inventory blockmenu(Player player, int page) {
        this.page = page;
        YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%count%", String.valueOf(getGlobalCount(config)));
        placeholders.put("%page%", String.valueOf(page));

        Inventory inventory = plugin.getGuiManager().createInventory("catalog-menu", placeholders);

        ConfigurationSection globalSection = config.getConfigurationSection("crops.global");
        List<String> materials = new ArrayList<>();
        if (globalSection != null) {
            globalSection.getKeys(false).forEach(k -> materials.add("global:" + k));
        }
        materials.sort(Comparator.naturalOrder());

        // Fill dynamic content in available slots
        int slot = 0;
        List<String> pageItems = PageUtil.getpageitems(materials, page, 21);
        
        for (String matKey : pageItems) {
            // Find next empty slot (AIR/Null) that isn't a filler or specific item
            while (slot < inventory.getSize() && inventory.getItem(slot) != null && inventory.getItem(slot).getType() != Material.AIR) {
                slot++;
            }
            if (slot >= inventory.getSize()) break;

            String materialKey = matKey.split(":")[1];
            String path = "crops.global." + materialKey;
            
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

            ItemStack icon = plugin.getHookManager().getItem(materialKey);
            if (icon.getType() == Material.STONE && !materialKey.equalsIgnoreCase("STONE")) {
                icon = new ItemStack(Material.BARRIER);
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

    private int getGlobalCount(YamlConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("crops.global");
        return sec == null ? 0 : sec.getKeys(false).size();
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
        
        if (action.equals("open_add_blocks")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.addtree.AddBlocksMenu().addblocks_menu(player, "global")));
            return;
        }
        if (action.equals("prev_page")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, Math.max(1, page - 1))));
            return;
        }
        if (action.equals("next_page")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(blockmenu(player, page + 1)));
            return;
        }
        if (action.equals("close_menu")) {
            player.closeInventory();
            return;
        }

        // Logic for blocks (action will be global:BLOCK)
        if (action.startsWith("global:")) {
            if (!player.hasPermission("seriafarm.admin")) return;
            YamlConfiguration config = (YamlConfiguration) plugin.getConfigManager().getConfig("crops.yml");
            
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                String subKey = action.split(":")[1];
                config.set("crops.global." + subKey, null);
                plugin.getConfigManager().saveConfig("crops.yml");
                plugin.getConfigManager().sendPrefixedMessage(player, "&cDeleted Global &f" + subKey);
                player.openInventory(blockmenu(player, page));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new id.seria.farm.inventory.maintree.GlobalBlockEditMenu(plugin).open(player, action)));
            }
        }
    }
}

