package ru.nikita.locators.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.nikita.locators.LocatorsPlugin;
import ru.nikita.locators.config.CooldownStore;
import ru.nikita.locators.config.LocatorConfigException;
import ru.nikita.locators.config.LocatorRegistry;
import ru.nikita.locators.item.LocatorItemService;
import ru.nikita.locators.model.LocatorDefinition;
import ru.nikita.locators.util.TurnRequestStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class LocatorsCommand implements CommandExecutor, TabCompleter {
    private static final String RESET_PERMISSION = "locators.resetcooldown";

    private final LocatorsPlugin plugin;
    private final LocatorRegistry registry;
    private final LocatorItemService itemService;
    private final CooldownStore cooldownStore;
    private final TurnRequestStore turnRequestStore;

    public LocatorsCommand(LocatorsPlugin plugin, LocatorRegistry registry, LocatorItemService itemService,
                           CooldownStore cooldownStore, TurnRequestStore turnRequestStore) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemService = itemService;
        this.cooldownStore = cooldownStore;
        this.turnRequestStore = turnRequestStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("turn")) {
            return turn(sender, args[1]);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return give(sender, args[1], args[2], args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("resetTypeCooldown")) {
            return resetTypeCooldown(sender, args[1], args[2]);
        }

        sender.sendMessage(ChatColor.YELLOW + "Использование:");
        sender.sendMessage(ChatColor.GRAY + "/locators give <игрок> <id> <количество>");
        sender.sendMessage(ChatColor.GRAY + "/locators reload");
        sender.sendMessage(ChatColor.GRAY + "/locator resetTypeCooldown <тип|*> <UUID|ник|*>");
        return true;
    }

    private boolean turn(CommandSender sender, String token) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Повернуть камеру можно только игроку.");
            return true;
        }
        Player player = (Player) sender;
        TurnRequestStore.TurnRequest request = turnRequestStore
                .consume(player.getUniqueId(), token)
                .orElse(null);
        if (request == null) {
            player.sendMessage(ChatColor.RED + "Ссылка на угол устарела или уже была использована.");
            return true;
        }

        Location location = player.getLocation();
        if (request.yaw() != null) {
            location.setYaw(request.yaw());
        }
        if (request.pitch() != null) {
            location.setPitch(request.pitch());
        }
        if (!player.teleport(location)) {
            player.sendMessage(ChatColor.RED + "Не удалось повернуть камеру: телепортация отменена.");
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("locators.reload")) {
            noPermission(sender, "locators.reload");
            return true;
        }
        try {
            registry.reload();
            sender.sendMessage(ChatColor.GREEN + "locators.json перезагружен. Типов: " + registry.all().size());
        } catch (LocatorConfigException exception) {
            sender.sendMessage(ChatColor.RED + "Конфигурация не перезагружена:");
            Arrays.stream(exception.getMessage().split("\\R"))
                    .forEach(line -> sender.sendMessage(ChatColor.RED + line));
            plugin.getLogger().warning("Ошибка reload: " + exception.getMessage());
        }
        return true;
    }

    private boolean give(CommandSender sender, String playerName, String locatorId, String amountText) {
        LocatorDefinition locator = registry.find(locatorId).orElse(null);
        if (locator == null) {
            sender.sendMessage(ChatColor.RED + "Локатор с id «" + locatorId + "» не найден.");
            return true;
        }
        if (!locator.givePermission().trim().isEmpty() && !sender.hasPermission(locator.givePermission())) {
            noPermission(sender, locator.givePermission());
            return true;
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок «" + playerName + "» не найден или не в сети.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountText);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Количество должно быть целым числом.");
            return true;
        }
        if (amount < 1 || amount > 6400) {
            sender.sendMessage(ChatColor.RED + "Количество должно быть от 1 до 6400.");
            return true;
        }

        if (locator.cooldownMode() == 2) {
            for (int index = 0; index < amount; index++) {
                addOrDrop(target, itemService.create(locator, 1));
            }
        } else {
            int remaining = amount;
            while (remaining > 0) {
                int stackAmount = Math.min(remaining, locator.material().getMaxStackSize());
                addOrDrop(target, itemService.create(locator, stackAmount));
                remaining -= stackAmount;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Выдано " + amount + " шт. локатора «" + locator.id()
                + "» игроку " + target.getName() + ".");
        if (!sender.equals(target)) {
            target.sendMessage(ChatColor.AQUA + "Вы получили локатор «" + locator.id() + "» x" + amount + ".");
        }
        return true;
    }

    private void addOrDrop(Player target, ItemStack stack) {
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
        for (ItemStack leftover : leftovers.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
    }

    private boolean resetTypeCooldown(CommandSender sender, String locatorArgument, String playerArgument) {
        if (!sender.hasPermission(RESET_PERMISSION)) {
            noPermission(sender, RESET_PERMISSION);
            return true;
        }

        String locatorFilter = null;
        if (!locatorArgument.equals("*")) {
            LocatorDefinition locator = registry.find(locatorArgument).orElse(null);
            if (locator == null) {
                sender.sendMessage(ChatColor.RED + "Локатор с id «" + locatorArgument + "» не найден.");
                return true;
            }
            if (locator.cooldownMode() != 1) {
                sender.sendMessage(ChatColor.RED + "Команда работает только с локаторами cooldownMode 1.");
                return true;
            }
            locatorFilter = locator.id();
        }

        UUID playerFilter = null;
        if (!playerArgument.equals("*")) {
            playerFilter = resolvePlayerId(playerArgument);
            if (playerFilter == null) {
                sender.sendMessage(ChatColor.RED + "Игрок «" + playerArgument
                        + "» не найден. Укажите известный серверу ник или UUID.");
                return true;
            }
        }

        int removed = cooldownStore.resetTypeCooldowns(locatorFilter, playerFilter);
        try {
            cooldownStore.save();
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить cooldowns.json после сброса: " + exception.getMessage());
            sender.sendMessage(ChatColor.RED + "Cooldown сброшен в памяти, но файл сохранить не удалось.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Сброшено активных cooldown: " + removed + ".");
        return true;
    }

    private UUID resolvePlayerId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            // Try online and previously seen player names below.
        }

        Player online = Bukkit.getPlayerExact(value);
        if (online != null) {
            return online.getUniqueId();
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(value)) {
                return offline.getUniqueId();
            }
        }
        return null;
    }

    private void noPermission(CommandSender sender, String permission) {
        sender.sendMessage(ChatColor.RED + "Нет права " + permission + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            if (sender.hasPermission("locators.reload")) {
                options.add("reload");
            }
            if (registry.all().stream().anyMatch(locator -> canGive(sender, locator))) {
                options.add("give");
            }
            if (sender.hasPermission(RESET_PERMISSION)) {
                options.add("resetTypeCooldown");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(registry.all().stream()
                    .filter(locator -> canGive(sender, locator))
                    .map(LocatorDefinition::id)
                    .collect(Collectors.toList()), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("1", "8", "16", "32", "64"), args[3]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetTypeCooldown")
                && sender.hasPermission(RESET_PERMISSION)) {
            List<String> types = registry.all().stream()
                    .filter(locator -> locator.cooldownMode() == 1)
                    .map(LocatorDefinition::id)
                    .collect(Collectors.toCollection(ArrayList::new));
            types.add("*");
            return filter(types, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("resetTypeCooldown")
                && sender.hasPermission(RESET_PERMISSION)) {
            List<String> players = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toCollection(ArrayList::new));
            players.add("*");
            return filter(players, args[2]);
        }
        return Collections.emptyList();
    }

    private boolean canGive(CommandSender sender, LocatorDefinition locator) {
        return locator.givePermission().trim().isEmpty() || sender.hasPermission(locator.givePermission());
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .collect(Collectors.toList());
    }
}
