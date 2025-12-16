package me.bintanq.quantum.managers;

import me.bintanq.quantum.QuantumPunish;

public class ConfigManager {
    private final QuantumPunish plugin;

    public ConfigManager(QuantumPunish plugin) {
        this.plugin = plugin;
    }

    public boolean isIdSystemEnabled() {
        return plugin.getConfig().getBoolean("id-system.enabled", false);
    }

    public String getIdForBan(String id) {
        return plugin.getConfig().getString("id-system.bans." + id);
    }

    public String getIdForMute(String id) {
        return plugin.getConfig().getString("id-system.mutes." + id);
    }
}