package ru.nikita.locators.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scoreboard.Team;
import ru.nikita.locators.config.LocatorRegistry;
import ru.nikita.locators.item.LocatorItemService;
import ru.nikita.locators.model.LocatorDefinition;
import ru.nikita.locators.util.Angles;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LocatorUseListener implements Listener {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final LocatorRegistry registry;
    private final LocatorItemService itemService;
    private final Map<CooldownKey, Long> cooldowns = new HashMap<>();

    public LocatorUseListener(LocatorRegistry registry, LocatorItemService itemService) {
        this.registry = registry;
        this.itemService = itemService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        itemService.getLocatorId(event.getItem()).ifPresent(locatorId -> {
            event.setCancelled(true);
            use(event.getPlayer(), locatorId);
        });
    }

    private void use(Player player, String locatorId) {
        LocatorDefinition locator = registry.find(locatorId).orElse(null);
        if (locator == null) {
            player.sendMessage(ChatColor.RED + "Этот локатор больше не существует в locators.json.");
            return;
        }
        if (!hasPermission(player, locator.usePermission())) {
            player.sendMessage(ChatColor.RED + "У вас нет права " + locator.usePermission() + ".");
            return;
        }

        CooldownKey key = new CooldownKey(player.getUniqueId(), locator.id().toLowerCase(Locale.ROOT));
        long now = System.nanoTime();
        long readyAt = cooldowns.getOrDefault(key, 0L);
        if (readyAt > now) {
            double remaining = (readyAt - now) / NANOS_PER_SECOND;
            player.sendMessage(ChatColor.YELLOW + String.format(Locale.US,
                    "Локатор перезаряжается. Осталось %.1f сек.", remaining));
            return;
        }

        long cooldownNanos = (long) (locator.cooldownSeconds() * NANOS_PER_SECOND);
        cooldowns.put(key, now + cooldownNanos);
        showResults(player, locator);
    }

    private void showResults(Player user, LocatorDefinition locator) {
        int found = 0;
        user.sendMessage(ChatColor.AQUA + "Результаты локатора «" + locator.id() + "»:");
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(user) || !target.getWorld().equals(user.getWorld()) || !matchesTeam(target, locator.targetTeam())) {
                continue;
            }

            Location from = user.getEyeLocation();
            Location to = target.getEyeLocation();
            double trueDistance = from.distance(to);
            if (trueDistance > locator.maxDistance()) {
                continue;
            }

            StringBuilder line = new StringBuilder(ChatColor.GREEN + " • " + target.getName());
            if (locator.showDistance()) {
                double distance = Math.max(0, randomize(trueDistance, locator.distanceError()));
                line.append(ChatColor.WHITE).append(String.format(Locale.US,
                        " | расстояние: ~%.1f блоков", distance));
            }
            if (locator.showYaw()) {
                double yaw = Angles.difference(Angles.yawTo(from, to), from.getYaw());
                yaw = Angles.normalize(randomize(yaw, locator.yawError()));
                line.append(ChatColor.WHITE).append(" | yaw: ").append(horizontalDirection(yaw));
            }
            if (locator.showPitch()) {
                double pitch = Angles.difference(Angles.pitchTo(from, to), from.getPitch());
                pitch = Angles.normalize(randomize(pitch, locator.pitchError()));
                line.append(ChatColor.WHITE).append(" | pitch: ").append(verticalDirection(pitch));
            }
            user.sendMessage(line.toString());
            found++;
        }

        if (found == 0) {
            user.sendMessage(ChatColor.GRAY + "Подходящие игроки не найдены.");
        }
    }

    private boolean matchesTeam(Player target, String requiredTeam) {
        if (requiredTeam.equalsIgnoreCase("all")) {
            return true;
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(target.getName());
        return team != null && team.getName().equalsIgnoreCase(requiredTeam);
    }

    private double randomize(double value, double error) {
        if (error == 0) {
            return value;
        }
        return ThreadLocalRandom.current().nextDouble(value - error, value + error);
    }

    private String horizontalDirection(double angle) {
        if (Math.abs(angle) < 0.05) {
            return "прямо";
        }
        return String.format(Locale.US, "%.1f° %s", Math.abs(angle), angle > 0 ? "вправо" : "влево");
    }

    private String verticalDirection(double angle) {
        if (Math.abs(angle) < 0.05) {
            return "прямо";
        }
        return String.format(Locale.US, "%.1f° %s", Math.abs(angle), angle > 0 ? "вниз" : "вверх");
    }

    private boolean hasPermission(Player player, String permission) {
        return permission.trim().isEmpty() || player.hasPermission(permission);
    }

    private static final class CooldownKey {
        private final UUID playerId;
        private final String locatorId;

        private CooldownKey(UUID playerId, String locatorId) {
            this.playerId = playerId;
            this.locatorId = locatorId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CooldownKey)) {
                return false;
            }
            CooldownKey that = (CooldownKey) other;
            return playerId.equals(that.playerId) && locatorId.equals(that.locatorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, locatorId);
        }
    }
}
