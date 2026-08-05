package dev.locators.listener;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Team;
import dev.locators.config.CooldownStore;
import dev.locators.config.LocatorRegistry;
import dev.locators.item.LocatorItemService;
import dev.locators.model.LocatorDefinition;
import dev.locators.util.Angles;
import dev.locators.util.TurnRequestStore;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LocatorUseListener implements Listener {
    private final LocatorRegistry registry;
    private final LocatorItemService itemService;
    private final CooldownStore cooldownStore;
    private final TurnRequestStore turnRequestStore;

    public LocatorUseListener(LocatorRegistry registry, LocatorItemService itemService, CooldownStore cooldownStore,
                              TurnRequestStore turnRequestStore) {
        this.registry = registry;
        this.itemService = itemService;
        this.cooldownStore = cooldownStore;
        this.turnRequestStore = turnRequestStore;
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

        long now = System.currentTimeMillis();
        UUID instanceId = null;
        long remainingMillis;
        if (locator.cooldownMode() == 2) {
            instanceId = prepareIndividualItem(player);
            remainingMillis = cooldownStore.remainingItemMillis(instanceId, now);
        } else {
            remainingMillis = cooldownStore.remainingTypeMillis(player.getUniqueId(), locator.id(), now);
        }

        if (remainingMillis > 0) {
            player.sendMessage(ChatColor.YELLOW + String.format(Locale.US,
                    "Локатор перезаряжается. Осталось %.1f сек.", remainingMillis / 1000.0));
            return;
        }

        long readyAt = calculateReadyAt(now, locator.cooldownSeconds());
        if (locator.cooldownMode() == 2) {
            cooldownStore.startItem(instanceId, readyAt);
        } else {
            cooldownStore.startType(player.getUniqueId(), locator.id(), readyAt);
        }
        showResults(player, locator);
    }

    /**
     * Mode 2 items are normally issued one by one. This also safely migrates a
     * legacy stacked locator by separating the currently held item from its stack.
     */
    private UUID prepareIndividualItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() > 1) {
            ItemStack remainder = held.clone();
            remainder.setAmount(held.getAmount() - 1);
            itemService.clearInstanceId(remainder);

            ItemStack individual = held.clone();
            individual.setAmount(1);
            UUID instanceId = itemService.getOrCreateInstanceId(individual);
            player.getInventory().setItemInMainHand(individual);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(remainder);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            return instanceId;
        }

        UUID instanceId = itemService.getOrCreateInstanceId(held);
        player.getInventory().setItemInMainHand(held);
        return instanceId;
    }

    private long calculateReadyAt(long now, double cooldownSeconds) {
        double durationMillis = cooldownSeconds * 1000.0;
        if (durationMillis >= Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + (long) durationMillis;
    }

    private void showResults(Player user, LocatorDefinition locator) {
        int found = 0;
        user.sendMessage(ChatColor.AQUA + "Результаты локатора «" + locator.id() + "»:");
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(user) || !target.getWorld().equals(user.getWorld())
                    || !matchesTeam(target, locator.targetTeam())) {
                continue;
            }

            Location from = user.getEyeLocation();
            Location to = target.getEyeLocation();
            double trueDistance = from.distance(to);
            if (trueDistance > locator.maxDistance()) {
                continue;
            }

            TextComponent line = new TextComponent(" • " + target.getName());
            line.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            if (locator.showDistance()) {
                double distance = Math.max(0, randomize(trueDistance, locator.distanceError()));
                line.addExtra(plainComponent(String.format(Locale.US,
                        " | расстояние: ~%.1f блоков", distance)));
            }
            if (locator.showYaw()) {
                double yaw = Angles.difference(Angles.yawTo(from, to), from.getYaw());
                yaw = Angles.normalize(randomize(yaw, locator.yawError()));
                float absoluteYaw = (float) Angles.normalize(from.getYaw() + yaw);
                String token = turnRequestStore.createYaw(user.getUniqueId(), absoluteYaw);
                line.addExtra(clickableAngle(" | yaw: " + horizontalDirection(yaw), token));
            }
            if (locator.showPitch()) {
                double pitch = Angles.difference(Angles.pitchTo(from, to), from.getPitch());
                pitch = Angles.normalize(randomize(pitch, locator.pitchError()));
                float absolutePitch = (float) Math.max(-90.0, Math.min(90.0, from.getPitch() + pitch));
                pitch = absolutePitch - from.getPitch();
                String token = turnRequestStore.createPitch(user.getUniqueId(), absolutePitch);
                line.addExtra(clickableAngle(" | pitch: " + verticalDirection(pitch), token));
            }
            user.spigot().sendMessage(line);
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

    private TextComponent plainComponent(String text) {
        TextComponent component = new TextComponent(text);
        component.setColor(net.md_5.bungee.api.ChatColor.WHITE);
        return component;
    }

    private TextComponent clickableAngle(String text, String token) {
        TextComponent component = new TextComponent(text);
        component.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        component.setUnderlined(true);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/locator turn " + token));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Нажмите, чтобы повернуть камеру")
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .create()));
        return component;
    }

    private boolean hasPermission(Player player, String permission) {
        return permission.trim().isEmpty() || player.hasPermission(permission);
    }
}
