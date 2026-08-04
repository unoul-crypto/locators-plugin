package ru.nikita.locators.util;

import org.bukkit.Location;

public final class Angles {
    private Angles() {
    }

    public static double yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static double pitchTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    public static double difference(double target, double current) {
        return normalize(target - current);
    }

    public static double normalize(double angle) {
        double normalized = angle % 360.0;
        if (normalized > 180.0) {
            normalized -= 360.0;
        } else if (normalized <= -180.0) {
            normalized += 360.0;
        }
        return normalized;
    }
}
