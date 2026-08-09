package dev.locators.filter;

import org.bukkit.entity.Player;

interface TargetGroupResolver {
    boolean isMember(Player player, String groupName);
}
