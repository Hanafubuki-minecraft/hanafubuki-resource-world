package com.hanafubuki.resourceworld;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Портал в мир ресурсов, работающий как портал в Незер: стоишь в нём — экран качает, звучит гул,
 * через charge-seconds телепорт. Вышел раньше — ничего не происходит.
 * Область — любой параллелепипед: вертикальная арка или горизонтальный «пол».
 */
public final class Portal {

    public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= minX && l.getBlockX() <= maxX
                    && l.getBlockY() >= minY && l.getBlockY() <= maxY
                    && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
        }
    }

    /** Игрок стоит в портале: когда начал и куда полетит (точку ищем заранее, пока качает). */
    private record Charge(long startedAt, CompletableFuture<Location> target) {
    }

    private final ResourceWorldPlugin plugin;
    private final Messages msg;
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Charge> charging = new ConcurrentHashMap<>();
    private Region region;
    private BukkitTask task;
    private long tick;

    public Portal(ResourceWorldPlugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    // ---------- настройка ----------

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

    public boolean save(Player p) {
        Location a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
        saveBox(a, b);
        return true;
    }

    /**
     * Найти проём рамки вокруг игрока, как игра находит рамку портала в Незер:
     * заливка по проходимым блокам в двух вертикальных плоскостях; рамка — та плоскость, где заливка замкнута.
     * Возвращает размер «ширина×высота» или null, если замкнутой рамки нет.
     */
    public String detect(Player p) {
        Block start = p.getLocation().getBlock();
        if (!start.isPassable()) return null;
        int limit = 21 * 21;
        Set<Block> best = null;
        for (int axis : new int[]{0, 2}) {                 // 0: плоскость X-Y, 2: плоскость Z-Y
            Set<Block> filled = fill(start, axis, limit);
            if (filled != null && (best == null || filled.size() < best.size())) best = filled;
        }
        if (best == null) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Block b : best) {
            minX = Math.min(minX, b.getX()); minY = Math.min(minY, b.getY()); minZ = Math.min(minZ, b.getZ());
            maxX = Math.max(maxX, b.getX()); maxY = Math.max(maxY, b.getY()); maxZ = Math.max(maxZ, b.getZ());
        }
        World w = start.getWorld();
        saveBox(new Location(w, minX, minY, minZ), new Location(w, maxX, maxY, maxZ));
        int width = Math.max(maxX - minX, maxZ - minZ) + 1;
        return width + "×" + (maxY - minY + 1);
    }

    private static Set<Block> fill(Block start, int axis, int limit) {
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int[][] dirs = axis == 0
                ? new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}}
                : new int[][]{{0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        while (!queue.isEmpty()) {
            Block b = queue.poll();
            for (int[] d : dirs) {
                Block n = b.getRelative(d[0], d[1], d[2]);
                if (seen.contains(n) || !n.isPassable()) continue;
                seen.add(n);
                if (seen.size() > limit) return null;     // не замкнуто — это не рамка
                queue.add(n);
            }
        }
        return seen;
    }

    private void saveBox(Location a, Location b) {
        FileConfiguration c = plugin.getConfig();
        c.set("portal.region.world", a.getWorld().getName());
        c.set("portal.region.min-x", Math.min(a.getBlockX(), b.getBlockX()));
        c.set("portal.region.min-y", Math.min(a.getBlockY(), b.getBlockY()));
        c.set("portal.region.min-z", Math.min(a.getBlockZ(), b.getBlockZ()));
        c.set("portal.region.max-x", Math.max(a.getBlockX(), b.getBlockX()));
        c.set("portal.region.max-y", Math.max(a.getBlockY(), b.getBlockY()));
        c.set("portal.region.max-z", Math.max(a.getBlockZ(), b.getBlockZ()));
        c.set("portal.enabled", true);
        plugin.saveConfig();
        load(c);
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

    // ---------- работа портала ----------

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : charging.keySet()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) cancelEffects(p);
        }
        charging.clear();
    }

    /** Ноги в области — или игрок стоит на ней (горизонтальный портал-пол). */
    private boolean inside(Location feet) {
        if (region == null || feet == null) return false;
        return region.contains(feet) || region.contains(feet.clone().subtract(0, 1, 0));
    }

    private boolean inside(Player p) {
        return inside(p.getLocation());
    }

    /**
     * Из PlayerMoveEvent: зашёл в портал — начинаем «качать».
     * Проверяем именно новую позицию (to): во время события p.getLocation() — ещё старая.
     */
    public void onMove(Player p, Location to) {
        if (!enabled()) return;
        UUID id = p.getUniqueId();
        if (charging.containsKey(id) || !inside(to)) return;
        long cd = plugin.getConfig().getLong("portal.cooldown-seconds", 5) * 1000L;
        if (System.currentTimeMillis() - cooldown.getOrDefault(id, 0L) < cd) return;

        World rw = plugin.resourceWorld().world();
        if (rw == null) {
            msg.send(p, "world-missing");
            cooldown.put(id, System.currentTimeMillis());
            return;
        }
        var c = plugin.getConfig();
        CompletableFuture<Location> target = SafeLocationFinder.find(rw,
                c.getInt("random-teleport.min-radius", 200), c.getInt("random-teleport.radius", 3000),
                c.getInt("random-teleport.max-attempts", 12));
        charging.put(id, new Charge(System.currentTimeMillis(), target));

        int chargeTicks = chargeMillis() / 50;
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, chargeTicks + 60, 0, true, false, false));
        p.playSound(p, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.15f);
    }

    private int chargeMillis() {
        return (int) Math.max(0, plugin.getConfig().getDouble("portal.charge-seconds", 4) * 1000);
    }

    private void tick() {
        tick += 2;
        long now = System.currentTimeMillis();
        int charge = chargeMillis();

        for (Iterator<Map.Entry<UUID, Charge>> it = charging.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Charge> e = it.next();
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                it.remove();
                continue;
            }
            if (!enabled() || !inside(p)) {          // вышел раньше времени — отмена
                it.remove();
                cancelEffects(p);
                continue;
            }
            Location l = p.getLocation().add(0, 1, 0);
            p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l, 3, 0.4, 0.8, 0.4, 0);

            if (now - e.getValue().startedAt() < charge) continue;
            CompletableFuture<Location> target = e.getValue().target();
            if (!target.isDone()) continue;          // точка ещё ищется — ждём, качка продолжается

            it.remove();
            cooldown.put(p.getUniqueId(), now);
            Location dest = target.getNow(null);
            if (dest == null) {
                cancelEffects(p);
                msg.send(p, "teleport-failed");
                continue;
            }
            p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l, 60, 0.6, 1, 0.6, 0);
            p.teleportAsync(dest).thenRun(() -> {
                p.stopSound(Sound.BLOCK_PORTAL_TRIGGER);
                p.playSound(p, Sound.BLOCK_PORTAL_TRAVEL, 0.35f, 1.3f);
                dest.getWorld().spawnParticle(Particle.CHERRY_LEAVES, dest.clone().add(0, 1, 0), 60, 0.6, 1, 0.6, 0);
                plugin.resourceWorld().sendCountdown(p);
            });
        }

        playAmbient();
    }

    private void cancelEffects(Player p) {
        p.removePotionEffect(PotionEffectType.NAUSEA);
        p.stopSound(Sound.BLOCK_PORTAL_TRIGGER);
    }

    // ---------- визуал ----------

    /**
     * Визуал: тонкая переливающаяся «плёнка» ровно в плоскости проёма, искорки по краю
     * и редкие лепестки по всей высоте. Показывается, только если рядом кто-то есть.
     */
    private void playAmbient() {
        var c = plugin.getConfig();
        if (!enabled() || !c.getBoolean("portal.effects.enabled", true)) return;
        World w = plugin.getServer().getWorld(region.world());
        if (w == null) return;

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
        // самая тонкая ось — это «толщина» портала; плёнка лежит посередине неё
        int thin = dx <= dy && dx <= dz ? 0 : (dz <= dy ? 2 : 1);

        if (c.getBoolean("portal.effects.veil", true)) {
            Color from = parseColor(c.getString("portal.effects.veil-color", "#F7A1C0"));
            Color to = parseColor(c.getString("portal.effects.veil-color-2", "#FFFFFF"));
            Particle.DustTransition dust = new Particle.DustTransition(from, to, 0.7f);
            double area = thin == 0 ? dy * dz : thin == 1 ? dx * dz : dx * dy;
            int points = (int) Math.max(3, Math.round(area * c.getDouble("portal.effects.veil-density", 2.0)));
            for (int i = 0; i < points; i++) {
                double[] pt = pointOnPlane(r, thin, x0, y0, z0, dx, dy, dz, false);
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pt[0], pt[1], pt[2], 1, 0, 0, 0, 0, dust);
            }
        }

        if (c.getBoolean("portal.effects.sparkles", true) && r.nextInt(3) == 0) {
            double[] pt = pointOnPlane(r, thin, x0, y0, z0, dx, dy, dz, true);
            w.spawnParticle(Particle.WAX_OFF, pt[0], pt[1], pt[2], 1, 0, 0, 0, 0);
        }

        if (c.getBoolean("portal.effects.petals", true) && r.nextInt(4) == 0) {
            double[] pt = pointOnPlane(r, thin, x0, y0, z0, dx, dy, dz, false);
            w.spawnParticle(Particle.CHERRY_LEAVES, pt[0], pt[1], pt[2], 1, 0, 0, 0, 0);
        }

        if (c.getBoolean("portal.effects.sound", true) && tick % 120 == 0 && r.nextInt(3) == 0) {
            w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.8f + r.nextFloat() * 0.4f);
        }
    }

    /** Случайная точка в плоскости проёма (или на её краю — для искорок). */
    private static double[] pointOnPlane(ThreadLocalRandom r, int thin, double x0, double y0, double z0,
                                         double dx, double dy, double dz, boolean edge) {
        double[] p = {x0 + r.nextDouble() * dx, y0 + r.nextDouble() * dy, z0 + r.nextDouble() * dz};
        double[] o = {x0, y0, z0}, size = {dx, dy, dz};
        p[thin] = o[thin] + size[thin] / 2 + (r.nextDouble() - 0.5) * 0.12;
        if (edge) {
            // прижать к одной из сторон проёма
            int axis = thin == 0 ? (r.nextBoolean() ? 1 : 2) : thin == 1 ? (r.nextBoolean() ? 0 : 2) : (r.nextBoolean() ? 0 : 1);
            p[axis] = o[axis] + (r.nextBoolean() ? 0.08 : size[axis] - 0.08);
        }
        return p;
    }

    private static Color parseColor(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (Exception e) {
            return Color.fromRGB(0xF7A1C0);
        }
    }
}
