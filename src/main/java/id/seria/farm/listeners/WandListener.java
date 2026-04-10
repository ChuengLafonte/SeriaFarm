package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class WandListener implements Listener {

    public static final Map<UUID, Location> Mpos1 = new HashMap<>();
    public static final Map<UUID, Location> Mpos2 = new HashMap<>();

    @EventHandler
    public void OnClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR 
            && player.getInventory().getItemInMainHand().hasItemMeta()
            && player.getInventory().getItemInMainHand().getItemMeta().getDisplayName().equals(ChatColor.YELLOW + "REGEN WAND") 
            && player.getInventory().getItemInMainHand().getItemMeta().hasCustomModelData()
            && player.getInventory().getItemInMainHand().getItemMeta().getCustomModelData() == 20) {
            
            String prefix = SeriaFarmPlugin.getInstance().getConfig().getString("settings.prefix", "&6&lSeriaFarm &8»");

            if (event.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
                event.setCancelled(true);
                Location loc = Objects.requireNonNull(event.getClickedBlock()).getLocation();
                Mpos1.put(player.getUniqueId(), loc);
                player.sendMessage(StaticColors.getHexMsg(prefix) + " " + ChatColor.WHITE + "Pos1 =" + ChatColor.RED + "[" + ChatColor.YELLOW + "X=" + ChatColor.WHITE + loc.getBlockX() + ChatColor.RED + "," + ChatColor.YELLOW + "Y=" + ChatColor.WHITE + loc.getBlockY() + ChatColor.RED + "," + ChatColor.YELLOW + "Z=" + ChatColor.WHITE + loc.getBlockZ() + ChatColor.RED + "]");
            } else if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && event.getHand().equals(EquipmentSlot.HAND)) {
                event.setCancelled(true);
                Location loc = Objects.requireNonNull(event.getClickedBlock()).getLocation();
                Mpos2.put(player.getUniqueId(), loc);
                player.sendMessage(StaticColors.getHexMsg(prefix) + " " + ChatColor.WHITE + "Pos2 =" + ChatColor.RED + "[" + ChatColor.YELLOW + "X=" + ChatColor.WHITE + loc.getBlockX() + ChatColor.RED + "," + ChatColor.YELLOW + "Y=" + ChatColor.WHITE + loc.getBlockY() + ChatColor.RED + "," + ChatColor.YELLOW + "Z=" + ChatColor.WHITE + loc.getBlockZ() + ChatColor.RED + "]");
            }
        }
    }
}
