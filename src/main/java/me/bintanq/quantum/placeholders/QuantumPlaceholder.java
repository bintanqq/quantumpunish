package me.bintanq.quantum.placeholders;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Jail;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * QuantumPlaceholder — PlaceholderAPI Expansion for QuantumPunish
 * ============================================================================
 *
 * DATA FLOW OVERVIEW
 * ─────────────────────────────────────────────────────────────────────────────
 *  Placeholder type          | Source               | Thread
 *  ─────────────────────────────────────────────────────────────────
 *  Jail data (time, labor)   | JailService (RAM)    | Main (safe, instant)
 *  Warning points            | WarningService (RAM) | Main (safe, instant)
 *  is_muted                  | PunishmentService    | Main (fast SQL check)
 *  is_banned / counts        | AsyncStatsCache      | Main reads, async writes
 *  staff_total_issued        | AsyncStatsCache      | Main reads, async writes
 *
 * CACHE BEHAVIOUR (stale-while-revalidate)
 * ─────────────────────────────────────────────────────────────────────────────
 *  1. First request → cache MISS → return zeroed snapshot, fire async fetch
 *  2. Async fetch completes → ConcurrentHashMap.put(uuid, freshSnapshot)
 *  3. All subsequent requests → instant ConcurrentHashMap.get (no SQL)
 *  4. After TTL expires → next request fires another background refresh
 *     while still returning the (slightly stale) old value
 *  5. On new punishment → invalidateCache(uuid) drops the entry immediately
 *     so the very next request sees correct data
 *
 * CONFIGURATION (placeholders.yml)
 * ─────────────────────────────────────────────────────────────────────────────
 *  placeholders.is_banned.active / inactive  → display strings for status PH
 *  jail_defaults.*                           → fallback strings for jail fields
 *  time_format.*                             → unit suffixes, separator, zero text
 *  cache.ttl_seconds                         → cache lifetime (default 300 s)
 *
 * PLACEHOLDER REFERENCE
 * ─────────────────────────────────────────────────────────────────────────────
 *  STATUS (configurable active/inactive strings)
 *    %quantum_is_banned%              is_banned  config node
 *    %quantum_is_muted%               is_muted   config node
 *    %quantum_is_jailed%              is_jailed  config node
 *
 *  JAIL (live from JailService RAM cache)
 *    %quantum_jail_active%            jail_active config node ("Yes"/"No")
 *    %quantum_jail_cell%              cell name or jail_defaults.cell_none
 *    %quantum_jail_reason%            reason or jail_defaults.reason_none
 *    %quantum_jail_staff%             staff name or jail_defaults.staff_none
 *    %quantum_jail_labor_done%        blocks broken
 *    %quantum_jail_labor_required%    total blocks needed
 *    %quantum_jail_labor_remaining%   blocks still needed
 *    %quantum_jail_labor_percent%     0-100
 *    %quantum_jail_time_remaining%    formatted via time_format config
 *    %quantum_jail_time_seconds%      raw seconds remaining
 *
 *  WARNINGS (live from WarningService)
 *    %quantum_warn_points%            total accumulated warning points
 *
 *  HISTORY COUNTS (async-cached)
 *    %quantum_total_bans%
 *    %quantum_total_mutes%
 *    %quantum_total_kicks%
 *    %quantum_total_warns%
 *    %quantum_total_jails%
 *    %quantum_total_punishments%
 *
 *  STAFF (async-cached)
 *    %quantum_staff_total_issued%     punishments issued BY this player
 *
 * ============================================================================
 */
public class QuantumPlaceholder extends PlaceholderExpansion {

    // ─────────────────────────────────────────────────────────────────────────
    // Fields
    // ─────────────────────────────────────────────────────────────────────────

    private final QuantumPunish plugin;

    /** Loaded from placeholders.yml — never null after loadPlaceholderConfig(). */
    private FileConfiguration phConfig;

    /** TTL in milliseconds, sourced from placeholders.yml cache.ttl_seconds. */
    private long cacheTtlMs;

    /**
     * Per-player async snapshot. Written exclusively on the async scheduler
     * thread via ConcurrentHashMap.put; read on the main thread via .get.
     * ConcurrentHashMap's happens-before guarantee makes this thread-safe
     * without extra synchronisation.
     */
    private final ConcurrentHashMap<UUID, StatsSnapshot> statsCache = new ConcurrentHashMap<>();

    /**
     * Deduplication guard: holds UUIDs that already have an in-flight fetch.
     * putIfAbsent(uuid, TRUE) returns null only once → exactly one task queued.
     */
    private final ConcurrentHashMap<UUID, Boolean> fetchInProgress = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Construction & PlaceholderExpansion metadata
    // ─────────────────────────────────────────────────────────────────────────

    public QuantumPlaceholder(QuantumPunish plugin) {
        this.plugin = plugin;
        loadPlaceholderConfig();
    }

    @Override public @NotNull String getIdentifier() { return "quantum"; }
    @Override public @NotNull String getAuthor()     { return "bintanq"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    /** Keep this expansion registered across plugin reloads. */
    @Override public boolean persist()               { return true; }

    // ─────────────────────────────────────────────────────────────────────────
    // Configuration loader
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads (or creates) placeholders.yml in the plugin's data folder.
     * Missing keys are filled from the bundled default, so the file is always
     * up-to-date after a plugin upgrade without overwriting user changes.
     *
     * <p>Call this once from the constructor and again inside
     * {@code QuantumPunish.reloadConfigurations()}.
     */
    public void loadPlaceholderConfig() {
        File file = new File(plugin.getDataFolder(), "placeholders.yml");

        // Create the file from the bundled resource if it doesn't exist yet
        if (!file.exists()) {
            plugin.saveResource("placeholders.yml", false);
        }

        // Load what's on disk
        FileConfiguration diskConfig = YamlConfiguration.loadConfiguration(file);

        // Merge any keys that exist in the bundled default but are missing on disk
        InputStream defaultStream = plugin.getResource("placeholders.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            boolean dirty = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!diskConfig.contains(key)) {
                    diskConfig.set(key, defaultConfig.get(key));
                    dirty = true;
                }
            }
            if (dirty) {
                try {
                    diskConfig.save(file);
                    plugin.getLogger().info("[QuantumPAPI] Updated placeholders.yml with new default keys.");
                } catch (IOException e) {
                    plugin.getLogger().warning("[QuantumPAPI] Could not save updated placeholders.yml: " + e.getMessage());
                }
            }
        }

        phConfig = diskConfig;

        // Resolve TTL (clamp to a sane minimum of 10 s)
        long ttlSecs = Math.max(10L, phConfig.getLong("cache.ttl_seconds", 300L));
        cacheTtlMs = ttlSecs * 1000L;

        plugin.getLogger().info("[QuantumPAPI] placeholders.yml loaded (TTL=" + ttlSecs + "s).");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main dispatcher — runs on the Main Server Thread; MUST stay non-blocking
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        final UUID uuid = player.getUniqueId();

        // ── Jail (reads JailService RAM cache — zero DB cost) ─────────────────
        if (params.startsWith("jail_")) {
            return handleJailPlaceholder(uuid, params.substring(5));
        }

        // ── Warning points (reads WarningService — fast) ──────────────────────
        if (params.equals("warn_points")) {
            return String.valueOf(plugin.getWarningService().getWarningPoints(uuid));
        }

        // ── Mute status (PunishmentService — brief SQL check acceptable) ──────
        if (params.equals("is_muted")) {
            return getFormattedStatus("is_muted", plugin.getPunishmentService().isMuted(uuid));
        }

        // ── Jail status alias ─────────────────────────────────────────────────
        if (params.equals("is_jailed")) {
            return getFormattedStatus("is_jailed", isJailedActive(uuid));
        }

        // ── Everything below uses the async cache ─────────────────────────────
        StatsSnapshot snap = getSnapshotOrScheduleFetch(uuid, player.getName());

        return switch (params) {
            case "is_banned"          -> getFormattedStatus("is_banned", snap.isBanned);
            case "total_bans"         -> String.valueOf(snap.totalBans);
            case "total_mutes"        -> String.valueOf(snap.totalMutes);
            case "total_kicks"        -> String.valueOf(snap.totalKicks);
            case "total_warns"        -> String.valueOf(snap.totalWarns);
            case "total_jails"        -> String.valueOf(snap.totalJails);
            case "total_punishments"  -> String.valueOf(snap.totalPunishments);
            case "staff_total_issued" -> String.valueOf(snap.staffTotalIssued);
            default                   -> null; // unknown placeholder — let PAPI handle it
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache management (public API for external callers)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immediately drops the cached snapshot for {@code uuid}.
     *
     * <p><strong>Wire this into your punishment pipeline so counts update
     * instantly instead of waiting for the TTL:</strong></p>
     *
     * <pre>
     * // PunishmentService.savePunishment() — add at the very end:
     * if (plugin.getPapiExpansion() != null)
     *     plugin.getPapiExpansion().invalidateCache(punishment.getUuid());
     *
     * // PunishmentService.unbanPlayer() / unmutePlayer():
     * if (plugin.getPapiExpansion() != null)
     *     plugin.getPapiExpansion().invalidateCache(uuid);
     *
     * // JailService.releasePlayer() — after activeJails.remove(uuid):
     * if (plugin.getPapiExpansion() != null)
     *     plugin.getPapiExpansion().invalidateCache(uuid);
     * </pre>
     *
     * @param uuid the player whose cache entry should be invalidated
     */
    public void invalidateCache(UUID uuid) {
        statsCache.remove(uuid);
        // fetchInProgress is intentionally NOT cleared here — any in-flight task
        // will still write its result, and that result is fine to use temporarily.
    }

    /**
     * Drops ALL cached snapshots — call after {@code /qpunish reload} or when
     * {@link #loadPlaceholderConfig()} is called to refresh the config.
     */
    public void invalidateAll() {
        statsCache.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Async cache internals
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current cached snapshot for {@code uuid}, scheduling an
     * async DB refresh when the entry is absent or expired.
     *
     * <p>This method is called from the main thread and is guaranteed
     * non-blocking; it never performs I/O itself.
     */
    private StatsSnapshot getSnapshotOrScheduleFetch(UUID uuid, @Nullable String playerName) {
        StatsSnapshot existing = statsCache.get(uuid);
        boolean needsRefresh   = (existing == null) || existing.isExpired(cacheTtlMs);

        if (needsRefresh && fetchInProgress.putIfAbsent(uuid, Boolean.TRUE) == null) {
            // Exactly one async task per UUID at a time
            final String nameForQuery = (playerName != null) ? playerName : "";
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    StatsSnapshot fresh = fetchFromDatabase(uuid, nameForQuery);
                    statsCache.put(uuid, fresh);
                } catch (Exception ex) {
                    plugin.getLogger().warning(
                            "[QuantumPAPI] Async fetch failed for " + uuid + ": " + ex.getMessage());
                } finally {
                    fetchInProgress.remove(uuid);
                }
            });
        }

        // Return immediately — stale value or zeroed sentinel; never blocks.
        return (existing != null) ? existing : StatsSnapshot.empty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Database fetch (called ONLY from async scheduler thread)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes all SQL work for one player inside a single connection.
     * Uses a GROUP BY query to count all punishment types in one round-trip
     * instead of N separate queries.
     *
     * <p>Must never be called from the main server thread.
     */
    private StatsSnapshot fetchFromDatabase(UUID uuid, String staffName) throws SQLException {
        final String uuidStr = uuid.toString();

        int  totalBans  = 0, totalMutes = 0, totalKicks = 0,
                totalWarns = 0, totalJails = 0, totalAll   = 0,
                staffTotal = 0;
        boolean isBanned = false;

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {

            // ── Single-query count for all punishment types ───────────────────
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT type, COUNT(*) AS cnt FROM punishments WHERE uuid = ? GROUP BY type")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int cnt = rs.getInt("cnt");
                        totalAll += cnt;
                        switch (rs.getString("type")) {
                            case "BAN"  -> totalBans  = cnt;
                            case "MUTE" -> totalMutes = cnt;
                            case "KICK" -> totalKicks = cnt;
                            case "WARN" -> totalWarns = cnt;
                            case "JAIL" -> totalJails = cnt;
                            // AUTO: counted in totalAll, not exposed separately
                        }
                    }
                }
            }

            // ── Active ban check ──────────────────────────────────────────────
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT expires FROM punishments " +
                            "WHERE uuid = ? AND type = 'BAN' AND active = 1 LIMIT 1")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long exp = rs.getLong("expires");
                        isBanned = (exp == 0L || exp > System.currentTimeMillis());
                    }
                }
            }

            // ── Staff issued count ────────────────────────────────────────────
            if (!staffName.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM punishments WHERE staff = ?")) {
                    ps.setString(1, staffName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) staffTotal = rs.getInt(1);
                    }
                }
            }
        }

        return new StatsSnapshot(
                System.currentTimeMillis(),
                isBanned,
                totalBans, totalMutes, totalKicks, totalWarns, totalJails, totalAll,
                staffTotal
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Jail placeholder handler (reads JailService in-memory map only)
    // ─────────────────────────────────────────────────────────────────────────

    private @Nullable String handleJailPlaceholder(UUID uuid, String key) {
        if (!plugin.getConfig().getBoolean("jail-system.enabled", false)) {
            return jailDefault(key);
        }

        boolean jailed = isJailedActive(uuid);

        if (key.equals("active")) {
            return getFormattedStatus("jail_active", jailed);
        }

        if (!jailed) return jailDefault(key);

        Jail jail = plugin.getJailService().getJail(uuid);
        if (jail == null) return jailDefault(key);

        return switch (key) {

            case "cell"   -> color(phConfig.getString("jail_defaults.cell_none",   "&7None").equals(jail.getCellName())
                    ? phConfig.getString("jail_defaults.cell_none",   "&7None")
                    : jail.getCellName());

            case "reason" -> color(jail.getReason().isEmpty()
                    ? phConfig.getString("jail_defaults.reason_none", "&7None")
                    : jail.getReason());

            case "staff"  -> color(jail.getStaff().isEmpty()
                    ? phConfig.getString("jail_defaults.staff_none",  "&7None")
                    : jail.getStaff());

            case "labor_done"      -> String.valueOf(jail.getLaborProgress());
            case "labor_required"  -> String.valueOf(jail.getLaborRequired());
            case "labor_remaining" -> String.valueOf(Math.max(0, jail.getLaborRequired() - jail.getLaborProgress()));
            case "labor_percent"   -> {
                int req = jail.getLaborRequired();
                yield req <= 0 ? "100"
                        : String.valueOf(Math.min(100, jail.getLaborProgress() * 100 / req));
            }
            case "time_remaining"  -> {
                long ms = Math.max(0L, jail.getExpires() - System.currentTimeMillis());
                yield formatTime((int) (ms / 1000L));
            }
            case "time_seconds"    -> {
                long s = Math.max(0L, (jail.getExpires() - System.currentTimeMillis()) / 1000L);
                yield String.valueOf(s);
            }
            default -> null;
        };
    }

    /**
     * Returns the configured fallback string for a jail field when the player
     * is not jailed or the field has no meaningful value.
     */
    private @Nullable String jailDefault(String key) {
        return switch (key) {
            case "cell"   -> color(phConfig.getString("jail_defaults.cell_none",   "&7None"));
            case "reason" -> color(phConfig.getString("jail_defaults.reason_none", "&7None"));
            case "staff"  -> color(phConfig.getString("jail_defaults.staff_none",  "&7None"));
            case "time_remaining" -> color(phConfig.getString("time_format.zero_display", "&a0s"));
            case "labor_done", "labor_required", "labor_remaining",
                 "labor_percent", "time_seconds" -> "0";
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reads the {@code active} or {@code inactive} string for {@code configPath}
     * from placeholders.yml and translates Minecraft color codes.
     *
     * <p>Example: {@code getFormattedStatus("is_banned", true)} reads
     * {@code placeholders.is_banned.active} and colorizes it.</p>
     *
     * @param configPath the node under {@code placeholders.*} (e.g. "is_banned")
     * @param state      {@code true}  → read the {@code active}   sub-key
     *                   {@code false} → read the {@code inactive} sub-key
     * @return colorized string, never null
     */
    private String getFormattedStatus(String configPath, boolean state) {
        String subKey = state ? "active" : "inactive";
        String raw    = phConfig.getString("placeholders." + configPath + "." + subKey,
                state ? "&cUnknown" : "&7Unknown");
        return color(raw);
    }

    /**
     * Formats a duration in seconds using the unit suffixes and separator
     * configured in {@code time_format.*} of placeholders.yml.
     *
     * <p>Units whose value is 0 are omitted. If the total is 0 or negative the
     * {@code time_format.zero_display} value is returned instead.</p>
     *
     * @param totalSeconds non-negative seconds to format
     * @return colorized, human-readable duration string
     */
    private String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) {
            return color(phConfig.getString("time_format.zero_display", "&a0s"));
        }

        String suffixD   = phConfig.getString("time_format.days",      "d");
        String suffixH   = phConfig.getString("time_format.hours",     "h");
        String suffixM   = phConfig.getString("time_format.minutes",   "m");
        String suffixS   = phConfig.getString("time_format.seconds",   "s");
        String separator = phConfig.getString("time_format.separator", " ");

        int days    = totalSeconds / 86400;
        int hours   = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if (days    > 0) appendUnit(sb, days,    suffixD, separator);
        if (hours   > 0) appendUnit(sb, hours,   suffixH, separator);
        if (minutes > 0) appendUnit(sb, minutes, suffixM, separator);
        if (seconds > 0) appendUnit(sb, seconds, suffixS, separator);

        // Trim the trailing separator that appendUnit always adds
        String result = sb.toString();
        if (result.endsWith(separator)) {
            result = result.substring(0, result.length() - separator.length());
        }

        return result.isEmpty()
                ? color(phConfig.getString("time_format.zero_display", "&a0s"))
                : result;
    }

    /** Appends {@code value + suffix + separator} to the builder. */
    private void appendUnit(StringBuilder sb, int value, String suffix, String separator) {
        sb.append(value).append(suffix).append(separator);
    }

    /** Translates {@code &} color codes to Minecraft formatting. */
    private String color(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private boolean isJailedActive(UUID uuid) {
        if (!plugin.getConfig().getBoolean("jail-system.enabled", false)) return false;
        if (plugin.getJailService() == null) return false;
        return plugin.getJailService().isJailed(uuid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner class: immutable SQL snapshot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable value object that holds all SQL-derived statistics for one
     * player at the moment the async fetch completed.
     *
     * <p>All fields are {@code final} primitives. Combined with
     * {@link ConcurrentHashMap}'s happens-before guarantee on put/get, reads
     * from the main thread are safe without additional locking.</p>
     */
    private static final class StatsSnapshot {

        final long    fetchedAt;
        final boolean isBanned;
        final int     totalBans;
        final int     totalMutes;
        final int     totalKicks;
        final int     totalWarns;
        final int     totalJails;
        final int     totalPunishments;
        final int     staffTotalIssued;

        StatsSnapshot(long fetchedAt, boolean isBanned,
                      int totalBans,  int totalMutes, int totalKicks,
                      int totalWarns, int totalJails, int totalPunishments,
                      int staffTotalIssued) {
            this.fetchedAt        = fetchedAt;
            this.isBanned         = isBanned;
            this.totalBans        = totalBans;
            this.totalMutes       = totalMutes;
            this.totalKicks       = totalKicks;
            this.totalWarns       = totalWarns;
            this.totalJails       = totalJails;
            this.totalPunishments = totalPunishments;
            this.staffTotalIssued = staffTotalIssued;
        }

        /**
         * Zero-value sentinel used while the first async fetch is in-flight.
         * {@code fetchedAt = 0} keeps {@link #isExpired} returning {@code true}
         * so this snapshot is never treated as a real, up-to-date entry.
         */
        static StatsSnapshot empty() {
            return new StatsSnapshot(0L, false, 0, 0, 0, 0, 0, 0, 0);
        }

        boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - fetchedAt) > ttlMs;
        }
    }
}