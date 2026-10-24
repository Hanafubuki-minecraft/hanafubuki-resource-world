package com.hanafubuki.resourceworld;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration lang = new YamlConfiguration();
    private String code = "en";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String requested) {
        String c = requested == null ? "en" : requested.toLowerCase();
        File file = new File(plugin.getDataFolder(), "lang/" + c + ".yml");
        if (!file.exists() && plugin.getResource("lang/" + c + ".yml") == null) {
            plugin.getLogger().warning("Language '" + c + "' not found, falling back to 'en'");
            c = "en";
            file = new File(plugin.getDataFolder(), "lang/en.yml");
        }

        YamlConfiguration loaded = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        // недостающие ключи берём из встроенного файла этого языка, а если его нет — из английского
        InputStream bundled = plugin.getResource("lang/" + c + ".yml");
        if (bundled == null) bundled = plugin.getResource("lang/en.yml");
        if (bundled != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }

        this.lang = loaded;
        this.code = c;
    }

    public String code() {
        return code;
    }

    public String raw(String key) {
        return lang.getString(key, key);
    }

    /** Сообщение с префиксом. */
    public void send(CommandSender to, String key, TagResolver... placeholders) {
        to.sendMessage(mm.deserialize(raw("prefix") + raw(key), placeholders));
    }

    /** Строка без префикса (для списков и помощи). */
    public void line(CommandSender to, String key, TagResolver... placeholders) {
        to.sendMessage(mm.deserialize(raw(key), placeholders));
    }

    public Component component(String key, TagResolver... placeholders) {
        return mm.deserialize(raw(key), placeholders);
    }

    /** Плейсхолдер без разбора MiniMessage — пользовательский текст не может внедрить теги. */
    public static TagResolver ph(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
