package me.bintanq.quantum.services;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class ChatFilterService {
    private final QuantumPunish plugin;
    private Set<String> blockedWords;
    private Map<Character, String> evasionMap;

    // Config variables
    private boolean enabled;
    private boolean detectEvasion;
    private boolean notifyStaffEnabled;
    private int maxRepeatedLetters;
    private int filterWarningPoints;
    private String action;

    private static final String ZERO_WIDTH_EVASION_CHARS = "[,\\.\\-/\\\\_\\s`~'\"]";

    public ChatFilterService(QuantumPunish plugin) {
        this.plugin = plugin;
        setupEvasionMap();
        loadFilter();
    }

    public void reload() {
        loadFilter();
    }

    private void loadFilter() {
        blockedWords = new HashSet<>();
        enabled = plugin.getConfig().getBoolean("chat-filter.enabled", true);
        detectEvasion = plugin.getConfig().getBoolean("chat-filter.detect-evasion", true);
        notifyStaffEnabled = plugin.getConfig().getBoolean("chat-filter.notify-staff", true); // <-- LOAD DARI CONFIG
        maxRepeatedLetters = plugin.getConfig().getInt("chat-filter.max-repeated-letters", 3);
        filterWarningPoints = plugin.getConfig().getInt("chat-filter.warning-points", 1);
        action = plugin.getConfig().getString("chat-filter.action", "REPLACE");

        File filterFile = new File(plugin.getDataFolder(), "filter/filter.txt");
        if (filterFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(filterFile.toPath());
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        blockedWords.add(line.toLowerCase());
                    }
                }
                plugin.getLogger().info("Loaded " + blockedWords.size() + " filtered words");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load filter.txt: " + e.getMessage());
            }
        }
    }

    private void setupEvasionMap() {
        evasionMap = new HashMap<>();
        evasionMap.put('4', "a"); evasionMap.put('@', "a"); evasionMap.put('3', "e");
        evasionMap.put('1', "i"); evasionMap.put('!', "i"); evasionMap.put('*', "i");
        evasionMap.put('0', "o"); evasionMap.put('5', "s"); evasionMap.put('$', "s");
        evasionMap.put('7', "t"); evasionMap.put('+', "t"); evasionMap.put('9', "g");
        evasionMap.put('k', "g"); evasionMap.put('q', "g"); evasionMap.put('v', "b");
        evasionMap.put('w', "u"); evasionMap.put('c', "s"); evasionMap.put('x', "s");
    }

    private Map<Character, List<String>> getReverseEvasionMap() {
        Map<Character, List<String>> reverseMap = new HashMap<>();
        evasionMap.forEach((evasionChar, cleanChar) -> {
            if (cleanChar.isEmpty()) return;
            char keyChar = cleanChar.charAt(0);
            reverseMap.computeIfAbsent(keyChar, k -> new ArrayList<>())
                    .add(Pattern.quote(String.valueOf(evasionChar)));
        });
        return reverseMap;
    }

    private Pattern createEvasionPattern(String word) {
        Map<Character, List<String>> reverseMap = getReverseEvasionMap();
        StringBuilder patternBuilder = new StringBuilder();

        for (char cleanChar : word.toCharArray()) {
            patternBuilder.append("[");
            patternBuilder.append(Pattern.quote(String.valueOf(cleanChar)));
            if (reverseMap.containsKey(cleanChar)) {
                reverseMap.get(cleanChar).forEach(patternBuilder::append);
            }
            patternBuilder.append("]");

            if (maxRepeatedLetters > 0) {
                patternBuilder.append("{1,").append(maxRepeatedLetters).append("}");
            }
            patternBuilder.append("[").append(ZERO_WIDTH_EVASION_CHARS).append("]*");
        }
        return Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    public String filterMessage(String message, Player player) {
        if (!enabled) return message;
        if (player.hasPermission("quantumpunish.bypass.filter")) return message; // Cek permission bypass

        String originalMessage = message;

        for (String word : blockedWords) {
            boolean detected = false;
            Pattern pattern = null;

            if (detectEvasion) {
                pattern = createEvasionPattern(word);
                if (pattern.matcher(originalMessage).find()) {
                    detected = true;
                }
            } else {
                pattern = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
                if (pattern.matcher(originalMessage).find() || originalMessage.toLowerCase().contains(word)) {
                    detected = true;
                }
            }

            if (detected) {
                switch (action.toUpperCase()) {
                    case "BLOCK":
                        player.sendMessage(plugin.getMessageManager().getMessage("filter-blocked"));
                        notifyStaff(player, originalMessage, word);
                        return null;

                    case "WARN":
                        plugin.getPunishmentService().warnPlayer(
                                player.getName(),
                                player.getUniqueId(),
                                "FILTER",
                                "Inappropriate language",
                                filterWarningPoints
                        );
                        notifyStaff(player, originalMessage, word);
                        return null;

                    case "REPLACE":
                    default:
                        String replacement = "*".repeat(word.length());
                        if (pattern != null) {
                            message = pattern.matcher(message).replaceAll(replacement);
                        } else {
                            message = message.replace(word, replacement);
                        }
                        notifyStaff(player, originalMessage, word);
                }
            }
        }

        return message;
    }

    private void notifyStaff(Player player, String message, String filtered) {
        if (!notifyStaffEnabled) return;

        String notification = plugin.getMessageManager().getMessage("filter-notify")
                .replace("%player%", player.getName())
                .replace("%message%", message)
                .replace("%word%", filtered);

        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("quantumpunish.notify"))
                .forEach(p -> p.sendMessage(notification));
    }
}