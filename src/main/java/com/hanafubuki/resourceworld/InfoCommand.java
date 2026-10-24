package com.hanafubuki.resourceworld;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import static com.hanafubuki.resourceworld.Messages.ph;

/** /rwtime — сколько осталось до сброса мира ресурсов. Доступна всем. */
public final class InfoCommand implements CommandExecutor {

    private final ResourceWorldPlugin plugin;
    private final Messages msg;

    public InfoCommand(ResourceWorldPlugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        ResourceWorld rw = plugin.resourceWorld();
        msg.send(s, "reset-countdown", ph("time", rw.format(rw.untilReset())));
        return true;
    }
}
