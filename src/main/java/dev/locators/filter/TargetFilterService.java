package dev.locators.filter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import dev.locators.model.LocatorDefinition;

import java.util.Locale;

public final class TargetFilterService {
    private final Plugin plugin;
    private TargetGroupResolver luckPermsResolver;
    private boolean runtimeFallbackReported;

    public TargetFilterService(Plugin plugin) {
        this.plugin = plugin;
        Plugin luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPermsPlugin == null || !luckPermsPlugin.isEnabled()) {
            plugin.getLogger().info("LuckPerms не найден: для luckperms_group будут использоваться scoreboard-теги.");
            return;
        }

        try {
            luckPermsResolver = new LuckPermsGroupResolver();
            plugin.getLogger().info("Интеграция с LuckPerms включена.");
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("API LuckPerms недоступен, используется fallback на scoreboard-теги: "
                    + exception.getMessage());
        }
    }

    public boolean matches(Player player, LocatorDefinition locator) {
        String target = locator.target();
        if (target.equalsIgnoreCase("all")) {
            return true;
        }

        String mode = locator.targetMode().toLowerCase(Locale.ROOT);
        if (mode.equals("luckperms_group") && luckPermsResolver != null) {
            try {
                return luckPermsResolver.isMember(player, target);
            } catch (RuntimeException | LinkageError exception) {
                luckPermsResolver = null;
                if (!runtimeFallbackReported) {
                    runtimeFallbackReported = true;
                    plugin.getLogger().warning("Ошибка LuckPerms во время проверки группы; дальнейшие проверки "
                            + "используют scoreboard-теги: " + exception.getMessage());
                }
            }
        }
        return hasScoreboardTag(player, target);
    }

    private boolean hasScoreboardTag(Player player, String requiredTag) {
        for (String tag : player.getScoreboardTags()) {
            if (tag.equalsIgnoreCase(requiredTag)) {
                return true;
            }
        }
        return false;
    }
}
