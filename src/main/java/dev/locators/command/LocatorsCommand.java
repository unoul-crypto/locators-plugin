package dev.locators.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import dev.locators.LocatorsPlugin;
import dev.locators.config.LocatorConfigException;
import dev.locators.config.LocatorRegistry;
import dev.locators.item.LocatorItemService;
import dev.locators.model.LocatorDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class LocatorsCommand implements CommandExecutor, TabCompleter {
    private final LocatorsPlugin plugin;
    private final LocatorRegistry registry;
    private final LocatorItemService itemService;

    public LocatorsCommand(LocatorsPlugin plugin, LocatorRegistry registry, LocatorItemService itemService) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemService = itemService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return give(sender, args[1], args[2], args[3]);
        }

        sender.sendMessage(ChatColor.YELLOW + "Использование:");
        sender.sendMessage(ChatColor.GRAY + "/locators give <игрок> <id> <количество>");
        sender.sendMessage(ChatColor.GRAY + "/locators reload");
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

        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, locator.material().getMaxStackSize());
            ItemStack stack = itemService.create(locator, stackAmount);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            remaining -= stackAmount;
        }

        sender.sendMessage(ChatColor.GREEN + "Выдано " + amount + " шт. локатора «" + locator.id()
                + "» игроку " + target.getName() + ".");
        if (!sender.equals(target)) {
            target.sendMessage(ChatColor.AQUA + "Вы получили локатор «" + locator.id() + "» x" + amount + ".");
        }
        return true;
    }

    private void noPermission(CommandSender sender, String permission) {
        sender.sendMessage(ChatColor.RED + "Нет права " + permission + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("locators.reload")) {
                options.add("reload");
            }
            if (registry.all().stream().anyMatch(locator -> canGive(sender, locator))) {
                options.add("give");
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
