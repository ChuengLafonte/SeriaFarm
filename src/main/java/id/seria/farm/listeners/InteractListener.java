package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class InteractListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public InteractListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        Player player = event.getPlayer();
        
        // Handle Harvesting (LiteFarm style)
        if (plugin.getFarmManager().getPlant(block.getLocation()) != null) {
            handleHarvest(player, block);
            event.setCancelled(true);
        }
    }

    private void handleHarvest(Player player, Block block) {
        // Check if fully grown, distribute rewards, reset growth stage
    }
}
