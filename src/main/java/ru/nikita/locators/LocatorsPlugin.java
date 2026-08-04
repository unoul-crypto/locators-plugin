package ru.nikita.locators;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.nikita.locators.command.LocatorsCommand;
import ru.nikita.locators.config.LocatorConfigException;
import ru.nikita.locators.config.LocatorRegistry;
import ru.nikita.locators.item.LocatorItemService;
import ru.nikita.locators.listener.LocatorUseListener;

import java.io.File;

public final class LocatorsPlugin extends JavaPlugin {
    private LocatorRegistry registry;

    @Override
    public void onEnable() {
        ensureDefaultConfig();
        registry = new LocatorRegistry(getDataFolder().toPath().resolve("locators.json"));
        try {
            registry.reload();
        } catch (LocatorConfigException exception) {
            getLogger().severe("Плагин отключён: ошибка в locators.json:");
            for (String line : exception.getMessage().split("\\R")) {
                getLogger().severe(line);
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LocatorItemService itemService = new LocatorItemService(this);
        getServer().getPluginManager().registerEvents(new LocatorUseListener(registry, itemService), this);

        PluginCommand command = getCommand("locators");
        if (command == null) {
            throw new IllegalStateException("Команда locators отсутствует в plugin.yml");
        }
        LocatorsCommand commandHandler = new LocatorsCommand(this, registry, itemService);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("Загружено типов локаторов: " + registry.all().size());
    }

    private void ensureDefaultConfig() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Не удалось создать папку плагина: " + getDataFolder());
        }
        File config = new File(getDataFolder(), "locators.json");
        if (!config.exists()) {
            saveResource("locators.json", false);
        }
    }
}
