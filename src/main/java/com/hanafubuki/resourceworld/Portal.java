package com.hanafubuki.resourceworld;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
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
        plugin.saveConfig();
        load(c);
        return true;
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
