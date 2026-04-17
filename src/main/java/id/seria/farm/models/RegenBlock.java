package id.seria.farm.models;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import java.util.List;

public class RegenBlock {
    private final Location location;
    private final Material originalMaterial;
    private final BlockData originalData;
    private final long restoreTime;
    private final List<String> replaceBlocks;
    private final List<String> delayBlocks;
    private final String materialKey;

    // Growth Metadata
    private boolean isGrowth = false;
    private String growthMode = "INSTANT";
    private int maxStage = 0;
    private int currentStage = 0;
    private long stepDuration = 0;
    private long lastStepTime = 0;
    private long startTime = 0;

    public RegenBlock(Location location, Material originalMaterial, BlockData originalData, long restoreTime, List<String> replaceBlocks, List<String> delayBlocks, String materialKey) {
        this.location = location;
        this.originalMaterial = originalMaterial;
        this.originalData = originalData;
        this.restoreTime = restoreTime;
        this.replaceBlocks = replaceBlocks != null ? replaceBlocks : new java.util.ArrayList<>();
        this.delayBlocks = delayBlocks != null ? delayBlocks : new java.util.ArrayList<>();
        this.materialKey = materialKey;
    }

    // Getters and Setters for Growth
    public boolean isGrowth() { return isGrowth; }
    public void setGrowth(boolean growth) { isGrowth = growth; }
    public String getGrowthMode() { return growthMode; }
    public void setGrowthMode(String growthMode) { this.growthMode = growthMode; }
    public int getMaxStage() { return maxStage; }
    public void setMaxStage(int maxStage) { this.maxStage = maxStage; }
    public int getCurrentStage() { return currentStage; }
    public void setCurrentStage(int currentStage) { this.currentStage = currentStage; }
    public long getStepDuration() { return stepDuration; }
    public void setStepDuration(long stepDuration) { this.stepDuration = stepDuration; }
    public long getLastStepTime() { return lastStepTime; }
    public void setLastStepTime(long lastStepTime) { this.lastStepTime = lastStepTime; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public Location getLocation() { return location; }
    public Material getOriginalMaterial() { return originalMaterial; }
    public BlockData getOriginalData() { return originalData; }
    public long getRestoreTime() { return restoreTime; }
    public List<String> getReplaceBlocks() { return replaceBlocks; }
    public List<String> getDelayBlocks() { return delayBlocks; }
    public String getMaterialKey() { return materialKey; }
}
