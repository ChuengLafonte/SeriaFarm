package id.seria.farm.managers;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.models.CustomPlantState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player TextDisplay holograms shown above custom plants
 * when the player holds a watering tool and aims at a custom plant.
 *
 * Format:
 *   §e✦ Shining Wheat
 *   §b💧 [|||||||···]
 *
 * Only one hologram per player at a time. Reuses the same entity via update.
 */
public class CustomPlantHologramManager {

    private static final int BAR_LENGTH = 10;

    private final SeriaFarmPlugin plugin;
    // One active hologram entity per player
    private final Map<UUID, TextDisplay> activeHolograms = new ConcurrentHashMap<>();

    public CustomPlantHologramManager(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Show or update hologram above the given location for the player.
     */
    public void show(Player player, Location plantLoc, CustomPlantState state) {
        String cropKey = state.getCropKey();
        boolean inGarden = plugin.getConfigManager().getConfig("crops.yml").contains("crops.garden." + cropKey);
        String path = inGarden ? "crops.garden." + cropKey : "crops.global." + cropKey;

        String displayName = plugin.getConfigManager()
                .getConfig("crops.yml")
                .getString(path + ".display-name", cropKey);
        // Translate color codes
        displayName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(displayName));

        int level = state.getWateringLevel();
        int maxLevel = plugin.getConfigManager()
                .getConfig("crops.yml")
                .getInt(path + ".watering.max-level", 10);

        String bar = buildBar(level, maxLevel);

        Component text;
        if (state.isRotten()) {
            text = Component.text()
                    .append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(displayName))
                    .append(Component.newline())
                    .append(Component.text("Tanaman Kering", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD))
                    .build();
        } else {
            text = Component.text()
                    .append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(displayName))
                    .append(Component.newline())
                    .append(Component.text("💧 [", NamedTextColor.AQUA))
                    .append(Component.text(bar, TextColor.color(0x3399FF)))
                    .append(Component.text("]", NamedTextColor.AQUA))
                    .build();
        }

        double holoHeight = plugin.getConfigManager()
                .getConfig("crops.yml")
                .getDouble(path + ".watering.hologram-height", 1.6);
        Location hologramLoc = plantLoc.clone().add(0.5, holoHeight, 0.5);

        TextDisplay existing = activeHolograms.get(player.getUniqueId());
        if (existing != null && existing.isValid()) {
            // Update existing entity in place
            existing.teleport(hologramLoc);
            existing.text(text);
            return;
        }

        // Spawn new TextDisplay
        TextDisplay display = plantLoc.getWorld().spawn(hologramLoc, TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setShadowed(true);
            td.setVisibleByDefault(false);
            // Scale up slightly
            td.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.2f, 1.2f, 1.2f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
        });

        player.showEntity(plugin, display);
        activeHolograms.put(player.getUniqueId(), display);
    }

    /**
     * Hide and remove the hologram for this player.
     */
    public void hide(Player player) {
        TextDisplay td = activeHolograms.remove(player.getUniqueId());
        if (td != null && td.isValid()) {
            td.remove();
        }
    }

    /**
     * Called when a player quits — cleanup their hologram.
     */
    public void cleanup(UUID uuid) {
        TextDisplay td = activeHolograms.remove(uuid);
        if (td != null && td.isValid()) td.remove();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String buildBar(int level, int max) {
        int filled = (int) Math.round((double) level / max * BAR_LENGTH);
        filled = Math.min(filled, BAR_LENGTH);
        return "§b" + "│".repeat(filled) + "§8" + "│".repeat(BAR_LENGTH - filled);
    }
}
