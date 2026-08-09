package dev.locators.filter;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

final class LuckPermsGroupResolver implements TargetGroupResolver {
    private final PlayerAdapter<Player> playerAdapter;

    LuckPermsGroupResolver() {
        RegisteredServiceProvider<LuckPerms> registration =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("LuckPerms не зарегистрировал API в ServicesManager");
        }
        playerAdapter = registration.getProvider().getPlayerAdapter(Player.class);
    }

    @Override
    public boolean isMember(Player player, String groupName) {
        User user = playerAdapter.getUser(player);
        if (user.getPrimaryGroup().equalsIgnoreCase(groupName)) {
            return true;
        }
        QueryOptions queryOptions = playerAdapter.getQueryOptions(player);
        for (Group group : user.getInheritedGroups(queryOptions)) {
            if (group.getName().equalsIgnoreCase(groupName)) {
                return true;
            }
        }
        return false;
    }
}
