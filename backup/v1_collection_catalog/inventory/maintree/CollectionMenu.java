package id.seria.farm.inventory.maintree;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CollectionMenu implements Listener {

    private final SeriaFarmPlugin plugin;

    public CollectionMenu(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, StaticColors.getHexMsg("&8Collections"));
        
        // Filler
        ItemStack filler = InvUtils.createItemStacks(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Farming Category
        inv.setItem(13, InvUtils.createItemStacks(Material.GOLDEN_HOE, "&aFarming Collections", 
            "&7View all your farming progress.", "", "&eClick to view!"));

        player.openInventory(inv);
    }

    public void openFarmingMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, StaticColors.getHexMsg("&8Farming Collections"));
        
        // Filler
        ItemStack filler = InvUtils.createItemStacks(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Crops from crops.yml
        addCropItem(inv, 10, Material.WHEAT, "wheat", player);
        addCropItem(inv, 11, Material.CARROT, "carrots", player);
        addCropItem(inv, 12, Material.POTATO, "potatoes", player);
        addCropItem(inv, 13, Material.BEETROOT, "beetroots", player);
        addCropItem(inv, 14, Material.NETHER_WART, "nether_wart", player);
        addCropItem(inv, 15, Material.COCOA_BEANS, "cocoa", player);
        addCropItem(inv, 16, Material.MELON, "melon", player);
        addCropItem(inv, 19, Material.PUMPKIN, "pumpkin", player);
        addCropItem(inv, 20, Material.SWEET_BERRIES, "sweet_berry_bush", player);

        // Back button
        inv.setItem(49, InvUtils.createItemStacks(Material.ARROW, "&aBack", "&7Return to main menu"));

        player.openInventory(inv);
    }

    private void addCropItem(Inventory inv, int slot, Material mat, String key, Player player) {
        long amount = plugin.getCollectionManager().getCollectionAmount(player.getUniqueId(), mat.name().toLowerCase());
        int tier = plugin.getCollectionManager().getTier(player.getUniqueId(), mat.name().toLowerCase());
        
        String name = "&a" + InvUtils.getFriendlyName(mat);
        inv.setItem(slot, InvUtils.createItemStacks(mat, name, 
            "&7Total Harvested: &f" + amount,
            "&7Current Tier: &e" + (tier > 0 ? "Tier " + tier : "None"),
            "",
            "&eClick to view tiers!"));
    }

    public void openTierMenu(Player player, Material cropMat) {
        String cropName = InvUtils.getFriendlyName(cropMat);
        Inventory inv = Bukkit.createInventory(null, 54, StaticColors.getHexMsg("&8" + cropName + " Collection"));
        
        // Filler
        ItemStack filler = InvUtils.createItemStacks(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        UUID uuid = player.getUniqueId();
        String cropKey = cropMat.name().toLowerCase();
        long amount = plugin.getCollectionManager().getCollectionAmount(uuid, cropKey);

        // Tiers 1-11
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
        for (int i = 1; i <= 11; i++) {
            long req = plugin.getCollectionManager().getRequirement(i);
            boolean unlocked = amount >= req;
            
            Material icon = unlocked ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            String tierName = "&e" + cropName + " " + (i == 1 ? "I" : i == 2 ? "II" : i == 3 ? "III" : i == 4 ? "IV" : i == 5 ? "V" : i == 6 ? "VI" : i == 7 ? "VII" : i == 8 ? "VIII" : i == 9 ? "IX" : i == 10 ? "X" : "XI");
            
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            
            // Progress Section
            int percent = (int) Math.min(100, (amount * 100) / req);
            lore.add("&7Progress: &e" + percent + "%");
            lore.add(getProgressBar(amount, req) + " &e" + amount + "/" + req);
            
            // Rewards Section
            lore.add(" ");
            lore.add("&7Rewards:");
            for (String reward : getRewards(cropKey, i)) {
                lore.add("  " + reward);
            }
            lore.add("&8+4 SkyBlock XP"); 
            
            lore.add(" ");
            lore.add("&eClick to view rewards!");

            inv.setItem(slots[i-1], InvUtils.createItemStacks(icon, tierName, lore.toArray()));
        }

        // Back button
        inv.setItem(49, InvUtils.createItemStacks(Material.ARROW, "&aBack", "&7Return to farming list"));

        player.openInventory(inv);
    }

    private String getProgressBar(long current, long goal) {
        int totalBars = 20;
        int filledBars = (int) Math.min(totalBars, (current * totalBars) / goal);
        StringBuilder bar = new StringBuilder("&a");
        for (int i = 0; i < filledBars; i++) bar.append("-");
        bar.append("&f");
        for (int i = filledBars; i < totalBars; i++) bar.append("-");
        return bar.toString();
    }

    private List<String> getRewards(String crop, int tier) {
        List<String> rewards = new ArrayList<>();
        if (crop.equals("wheat")) {
            if (tier == 1) rewards.add("&9Farmer Hoe V2 Recipe");
            if (tier == 2) rewards.add("&9Elder Farmer Hoe V2 Recipe");
            if (tier == 3) {
                rewards.add("&9Sacred Farmer Hoe V2 Recipe");
                rewards.add("&9Farm Suit Recipes");
            }
            if (tier == 9) rewards.add("&9Claustrophobic Hoe V2 Recipe");
        }
        // Add more crops as needed
        return rewards;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.contains("Collections") && !title.contains("Collection")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (title.equals("Collections")) {
            if (clicked.getType() == Material.GOLDEN_HOE) openFarmingMenu(player);
        } else if (title.equals("Farming Collections")) {
            if (clicked.getType() == Material.ARROW) {
                openMainMenu(player);
            } else {
                openTierMenu(player, clicked.getType());
            }
        } else if (title.contains("Collection")) {
            if (clicked.getType() == Material.ARROW) openFarmingMenu(player);
        }
    }
}
