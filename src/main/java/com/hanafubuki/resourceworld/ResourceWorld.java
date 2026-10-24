package com.hanafubuki.resourceworld;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.hanafubuki.resourceworld.Messages.ph;

/** Жизненный цикл мира ресурсов: загрузка, сброс по расписанию, время до сброса. */
public final class ResourceWorld {

    private final ResourceWorldPlugin plugin;
    private final Messages msg;
    private final File dataFile;
    private Instant lastReset;

    public ResourceWorld(ResourceWorldPlugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public String name() {
        return plugin.getConfig().getString("world.name", "resources");
    }

    public World world() {
        return Bukkit.getWorld(name());
    }

    public boolean is(World w) {
        return w != null && w.getName().equals(name());
    }

    public Duration interval() {
        return Duration.ofDays(Math.max(1, plugin.getConfig().getInt("reset.interval-days", 30)));
    }

    public Instant nextReset() {
        return lastReset.plus(interval());
    }

    public Duration untilReset() {
        Duration d = Duration.between(Instant.now(), nextReset());
        return d.isNegative() ? Duration.ZERO : d;
    }

    /** При старте сервера: загрузить мир (или сразу сбросить, если срок вышел). */
    public void start() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        long last = data.getLong("last-reset", 0);
        if (last == 0) {
            lastReset = Instant.now();
            saveData();
        } else {
            lastReset = Instant.ofEpochMilli(last);
        }
        if (Instant.now().isAfter(nextReset())) {
            plugin.getLogger().info("Resource world reset is due — resetting now");
            reset();
        } else {
            create(null);
        }
    }

    /** Раз в минуту: не пора ли сбросить. */
    public void tick() {
        if (Instant.now().isAfter(nextReset())) reset();
    }

    public void reset() {
        World old = world();
        if (old != null) {
            World main = Bukkit.getWorlds().get(0);
            for (Player p : old.getPlayers()) {
                p.teleport(main.getSpawnLocation());
                msg.send(p, "reset-kicked");
            }
            File folder = old.getWorldFolder();
            if (!Bukkit.unloadWorld(old, false)) {
                plugin.getLogger().severe("Could not unload resource world, reset skipped");
                return;
            }
            deleteRecursively(folder.toPath());
        }
        create(ThreadLocalRandom.current().nextLong());
        lastReset = Instant.now();
        saveData();
        for (Player p : Bukkit.getOnlinePlayers()) msg.send(p, "reset-done");
        plugin.getLogger().info("Resource world has been reset");
        pregenerate();
        purgeBlueMap();
    }

    private void create(Long seed) {
        WorldCreator creator = new WorldCreator(name());
        String env = plugin.getConfig().getString("world.environment", "NORMAL");
        try {
            creator.environment(World.Environment.valueOf(env.toUpperCase()));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown world.environment '" + env + "', using NORMAL");
        }
        if (seed != null) creator.seed(seed);
        World w = creator.createWorld();
        if (w == null) {
            plugin.getLogger().severe("Could not create resource world '" + name() + "'");
            return;
        }
        int border = plugin.getConfig().getInt("world.border-size", 8000);
        if (border > 0) {
            w.getWorldBorder().setCenter(w.getSpawnLocation());
            w.getWorldBorder().setSize(border);
        }
    }

    private void pregenerate() {
        if (!plugin.getConfig().getBoolean("reset.pregenerate-with-chunky", false)) return;
        if (Bukkit.getPluginManager().getPlugin("Chunky") == null) {
            plugin.getLogger().warning("reset.pregenerate-with-chunky is on, but Chunky is not installed");
            return;
        }
        int radius = plugin.getConfig().getInt("random-teleport.radius", 3000);
        var console = Bukkit.getConsoleSender();
        Bukkit.dispatchCommand(console, "chunky world " + name());
        Bukkit.dispatchCommand(console, "chunky radius " + radius);
        Bukkit.dispatchCommand(console, "chunky start");
    }

    /** Стереть старые тайлы карты BlueMap — иначе на ней останется прошлый мир. */
    private void purgeBlueMap() {
        String mapId = plugin.getConfig().getString("bluemap.map-id", "");
        if (mapId == null || mapId.isBlank()) return;
        if (Bukkit.getPluginManager().getPlugin("BlueMap") == null) return;
        var console = Bukkit.getConsoleSender();
        // reload — BlueMap заново цепляется к пересозданной папке мира; purge — чистит тайлы, рендер пойдёт заново
        Bukkit.dispatchCommand(console, "bluemap reload light");
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.dispatchCommand(console, "bluemap purge " + mapId), 100L);
    }

    private void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("last-reset", lastReset.toEpochMilli());
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    private void deleteRecursively(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not delete " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            plugin.getLogger().severe("Could not delete old resource world: " + e.getMessage());
        }
    }

    /** «2д 5ч 13м» на языке сервера. */
    public String format(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        return msg.raw("time-format")
                .replace("<days>", String.valueOf(days))
                .replace("<hours>", String.valueOf(hours))
                .replace("<minutes>", String.valueOf(minutes));
    }

    public void sendCountdown(Player p) {
        msg.send(p, "reset-countdown", ph("time", format(untilReset())));
    }
}
