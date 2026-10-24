package com.hanafubuki.resourceworld;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.time.Duration;

public final class RulesListener implements Listener {

    private final ResourceWorldPlugin plugin;
    private final Messages msg;

    public RulesListener(ResourceWorldPlugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    private ResourceWorld rw() {
        return plugin.resourceWorld();
    }

    /** Кровать и якорь в мире ресурсов не меняют точку возрождения (а значит и /home). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSetSpawn(PlayerSetSpawnEvent e) {
        if (!plugin.getConfig().getBoolean("rules.block-bed-spawn", true)) return;
        if (e.getLocation() == null || !rw().is(e.getLocation().getWorld())) return;
        e.setCancelled(true);
        msg.send(e.getPlayer(), "bed-blocked");
    }

    /** Порталы в Незер/Энд из мира ресурсов не работают — иначе это короткая дорога в чужие районы. */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (!plugin.getConfig().getBoolean("rules.block-nether-portals", true)) return;
        if (!rw().is(e.getFrom().getWorld())) return;
        e.setCancelled(true);
        msg.send(e.getPlayer(), "portal-blocked");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (plugin.getConfig().getBoolean("rules.block-nether-portals", true) && rw().is(e.getFrom().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (plugin.getConfig().getBoolean("rules.block-nether-portals", true) && rw().is(e.getWorld())) {
            e.setCancelled(true);
        }
    }

    /** Личное напоминание о сбросе при входе — начиная за N дней. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        int days = plugin.getConfig().getInt("reset.notify-before-days", 3);
        if (days <= 0) return;
        if (rw().untilReset().compareTo(Duration.ofDays(days)) <= 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (e.getPlayer().isOnline()) rw().sendCountdown(e.getPlayer());
            }, 40L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        plugin.portal().onEnter(e.getPlayer(), e.getTo());
    }
}
