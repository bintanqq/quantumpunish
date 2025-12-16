package me.bintanq.quantum.models;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final String lastName;
    private final String ipAddresses;
    private final long firstJoin;
    private final long lastSeen;

    public PlayerData(UUID uuid, String lastName, String ipAddresses, long firstJoin, long lastSeen) {
        this.uuid = uuid;
        this.lastName = lastName;
        this.ipAddresses = ipAddresses;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid() { return uuid; }
    public String getLastName() { return lastName; }
    public String getIpAddresses() { return ipAddresses; }
    public long getFirstJoin() { return firstJoin; }
    public long getLastSeen() { return lastSeen; }
}