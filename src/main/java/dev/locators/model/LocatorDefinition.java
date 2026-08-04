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
    private String targetTeam;
    private double cooldownSeconds;
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

    public String targetTeam() {
        return targetTeam;
    }

    public double cooldownSeconds() {
        return cooldownSeconds;
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
