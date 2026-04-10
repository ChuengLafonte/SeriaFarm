package id.seria.farm.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public class LocationUtils {

    public static String serialize(Location loc) {
        if (loc == null) return "world;0;0;0;0;0";
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    public static Location deserialize(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            String[] p = str.split(";");
            if (p.length >= 4) {
                return new Location(Bukkit.getWorld(p[0]), 
                                    Double.parseDouble(p[1]), 
                                    Double.parseDouble(p[2]), 
                                    Double.parseDouble(p[3]),
                                    p.length > 4 ? Float.parseFloat(p[4]) : 0, 
                                    p.length > 5 ? Float.parseFloat(p[5]) : 0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isInside(Location target, Location l1, Location l2) {
        if (!target.getWorld().equals(l1.getWorld())) return false;
        
        double minX = Math.min(l1.getX(), l2.getX());
        double minY = Math.min(l1.getY(), l2.getY());
        double minZ = Math.min(l1.getZ(), l2.getZ());
        double maxX = Math.max(l1.getX(), l2.getX()) + 1;
        double maxY = Math.max(l1.getY(), l2.getY()) + 1;
        double maxZ = Math.max(l1.getZ(), l2.getZ()) + 1;

        return target.getX() >= minX && target.getX() < maxX &&
               target.getY() >= minY && target.getY() < maxY &&
               target.getZ() >= minZ && target.getZ() < maxZ;
    }

    public static java.util.Set<org.bukkit.Material> getUniqueMaterialsInRange(Location l1, Location l2) {
        java.util.Set<org.bukkit.Material> materials = new java.util.HashSet<>();
        if (l1 == null || l2 == null || !l1.getWorld().equals(l2.getWorld())) return materials;

        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int minY = Math.min(l1.getBlockY(), l2.getBlockY());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int maxY = Math.max(l1.getBlockY(), l2.getBlockY());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

        org.bukkit.World world = l1.getWorld();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    materials.add(world.getBlockAt(x, y, z).getType());
                }
            }
        }
        return materials;
    }
}
