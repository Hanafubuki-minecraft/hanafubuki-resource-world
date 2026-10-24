package com.hanafubuki.resourceworld;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

/** Необязательный портал: вошёл в область — оказался в мире ресурсов. Для серверов без отдельной команды телепорта. */
public final class Portal {

    public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= minX && l.getBlockX() <= maxX
                    && l.getBlockY() >= minY && l.getBlockY() <= maxY
                    && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
        }
    }

    private final ResourceWorldPlugin plugin;
    private final Messages msg;
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> busy = new ConcurrentHashMap<>();
    private Region region;
    private BukkitTask effectsTask;
    private long effectTick;

    public Portal(ResourceWorldPlugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    public void load(FileConfiguration c) {
        ConfigurationSection s = c.getConfigurationSection("portal.region");
        region = s == null || s.getString("world") == null ? null : new Region(
                s.getString("world"), s.getInt("min-x"), s.getInt("min-y"), s.getInt("min-z"),
                s.getInt("max-x"), s.getInt("max-y"), s.getInt("max-z"));
    }

    /** Визуал портала: лепестки сыплются сверху проёма, в самом проёме — розовая дымка, иногда звон. */
    public void startEffects() {
        if (effectsTask != null) effectsTask.cancel();
        effectsTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::playEffects, 20L, 3L);
    }

    public void stopEffects() {
        if (effectsTask != null) effectsTask.cancel();
        effectsTask = null;
    }

    private void playEffects() {
        effectTick += 3;
        var c = plugin.getConfig();
        if (!enabled() || !c.getBoolean("portal.effects.enabled", true)) return;
        World w = plugin.getServer().getWorld(region.world());
        if (w == null) return;

        // проём в координатах мира: блоки min..max включительно → объём min..max+1
        double x0 = region.minX(), y0 = region.minY(), z0 = region.minZ();
        double dx = region.maxX() - region.minX() + 1, dy = region.maxY() - region.minY() + 1, dz = region.maxZ() - region.minZ() + 1;
        Location center = new Location(w, x0 + dx / 2, y0 + dy / 2, z0 + dz / 2);

        double view = c.getDouble("portal.effects.view-distance", 32);
        boolean someoneNear = false;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= view * view) { someoneNear = true; break; }
        }
        if (!someoneNear) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (c.getBoolean("portal.effects.petals", true)) {
            int petals = (int) Math.max(1, Math.round(dx * dz * 0.9));
            for (int i = 0; i < petals; i++) {
                w.spawnParticle(Particle.CHERRY_LEAVES,
                        x0 + r.nextDouble() * dx, y0 + dy - 0.1, z0 + r.nextDouble() * dz, 1, 0, 0, 0, 0);
            }
        }

        if (c.getBoolean("portal.effects.veil", true)) {
            Particle.DustOptions dust = new Particle.DustOptions(parseColor(c.getString("portal.effects.veil-color", "#F7A1C0")), 0.9f);
            int points = (int) Math.max(2, Math.round(dx * dy * dz * 1.2));
            for (int i = 0; i < points; i++) {
                w.spawnParticle(Particle.DUST,
                        x0 + r.nextDouble() * dx, y0 + r.nextDouble() * dy, z0 + r.nextDouble() * dz, 1, 0, 0, 0, 0, dust);
            }
        }

        if (c.getBoolean("portal.effects.sound", true) && effectTick % 60 == 0 && r.nextInt(3) == 0) {
            w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f + r.nextFloat() * 0.4f);
        }
    }

    private static Color parseColor(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (Exception e) {
            return Color.fromRGB(0xF7A1C0);
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("portal.enabled", false) && region != null;
    }

    public Region region() {
        return region;
    }

    public void setPos(Player p, boolean first, Location l) {
        (first ? pos1 : pos2).put(p.getUniqueId(), l);
    }

    /** Сохранить область из pos1/pos2 в config.yml. */
    public boolean save(Player p) {
        Location a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
        FileConfiguration c = plugin.getConfig();
        c.set("portal.region.world", a.getWorld().getName());
        c.set("portal.region.min-x", Math.min(a.getBlockX(), b.getBlockX()));
        c.set("portal.region.min-y", Math.min(a.getBlockY(), b.getBlockY()));
        c.set("portal.region.min-z", Math.min(a.getBlockZ(), b.getBlockZ()));
        c.set("portal.region.max-x", Math.max(a.getBlockX(), b.getBlockX()));
        c.set("portal.region.max-y", Math.max(a.getBlockY(), b.getBlockY()));
        c.set("portal.region.max-z", Math.max(a.getBlockZ(), b.getBlockZ()));
        c.set("portal.enabled", true); // задал область — значит, портал нужен
        plugin.saveConfig();
        load(c);
        return true;
    }

    public void setEnabled(boolean on) {
        plugin.getConfig().set("portal.enabled", on);
        plugin.saveConfig();
    }

    public void clear() {
        plugin.getConfig().set("portal.region", null);
        plugin.saveConfig();
        region = null;
    }

    /** Вызывается из PlayerMoveEvent при смене блока. */
    public void onEnter(Player p, Location to) {
        if (!enabled() || !region.contains(to)) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("portal.cooldown-seconds", 5) * 1000L;
        if (busy.containsKey(id) || now - cooldown.getOrDefault(id, 0L) < cd) return;

        World rw = plugin.resourceWorld().world();
        if (rw == null) {
            msg.send(p, "world-missing");
            cooldown.put(id, now);
            return;
        }
        busy.put(id, true);
        msg.send(p, "teleport-searching");
        var c = plugin.getConfig();
        SafeLocationFinder.find(rw, c.getInt("random-teleport.min-radius", 200),
                c.getInt("random-teleport.radius", 3000), c.getInt("random-teleport.max-attempts", 12))
                .whenComplete((loc, err) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    busy.remove(id);
                    cooldown.put(id, System.currentTimeMillis());
                    if (!p.isOnline()) return;
                    if (loc == null) {
                        msg.send(p, "teleport-failed");
                        return;
                    }
                    p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0);
                    p.teleportAsync(loc).thenRun(() -> {
                        p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                        loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 1, 0), 60, 0.6, 1, 0.6, 0);
                        plugin.resourceWorld().sendCountdown(p);
                    });
                }));
    }
}
