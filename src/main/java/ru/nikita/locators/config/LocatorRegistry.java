package ru.nikita.locators.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.bukkit.Material;
import ru.nikita.locators.model.LocatorDefinition;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LocatorRegistry {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Gson GSON = new GsonBuilder().create();

    private final Path configPath;
    private Map<String, LocatorDefinition> definitions = Collections.emptyMap();

    public LocatorRegistry(Path configPath) {
        this.configPath = configPath;
    }

    public void reload() throws LocatorConfigException {
        Root root;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, Root.class);
        } catch (IOException | JsonParseException exception) {
            throw new LocatorConfigException("Не удалось прочитать " + configPath.getFileName() + ": "
                    + exception.getMessage(), exception);
        }

        if (root == null || root.locators == null) {
            throw new LocatorConfigException("Корневой объект должен содержать массив \"locators\".");
        }

        Map<String, LocatorDefinition> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < root.locators.size(); index++) {
            LocatorDefinition definition = root.locators.get(index);
            String prefix = "locators[" + index + "]";
            validate(definition, prefix, errors);
            if (definition != null && definition.id() != null) {
                String normalizedId = normalize(definition.id());
                if (loaded.putIfAbsent(normalizedId, definition) != null) {
                    errors.add(prefix + ": повторяющийся id \"" + definition.id() + "\"");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new LocatorConfigException(String.join(System.lineSeparator(), errors));
        }

        definitions = Collections.unmodifiableMap(loaded);
    }

    private void validate(LocatorDefinition definition, String prefix, List<String> errors) {
        if (definition == null) {
            errors.add(prefix + ": запись не может быть null");
            return;
        }

        if (isBlank(definition.id()) || !VALID_ID.matcher(definition.id()).matches()) {
            errors.add(prefix + ".id: допустимы только a-z, 0-9, _, . и -");
        }

        Material material = definition.material();
        if (material == null || !material.isItem()) {
            errors.add(prefix + ".item: неизвестный или непригодный предмет \"" + definition.item() + "\"");
        }

        validateNonNegative(definition.distanceError(), prefix + ".distanceError", errors);
        validateNonNegative(definition.yawError(), prefix + ".yawError", errors);
        validateNonNegative(definition.pitchError(), prefix + ".pitchError", errors);
        validateNonNegative(definition.cooldownSeconds(), prefix + ".cooldownSeconds", errors);
        if (definition.cooldownMode() != 1 && definition.cooldownMode() != 2) {
            errors.add(prefix + ".cooldownMode: допустимы только значения 1 и 2");
        }
        if (!Double.isFinite(definition.maxDistance()) || definition.maxDistance() <= 0) {
            errors.add(prefix + ".maxDistance: должно быть конечным числом больше 0");
        }
        if (isBlank(definition.targetTeam())) {
            errors.add(prefix + ".targetTeam: укажите имя команды или \"all\"");
        }
        if (definition.usePermission() == null) {
            errors.add(prefix + ".usePermission: укажите право или пустую строку");
        }
        if (definition.givePermission() == null) {
            errors.add(prefix + ".givePermission: укажите право или пустую строку");
        }
    }

    private void validateNonNegative(double value, String field, List<String> errors) {
        if (!Double.isFinite(value) || value < 0) {
            errors.add(field + ": должно быть конечным неотрицательным числом");
        }
    }

    public Optional<LocatorDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(normalize(id)));
    }

    public Collection<LocatorDefinition> all() {
        return definitions.values();
    }

    private String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class Root {
        private List<LocatorDefinition> locators;
    }
}
