package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class AppealCommand implements CommandExecutor {
    private final QuantumPunish plugin;

    public AppealCommand(QuantumPunish plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getCommand("appeal").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
            return true;
        }

        if (!plugin.getConfig().getBoolean("appeal-system.enabled")) {
            player.sendMessage(plugin.getMessageManager().getMessage("unknown-subcommand"));
            return true;
        }

        // Cek apakah player sedang di-Mute
        if (!plugin.getPunishmentService().isMuted(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("appeal-not-punished"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.getMessageManager().getMessage("usage.appeal"));
            return true;
        }

        if (plugin.getAppealService().hasPendingAppeal(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("appeal-cooldown"));
            return true;
        }

        String reason = String.join(" ", args);
        boolean success = plugin.getAppealService().createAppeal(player, reason);

        if (success) {
            player.sendMessage(plugin.getMessageManager().getMessage("appeal-submitted"));

            if (plugin.getConfig().getBoolean("appeal-system.notify-staff", true)) {
                String notifyMsg = plugin.getMessageManager().getMessage("appeal-notify-staff")
                        .replace("%player%", player.getName());

                plugin.getServer().getOnlinePlayers().stream()
                        .filter(staff -> staff.hasPermission("quantumpunish.notify"))
                        .forEach(staff -> {
                            staff.sendMessage(notifyMsg);
                            staff.playSound(staff.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        });
            }
        } else {
            player.sendMessage(plugin.getMessageManager().getMessage("appeal-cooldown"));
        }

        return true;
    }
}