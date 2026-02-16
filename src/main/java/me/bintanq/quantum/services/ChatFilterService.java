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
    private Set<String> whitelist;
    private Map<String, Pattern> compiledPatterns;

    // Config variables
    private boolean enabled;
    private boolean detectEvasion;
    private boolean notifyStaffEnabled;
    private int maxRepeatedLetters;
    private int filterWarningPoints;
    private String action;

    private static final String ZERO_WIDTH_EVASION_CHARS = "[,\\.\\-/\\\\_\\s`~'\" ]";

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
        whitelist = new HashSet<>();
        compiledPatterns = new HashMap<>();
        enabled = plugin.getConfig().getBoolean("chat-filter.enabled", true);
        detectEvasion = plugin.getConfig().getBoolean("chat-filter.detect-evasion", true);
        notifyStaffEnabled = plugin.getConfig().getBoolean("chat-filter.notify-staff", true); // <-- LOAD DARI CONFIG
        maxRepeatedLetters = plugin.getConfig().getInt("chat-filter.max-repeated-letters", 3);
        filterWarningPoints = plugin.getConfig().getInt("chat-filter.warning-points", 1);
        action = plugin.getConfig().getString("chat-filter.action", "REPLACE");

        File whitelistFile = new File(plugin.getDataFolder(), "filter/whitelist.txt");
        if (whitelistFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(whitelistFile.toPath());
                for (String line : lines) {
                    if (!line.trim().isEmpty()) whitelist.add(line.trim().toLowerCase());
                }
            } catch (IOException e) { /* log error */ }
        }

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

        if (detectEvasion) {
            for (String word : blockedWords) {
                compiledPatterns.put(word, createEvasionPattern(word));
            }
        }

    }

    private void setupEvasionMap() {
        evasionMap = new HashMap<>();
        evasionMap.put('4', "a"); evasionMap.put('@', "a"); evasionMap.put('3', "e"); evasionMap.put('µ', "u"); evasionMap.put('^', "a");
        evasionMap.put('1', "i"); evasionMap.put('!', "i"); evasionMap.put('*', "i"); evasionMap.put('y', "u"); evasionMap.put('£', "e");
        evasionMap.put('0', "o"); evasionMap.put('5', "s"); evasionMap.put('$', "s"); evasionMap.put('8', "b"); evasionMap.put('€', "e");
        evasionMap.put('7', "t"); evasionMap.put('+', "t"); evasionMap.put('9', "g"); evasionMap.put('6', "b"); evasionMap.put('|', "i");
        evasionMap.put('k', "g"); evasionMap.put('q', "g"); evasionMap.put('v', "b"); evasionMap.put('l', "i"); evasionMap.put('j', "i");
        evasionMap.put('w', "u"); evasionMap.put('c', "s"); evasionMap.put('x', "s"); evasionMap.put('z', "s"); evasionMap.put('r', "t");
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
        if (!enabled || player.hasPermission("quantumpunish.bypass.filter")) return message;

        String originalMessage = message;
        String cleanedMessage = originalMessage.toLowerCase();
        String[] wordsInChat = originalMessage.toLowerCase().split("\\s+");

        for (String chatWord : wordsInChat) {
            if (whitelist.contains(chatWord)) return message;
        }

        boolean detected = false;
        String triggeredWord = "";
        Pattern foundPattern = null;

        for (String badWord : blockedWords) {
            Pattern p = compiledPatterns.get(badWord);

            if (p != null && p.matcher(originalMessage).find()) {
                detected = true;
                triggeredWord = badWord;
                foundPattern = p;
                break;
            }

            for (String chatWord : wordsInChat) {
                if (chatWord.length() >= 3 && getSimilarity(chatWord, badWord) > 0.8) {
                    detected = true;
                    triggeredWord = badWord;
                    break;
                }
            }
            if (detected) break;
        }

        if (detected) {
            switch (action.toUpperCase()) {
                case "BLOCK":
                    player.sendMessage(plugin.getMessageManager().getMessage("filter-blocked"));
                    notifyStaff(player, originalMessage, triggeredWord);
                    return null;

                case "WARN":
                    plugin.getPunishmentService().warnPlayer(
                            player.getName(),
                            player.getUniqueId(),
                            "FILTER",
                            "Inappropriate language",
                            filterWarningPoints
                    );
                    notifyStaff(player, originalMessage, triggeredWord);
                    return null;

                case "REPLACE":
                default:
                    String replacement = "*".repeat(triggeredWord.length());
                    if (foundPattern != null) {
                        message = foundPattern.matcher(message).replaceAll(replacement);
                    } else {
                        message = message.replaceAll("(?i)" + Pattern.quote(triggeredWord), replacement);
                    }
                    notifyStaff(player, originalMessage, triggeredWord);
            }
        }

        return message;
    }

    private double getSimilarity(String s1, String s2) {
        int longLen = Math.max(s1.length(), s2.length());
        if (longLen == 0) return 1.0;
        return (longLen - computeEditDistance(s1, s2)) / (double) longLen;
    }

    private int computeEditDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    private void notifyStaff(Player player, String message, String filtered) {
        if (!notifyStaffEnabled) return;

        String notification = plugin.getMessageManager().getMessage("filter-notify")
                .replace("%player%", player.getName())
                .replace("%message%", message)
                .replace("%word%", filtered);

        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("quantumpunish.notify"))
                .forEach(staff -> {
                    staff.sendMessage(notification);
                    staff.playSound(staff.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                });
    }
}