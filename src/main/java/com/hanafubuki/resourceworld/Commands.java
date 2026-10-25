package com.hanafubuki.resourceworld;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Регистрирует команды по config.yml → commands.&lt;id&gt;: name, aliases, enabled.
 * Так любую команду можно переименовать, добавить алиасы или выключить. Изменения — после рестарта.
 */
public final class Commands {

    private static final Map<String, Command> REGISTERED = new LinkedHashMap<>();

    private Commands() {
    }

    /**
     * @param id          ключ команды в config.yml
     * @param permission  право (команда скрыта в Tab у тех, у кого его нет), или null
     * @param aliases     алиасы по умолчанию
     */
    public static void register(JavaPlugin plugin, String id, String description, String permission,
                                List<String> aliases, CommandExecutor executor, TabCompleter completer) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("commands." + id);
        if (c != null && !c.getBoolean("enabled", true)) return;
        String name = c != null ? c.getString("name", id) : id;
        List<String> al = c != null && c.isList("aliases") ? c.getStringList("aliases") : aliases;

        Command cmd = new Command(name, description, "/" + name, new ArrayList<>(al)) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (getPermission() != null && !testPermissionSilent(sender)) {
                    sender.sendMessage(Bukkit.permissionMessage());
                    return true;
                }
                return executor.onCommand(sender, this, label, args);
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                List<String> r = completer == null ? null : completer.onTabComplete(sender, this, alias, args);
                return r != null ? r : super.tabComplete(sender, alias, args); // null → ники онлайн-игроков
            }
        };
        if (permission != null) cmd.setPermission(permission);
        Bukkit.getCommandMap().register(plugin.getName().toLowerCase(Locale.ROOT), cmd);
        REGISTERED.put(id, cmd);
    }

    /** Текущее имя команды по её id (учитывает переименование в конфиге). */
    public static String name(String id) {
        Command c = REGISTERED.get(id);
        return c == null ? id : c.getName();
    }

    public static boolean has(String id) {
        return REGISTERED.containsKey(id);
    }
}
