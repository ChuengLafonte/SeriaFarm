package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.listeners.WandListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.time.Duration;
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

    public void showGrowthInfo(Player player, id.seria.farm.models.RegenBlock regen) {
        String region = plugin.getRegenManager().getRegionAt(regen.getLocation());
        
        // Debug Logging
        Bukkit.getLogger().info("[SeriaFarm Debug] showGrowthInfo called for " + regen.getLocation() + " | Region: " + region);

        // Check if inside a region
        if (region != null) {
            return; // No progress display for regions as requested
        }

        long now = System.currentTimeMillis();
        long remainingSec = Math.max(0, (regen.getRestoreTime() - now) / 1000);
        long minutes = remainingSec / 60;
        long seconds = remainingSec % 60;
        String timeStr = String.format("%02dm:%02ds", minutes, seconds);

        // Title format using requested HEX color #54F47F
        Component title = StaticColors.getHexMsg("&#54F47FTime " + timeStr);
        
        // Subtitle: Material display name
        Component subtitle = getMaterialDisplayName(regen.getMaterialKey());

        // Send Title and Subtitle (Stay for 1 second, no fade for snappiness)
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(250))));
    }

    private Component getMaterialDisplayName(String materialKey) {
        if (materialKey == null) return Component.text("Unknown");
        
        // 1. Check config (materials.yml) for custom display-name
        org.bukkit.configuration.ConfigurationSection config = plugin.getConfigManager().getConfig("materials.yml").getConfigurationSection("blocks." + materialKey);
        if (config != null && config.contains("display-name")) {
            return StaticColors.getHexMsg(config.getString("display-name"));
        }

        // 2. Extract identifier for HookManager (e.g., global.wheat -> wheat)
        String id = materialKey;
        if (id.contains(".")) {
            id = id.substring(id.lastIndexOf(".") + 1);
        }

        // 3. Check HookManager for custom items (MMOItems, ItemsAdder, etc.)
        ItemStack item = plugin.getHookManager().getItem(id);
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName();
        }

        // 4. Fallback for vanilla: Prettify key
        String name = id.replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return Component.text(sb.toString().trim());
    }
}
