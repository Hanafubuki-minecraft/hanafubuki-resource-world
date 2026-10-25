package com.hanafubuki.resourceworld;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class ResourceWorldPlugin extends JavaPlugin {

    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "ru");

    private Messages messages;
    private ResourceWorld resourceWorld;
    private Portal portal;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        for (String lang : BUNDLED_LANGUAGES) {
            if (!new File(getDataFolder(), "lang/" + lang + ".yml").exists()) saveResource("lang/" + lang + ".yml", false);
        }

        messages = new Messages(this);
        messages.load(getConfig().getString("language", "en"));
        resourceWorld = new ResourceWorld(this, messages);
        portal = new Portal(this, messages);
        portal.load(getConfig());
        portal.start();

        resourceWorld.start();
        getServer().getPluginManager().registerEvents(new RulesListener(this, messages), this);
        getServer().getScheduler().runTaskTimer(this, resourceWorld::tick, 1200L, 1200L);

        // имена, алиасы и включение — config.yml → commands
        AdminCommand admin = new AdminCommand(this, messages);
        Commands.register(this, "rwadmin", "Manage the resource world", "hanafubuki.rw.admin", List.of(), admin, admin);
        Commands.register(this, "rwtime", "Time left until the resource world resets", "hanafubuki.rw.info", List.of(),
                new InfoCommand(this, messages), (s, c, l, a) -> List.of());
    }

    @Override
    public void onDisable() {
        if (portal != null) portal.stop();
    }

    public ResourceWorld resourceWorld() {
        return resourceWorld;
    }

    public Portal portal() {
        return portal;
    }

    public void reloadAll() {
        reloadConfig();
        messages.load(getConfig().getString("language", "en"));
        portal.load(getConfig());
    }

    public boolean setLanguage(String code) {
        if (!availableLanguages().contains(code)) return false;
        getConfig().set("language", code);
        saveConfig();
        reloadAll();
        return true;
    }

    public List<String> availableLanguages() {
        Set<String> result = new TreeSet<>(BUNDLED_LANGUAGES);
        File[] files = new File(getDataFolder(), "lang").listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) result.add(f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT));
        return List.copyOf(result);
    }
}
