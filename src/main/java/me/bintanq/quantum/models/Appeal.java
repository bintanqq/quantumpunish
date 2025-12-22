package me.bintanq.quantum.models;

import java.util.UUID;

public class Appeal {
    private final int id;
    private final UUID uuid;
    private final String playerName;
    private final String reason;
    private final long timestamp;
    private final String status; // PENDING, APPROVED, REJECTED

    public Appeal(int id, UUID uuid, String playerName, String reason, long timestamp, String status) {
        this.id = id;
        this.uuid = uuid;
        this.playerName = playerName;
        this.reason = reason;
        this.timestamp = timestamp;
        this.status = status;
    }

    public int getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
}