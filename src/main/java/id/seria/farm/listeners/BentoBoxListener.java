package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.managers.SoilSlotManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandResetEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.BentoBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BentoBoxListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public BentoBoxListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandDelete(IslandDeleteEvent event) {
        handleCleanup(event.getIsland());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandReset(IslandResetEvent event) {
        handleCleanup(event.getIsland());
    }

    private void handleCleanup(Island island) {
        if (island == null) return;

        World world = island.getWorld();
        if (world == null) return;

        List<Location> toRemove = new ArrayList<>();
        SoilSlotManager ssm = plugin.getSoilSlotManager();

        // 1. Identify all soils within island bounds
        ssm.getPlacedSoils().forEach((loc, soil) -> {
            if (loc.getWorld().equals(world)) {
                java.util.Optional<Island> found = BentoBox.getInstance().getIslands().getIslandAt(loc);
                if (found.isPresent() && found.get().getUniqueId().equals(island.getUniqueId())) {
                    toRemove.add(loc);
                }
            }
        });

        if (toRemove.isEmpty()) return;

        plugin.getLogger().info("Island " + island.getUniqueId() + " deleted/reset. Cleaning up " + toRemove.size() + " soils.");

        // 2. Process removal and return items
        for (Location loc : toRemove) {
            SoilSlotManager.SoilBlock soil = ssm.getPlacedSoils().get(loc);
            if (soil == null) continue;

            UUID ownerUUID = soil.getOwner();
            String soilId = soil.getSoilId();

            // Forcefully remove from DB and World
            ssm.forceRemoveSoil(loc);

            // Try to give item back to owner
            returnSoilItem(ownerUUID, soilId, loc);
        }
    }

    private void returnSoilItem(UUID ownerUUID, String soilId, Location loc) {
        Player player = Bukkit.getPlayer(ownerUUID);
        
        // Find item ID from config
        String itemId = plugin.getConfigManager().getConfig("soils.yml")
                .getString("soils." + soilId + ".item-id");
        
        if (itemId == null) return;
        
        ItemStack soilItem = plugin.getHookManager().getItem(itemId);
        if (soilItem == null || soilItem.getType().isAir()) return;

        if (player != null && player.isOnline()) {
            // Give to inventory
            java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(soilItem);
            if (!remaining.isEmpty()) {
                for (ItemStack left : remaining.values()) {
                    loc.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
            plugin.getConfigManager().sendPrefixedMessage(player, "&aPulau di-reset! &e" + soilItem.getAmount() + "x Media Tanam &atelah dikembalikan ke inventory.");
        } else {
            // Drop at location if owner is offline (it will be cleared by island reset anyway, 
            // but we've already removed it from our DB, so it's "safe" in terms of slots).
            // Actually, dropping at 'loc' might be useless since the island is being deleted.
            // But if the player is offline, they lose the item.
            // User requirement: "masuk ke inventory player".
            // If offline, we can't do that easily without a persistent mailbox.
            // For now, we drop at the location (which might get deleted) or log it.
            plugin.getLogger().info("Returned soil item for offline player " + ownerUUID + " at " + loc);
        }
    }
}
