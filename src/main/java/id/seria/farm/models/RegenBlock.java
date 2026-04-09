package id.seria.farm.models;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class RegenBlock {
    private final Location location;
    private final Material originalMaterial;
    private final BlockData originalData;
    private final long restoreTime;

    public RegenBlock(Location location, Material originalMaterial, BlockData originalData, long restoreTime) {
        this.location = location;
        this.originalMaterial = originalMaterial;
        this.originalData = originalData;
        this.restoreTime = restoreTime;
    }

    public Location getLocation() { return location; }
    public Material getOriginalMaterial() { return originalMaterial; }
    public BlockData getOriginalData() { return originalData; }
    public long getRestoreTime() { return restoreTime; }
}
