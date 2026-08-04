package ru.nikita.locators.item;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.nikita.locators.model.LocatorDefinition;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class LocatorItemService {
    private final NamespacedKey locatorIdKey;
    private final NamespacedKey instanceIdKey;

    public LocatorItemService(Plugin plugin) {
        this.locatorIdKey = new NamespacedKey(plugin, "locator_id");
        this.instanceIdKey = new NamespacedKey(plugin, "locator_instance_id");
    }

    public ItemStack create(LocatorDefinition definition, int amount) {
        if (definition.cooldownMode() == 2 && amount != 1) {
            throw new IllegalArgumentException("Локатор режима 2 должен создаваться по одному предмету");
        }
        ItemStack item = new ItemStack(definition.material(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Локатор: " + definition.id());
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Нажмите ПКМ, чтобы найти игроков",
                ChatColor.DARK_GRAY + "ID: " + definition.id()
        ));
        meta.getPersistentDataContainer().set(locatorIdKey, PersistentDataType.STRING, definition.id());
        if (definition.cooldownMode() == 2) {
            meta.getPersistentDataContainer().set(instanceIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        item.setItemMeta(meta);
        return item;
    }

    public Optional<String> getLocatorId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer()
                .get(locatorIdKey, PersistentDataType.STRING));
    }

    public UUID getOrCreateInstanceId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(instanceIdKey, PersistentDataType.STRING);
        if (stored != null) {
            try {
                return UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {
                // Replace invalid data below.
            }
        }
        UUID created = UUID.randomUUID();
        meta.getPersistentDataContainer().set(instanceIdKey, PersistentDataType.STRING, created.toString());
        item.setItemMeta(meta);
        return created;
    }

    public void clearInstanceId(ItemStack item) {
        if (!item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(instanceIdKey);
        item.setItemMeta(meta);
    }
}
