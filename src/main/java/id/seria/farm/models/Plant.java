package id.seria.farm.models;

import java.util.UUID;
import org.bukkit.Location;

public class Plant {
    private final Location location;
    private final String cropType;
    private final UUID owner;
    private final long plantedAt;
    private int growthStage;

    public Plant(Location location, String cropType, UUID owner, long plantedAt) {
        this.location = location;
        this.cropType = cropType;
        this.owner = owner;
        this.plantedAt = plantedAt;
        this.growthStage = 0;
    }

    public Location getLocation() { return location; }
    public String getCropType() { return cropType; }
    public UUID getOwner() { return owner; }
    public long getPlantedAt() { return plantedAt; }
    public int getGrowthStage() { return growthStage; }
    public void setGrowthStage(int stage) { this.growthStage = stage; }
}
