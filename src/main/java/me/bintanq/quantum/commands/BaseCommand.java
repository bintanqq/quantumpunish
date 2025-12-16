package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public abstract class BaseCommand implements CommandExecutor, TabCompleter {
    protected final QuantumPunish plugin;

    public BaseCommand(QuantumPunish plugin) {
        this.plugin = plugin;
    }

    public void register() {
        PluginCommand command = plugin.getCommand(getCommandName());
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    protected abstract String getCommandName();
}