package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityInteractEvent;

public class CropProtectionListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public CropProtectionListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) {
                // Check if plugin is enabled
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
}
