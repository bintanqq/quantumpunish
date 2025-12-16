package me.bintanq.quantum.models;

import java.util.UUID;

public class Punishment {
    private final UUID uuid;
    private final String playerName;
    private final PunishmentType type;
    private final String reason;
    private final String staff;
    private final long timestamp;
    private final Long expires;
    private final String ipAddress;

    public Punishment(UUID uuid, String playerName, PunishmentType type, String reason,
                      String staff, long timestamp, Long expires, String ipAddress) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.type = type;
        this.reason = reason;
        this.staff = staff;
        this.timestamp = timestamp;
        this.expires = expires;
        this.ipAddress = ipAddress;
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public PunishmentType getType() { return type; }
    public String getReason() { return reason; }
    public String getStaff() { return staff; }
    public long getTimestamp() { return timestamp; }
    public Long getExpires() { return expires; }
    public String getIpAddress() { return ipAddress; }
}