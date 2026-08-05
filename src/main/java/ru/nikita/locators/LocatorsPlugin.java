package ru.nikita.locators;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.nikita.locators.command.LocatorsCommand;
import ru.nikita.locators.config.CooldownStore;
import ru.nikita.locators.config.LocatorConfigException;
import ru.nikita.locators.config.LocatorRegistry;
import ru.nikita.locators.item.LocatorItemService;
import ru.nikita.locators.listener.LocatorUseListener;
import ru.nikita.locators.util.TurnRequestStore;

import java.io.File;
import java.io.IOException;

public final class LocatorsPlugin extends JavaPlugin {
    private LocatorRegistry registry;
    private CooldownStore cooldownStore;
    private boolean cooldownStoreLoaded;

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

        cooldownStore = new CooldownStore(getDataFolder().toPath().resolve("cooldowns.json"));
        try {
            cooldownStore.load();
            cooldownStoreLoaded = true;
        } catch (IOException exception) {
            getLogger().severe("Плагин отключён: не удалось загрузить cooldowns.json: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LocatorItemService itemService = new LocatorItemService(this);
        TurnRequestStore turnRequestStore = new TurnRequestStore();
        getServer().getPluginManager().registerEvents(
                new LocatorUseListener(registry, itemService, cooldownStore, turnRequestStore), this);

        PluginCommand command = getCommand("locators");
        if (command == null) {
            throw new IllegalStateException("Команда locators отсутствует в plugin.yml");
        }
        LocatorsCommand commandHandler = new LocatorsCommand(
                this, registry, itemService, cooldownStore, turnRequestStore);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("Загружено типов локаторов: " + registry.all().size()
                + ", активных cooldown: " + cooldownStore.size());
    }

    @Override
    public void onDisable() {
        if (!cooldownStoreLoaded || cooldownStore == null) {
            return;
        }
        try {
            cooldownStore.save();
            getLogger().info("Cooldown сохранены в cooldowns.json.");
        } catch (IOException exception) {
            getLogger().severe("Не удалось сохранить cooldowns.json: " + exception.getMessage());
        }
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
