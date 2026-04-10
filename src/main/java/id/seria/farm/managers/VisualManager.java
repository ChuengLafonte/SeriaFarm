package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.listeners.WandListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VisualManager {

    private final SeriaFarmPlugin plugin;
    private final Map<UUID, String> focusedRegion = new HashMap<>();

    public VisualManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
        startVisualizer();
    }

    private void startVisualizer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    Location p1 = WandListener.Mpos1.get(uuid);
                    Location p2 = WandListener.Mpos2.get(uuid);

                    if (p1 != null) {
                        spawnPointParticle(player, p1);
                    }
                    if (p2 != null) {
                        spawnPointParticle(player, p2);
                    }
                    if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
                        drawCuboid(player, p1, p2);
                    }

                    String regionId = focusedRegion.get(uuid);
                    if (regionId != null) {
                        drawRegion(player, regionId);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public void setFocusedRegion(Player player, String regionName) {
        if (regionName == null) {
            focusedRegion.remove(player.getUniqueId());
        } else {
            focusedRegion.put(player.getUniqueId(), regionName);
        }
    }

    private void drawRegion(Player player, String regionName) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getConfig("regions.yml");
        String path = "regions." + regionName + ".";
        String p1Str = config.getString(path + "pos1");
        String p2Str = config.getString(path + "pos2");

        if (p1Str != null && p2Str != null) {
            Location l1 = deserializeLoc(p1Str);
            Location l2 = deserializeLoc(p2Str);
            if (l1 != null && l2 != null) {
                drawCuboid(player, l1, l2);
            }
        }
    }

    private Location deserializeLoc(String str) {
        try {
            String[] p = str.split(";");
            if (p.length >= 4) {
                return new Location(Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void spawnPointParticle(Player player, Location loc) {
        player.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.1, 0.5), 5, 0.1, 0.1, 0.1, 0.05);
    }

    private void drawCuboid(Player player, Location l1, Location l2) {
        double minX = Math.min(l1.getX(), l2.getX());
        double minY = Math.min(l1.getY(), l2.getY());
        double minZ = Math.min(l1.getZ(), l2.getZ());
        double maxX = Math.max(l1.getX(), l2.getX()) + 1;
        double maxY = Math.max(l1.getY(), l2.getY()) + 1;
        double maxZ = Math.max(l1.getZ(), l2.getZ()) + 1;

        // Draw edges
        for (double x = minX; x <= maxX; x += 0.5) {
            spawnEdgeParticle(player, new Location(l1.getWorld(), x, minY, minZ));
            spawnEdgeParticle(player, new Location(l1.getWorld(), x, maxY, minZ));
            spawnEdgeParticle(player, new Location(l1.getWorld(), x, minY, maxZ));
            spawnEdgeParticle(player, new Location(l1.getWorld(), x, maxY, maxZ));
        }
        for (double y = minY; y <= maxY; y += 0.5) {
            spawnEdgeParticle(player, new Location(l1.getWorld(), minX, y, minZ));
            spawnEdgeParticle(player, new Location(l1.getWorld(), maxX, y, minZ));
            spawnEdgeParticle(player, new Location(l1.getWorld(), minX, y, maxZ));
            spawnEdgeParticle(player, new Location(l1.getWorld(), maxX, y, maxZ));
        }
        for (double z = minZ; z <= maxZ; z += 0.5) {
            spawnEdgeParticle(player, new Location(l1.getWorld(), minX, minY, z));
            spawnEdgeParticle(player, new Location(l1.getWorld(), maxX, minY, z));
            spawnEdgeParticle(player, new Location(l1.getWorld(), minX, maxY, z));
            spawnEdgeParticle(player, new Location(l1.getWorld(), maxX, maxY, z));
        }
    }

    private void spawnEdgeParticle(Player player, Location loc) {
        player.spawnParticle(Particle.COMPOSTER, loc, 1, 0, 0, 0, 0);
    }
}
