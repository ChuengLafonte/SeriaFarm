package id.seria.farm.models;

import org.bukkit.Location;
import java.util.UUID;

/**
 * Represents the live state of a custom plant at a specific location.
 */
public class CustomPlantState {

    private final Location location;
    private final UUID ownerUUID;
    private final String cropKey;
    private int wateringLevel;
    private long plantedAt;
    private long noWaterSince; // 0 = has water
    private boolean rotten;

    public CustomPlantState(Location location, UUID ownerUUID, String cropKey,
                            int wateringLevel, long plantedAt, long noWaterSince, boolean rotten) {
        this.location = location;
        this.ownerUUID = ownerUUID;
        this.cropKey = cropKey;
        this.wateringLevel = wateringLevel;
        this.plantedAt = plantedAt;
        this.noWaterSince = noWaterSince;
        this.rotten = rotten;
    }

    public Location getLocation() { return location; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getCropKey() { return cropKey; }

    public int getWateringLevel() { return wateringLevel; }
    public void setWateringLevel(int level) { this.wateringLevel = level; }

    public long getPlantedAt() { return plantedAt; }
    public void setPlantedAt(long ts) { this.plantedAt = ts; }

    public long getNoWaterSince() { return noWaterSince; }
    public void setNoWaterSince(long ts) { this.noWaterSince = ts; }

    public boolean isRotten() { return rotten; }
    public void setRotten(boolean rotten) { this.rotten = rotten; }
}
