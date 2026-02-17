package me.bintanq.quantum.models;

import java.util.UUID;

public class Jail {
    private final UUID uuid;
    private final String playerName;
    private final String cellName;
    private final String reason;
    private final String staff;
    private final long timestamp;
    private long expires;
    private final int laborRequired;
    private int laborProgress;

    public Jail(UUID uuid, String playerName, String cellName, String reason,
                String staff, long timestamp, long expires, int laborRequired, int laborProgress) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.cellName = cellName;
        this.reason = reason;
        this.staff = staff;
        this.timestamp = timestamp;
        this.expires = expires;
        this.laborRequired = laborRequired;
        this.laborProgress = laborProgress;
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public String getCellName() { return cellName; }
    public String getReason() { return reason; }
    public String getStaff() { return staff; }
    public long getTimestamp() { return timestamp; }
    public long getExpires() { return expires; }
    public void setExpires(long expires) { this.expires = expires; }
    public int getLaborRequired() { return laborRequired; }
    public int getLaborProgress() { return laborProgress; }

    public void incrementLabor() { this.laborProgress++; }
    public boolean isLaborComplete() { return laborProgress >= laborRequired; }
    public boolean isTimeExpired() { return System.currentTimeMillis() >= expires; }

    public boolean canBeReleased() {
        if (laborRequired > 0) {
            return isLaborComplete() && isTimeExpired();
        }
        return isTimeExpired();
    }
}