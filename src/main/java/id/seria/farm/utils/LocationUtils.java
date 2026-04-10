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
}
