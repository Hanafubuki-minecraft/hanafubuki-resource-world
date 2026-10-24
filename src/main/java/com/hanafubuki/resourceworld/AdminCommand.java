package com.hanafubuki.resourceworld;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.hanafubuki.resourceworld.Messages.ph;

public final class AdminCommand implements TabExecutor {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ResourceWorldPlugin plugin;
    private final Messages msg;
    private final Map<String, Long> confirm = new HashMap<>();

    public AdminCommand(ResourceWorldPlugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                ResourceWorld rw = plugin.resourceWorld();
                msg.send(s, "info", ph("world", rw.name()), ph("time", rw.format(rw.untilReset())),
                        ph("date", DATE.format(rw.nextReset())),
                        ph("portal", plugin.portal().enabled() ? "✔" : "✘"));
            }
            case "reset" -> {
                String key = s.getName();
                long now = System.currentTimeMillis();
                if (now - confirm.getOrDefault(key, 0L) > 15_000) {
                    confirm.put(key, now);
                    msg.send(s, "reset-confirm", ph("label", label));
                } else {
                    confirm.remove(key);
                    plugin.resourceWorld().reset();
                }
            }
            case "portal" -> portal(s, label, a);
            case "language", "lang" -> {
                String available = String.join(", ", plugin.availableLanguages());
                if (a.length < 2) {
                    msg.send(s, "language-current", ph("language", msg.code()), ph("available", available));
                } else if (plugin.setLanguage(a[1].toLowerCase(Locale.ROOT))) {
                    msg.send(s, "language-set", ph("language", a[1].toLowerCase(Locale.ROOT)));
                } else {
                    msg.send(s, "language-unknown", ph("language", a[1]), ph("available", available));
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                msg.send(s, "reloaded");
            }
            default -> help(s, label);
        }
        return true;
    }

    private void portal(CommandSender s, String label, String[] a) {
        if (!(s instanceof Player p)) {
            msg.send(s, "players-only");
            return;
        }
        String action = a.length < 2 ? "" : a[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "pos1", "pos2" -> {
                Block b = p.getTargetBlockExact(6);
                Location l = b != null ? b.getLocation() : p.getLocation().getBlock().getLocation();
                plugin.portal().setPos(p, action.equals("pos1"), l);
                msg.send(s, "portal-pos-set", ph("pos", action),
                        ph("xyz", l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
            }
            case "save" -> msg.send(s, plugin.portal().save(p) ? "portal-saved" : "portal-need-positions", ph("label", label));
            case "clear" -> {
                plugin.portal().clear();
                msg.send(s, "portal-cleared");
            }
            case "enable", "disable" -> {
                boolean on = action.equals("enable");
                if (on && plugin.portal().region() == null) {
                    msg.send(s, "portal-need-positions");
                    return;
                }
                plugin.portal().setEnabled(on);
                msg.send(s, on ? "portal-enabled" : "portal-disabled");
            }
            default -> msg.send(s, "usage-portal", ph("label", label));
        }
    }

    private void help(CommandSender s, String label) {
        msg.send(s, "help-header");
        for (String k : List.of("help-info", "help-reset", "help-portal", "help-language", "help-reload")) {
            msg.line(s, k, ph("label", label));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1) return filter(List.of("info", "reset", "portal", "language", "reload"), a[0]);
        if (a.length == 2 && a[0].equalsIgnoreCase("portal")) return filter(List.of("pos1", "pos2", "save", "enable", "disable", "clear"), a[1]);
        if (a.length == 2 && (a[0].equalsIgnoreCase("language") || a[0].equalsIgnoreCase("lang"))) {
            return filter(plugin.availableLanguages(), a[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
