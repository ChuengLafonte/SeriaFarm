package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SoilListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public SoilListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSoilInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        UUID owner = plugin.getSoilSlotManager().getOwner(block.getLocation());
        if (owner == null) return;

        Player player = event.getPlayer();
        // 1-Tap requirement: Left Click + Sneaking
        if (!player.isSneaking()) return;

        if (!owner.equals(player.getUniqueId()) && !player.hasPermission("seriafarm.admin")) {
            plugin.getConfigManager().sendPrefixedMessage(player, "&cIni bukan ladang milikmu!");
            return;
        }

        // Valid owner/admin removal
        String soilKey = plugin.getSoilSlotManager().getSoilId(block.getLocation());
        
        // 1. Instant break
        block.setType(Material.AIR);
        plugin.getSoilSlotManager().breakSoil(block.getLocation());

        // 2. Give custom item back
        if (soilKey != null) {
            // Resolve key to technical ID (mi:...)
            String actualId = plugin.getCustomPlantManager().getSoilItemId(soilKey);
            if (actualId == null) actualId = soilKey; // Fallback for legacy raw IDs in DB
            
            ItemStack item = plugin.getHookManager().getItem(actualId);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
            }
        }

        // 3. Cleanup plant above (holograms, etc)
        Block above = block.getRelative(0, 1, 0);
        if (plugin.getCustomPlantManager().isCustomPlant(above.getLocation())) {
            plugin.getCustomPlantManager().removePlant(above.getLocation());
            above.setType(Material.AIR);
            plugin.getHologramManager().hide(player);
        }

        // 4. Update Slot Notification
        plugin.getSoilSlotManager().sendNotification(player);
    }
}
