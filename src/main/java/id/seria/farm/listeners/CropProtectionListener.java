package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityInteractEvent;

public class CropProtectionListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public CropProtectionListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Anti-Trample ──────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) {
                if (plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.crop-protection.anti-trample", true)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityTrample(EntityInteractEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND) {
            if (plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.crop-protection.anti-trample", true)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── Prevent FARMLAND → DIRT (hydration loss / other entity changes) ──

    /**
     * Prevents custom soil blocks (base material FARMLAND) from fading to DIRT
     * when there is no nearby water source.
     * Called when a FARMLAND block would naturally dry out.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFarmlandFade(BlockFadeEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        if (event.getNewState().getType() != Material.DIRT) return;
        // Cancel drying if this farmland is a registered custom soil
        if (plugin.getCustomPlantManager().isValidSoil(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancels any entity-driven FARMLAND → DIRT conversion
     * (e.g. mobs landing on it) when the block is a custom soil.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeFarmland(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        if (event.getTo() != Material.DIRT) return;
        if (plugin.getCustomPlantManager().isValidSoil(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
