package dev.locators.model;

import org.bukkit.Material;

/**
 * JSON model. It intentionally uses a regular class instead of a Java record so
 * the resulting plugin remains loadable on Java 8 based Minecraft servers.
 */
public final class LocatorDefinition {
    private String id;
    private String item;
    private boolean showDistance;
    private double distanceError;
    private boolean showYaw;
    private double yawError;
    private boolean showPitch;
    private double pitchError;
    private double maxDistance;
    private String targetMode;
    private String target;
    // Legacy field: configurations before 1.3.0 used scoreboard teams.
    private String targetTeam;
    private double cooldownSeconds;
    private int cooldownMode;
    private String usePermission;
    private String givePermission;

    public String id() {
        return id;
    }

    public String item() {
        return item;
    }

    public boolean showDistance() {
        return showDistance;
    }

    public double distanceError() {
        return distanceError;
    }

    public boolean showYaw() {
        return showYaw;
    }

    public double yawError() {
        return yawError;
    }

    public boolean showPitch() {
        return showPitch;
    }

    public double pitchError() {
        return pitchError;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public String targetMode() {
        return targetMode == null || targetMode.trim().isEmpty() ? "scoreboard_tag" : targetMode;
    }

    public String target() {
        if (target != null && !target.trim().isEmpty()) {
            return target;
        }
        return targetTeam;
    }

    public double cooldownSeconds() {
        return cooldownSeconds;
    }

    /**
     * Missing values from configurations created before 1.1.0 are treated as mode 1.
     */
    public int cooldownMode() {
        return cooldownMode == 0 ? 1 : cooldownMode;
    }

    public String usePermission() {
        return usePermission;
    }

    public String givePermission() {
        return givePermission;
    }

    public Material material() {
        return item == null ? null : Material.matchMaterial(item);
    }
}
