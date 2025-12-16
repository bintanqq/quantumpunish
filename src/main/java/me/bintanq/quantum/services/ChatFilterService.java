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
    private boolean enabled;
    private boolean detectEvasion;
    private int maxRepeatedLetters;
    private String action;
    private static final String ZERO_WIDTH_EVASION_CHARS = "[,\\.\\-/\\\\_\\s`~'\"]";

    private Map<Character, List<String>> getReverseEvasionMap() {
        Map<Character, List<String>> reverseMap = new HashMap<>();

        evasionMap.forEach((evasionChar, cleanChar) -> {

            if (cleanChar.isEmpty()) {
                return;
            }
            char keyChar = cleanChar.charAt(0);

            reverseMap.computeIfAbsent(keyChar, k -> new ArrayList<>())
                    .add(Pattern.quote(String.valueOf(evasionChar)));
        });
        return reverseMap;
    }

    public ChatFilterService(QuantumPunish plugin) {
        this.plugin = plugin;
        loadFilter();
        setupEvasionMap();
    }

    public void reload() {
        loadFilter();
    }

    private void loadFilter() {
        blockedWords = new HashSet<>();
        enabled = plugin.getConfig().getBoolean("chat-filter.enabled", true);
        detectEvasion = plugin.getConfig().getBoolean("chat-filter.detect-evasion", true);
        maxRepeatedLetters = plugin.getConfig().getInt("chat-filter.max-repeated-letters", 3);
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

        // --- Evasion Vokal & Angka/Simbol (Sudah Ada, Ditambah Sedikit) ---
        evasionMap.put('4', "a");
        evasionMap.put('@', "a");
        evasionMap.put('3', "e");
        evasionMap.put('1', "i");
        evasionMap.put('!', "i");
        evasionMap.put('0', "o");
        evasionMap.put('5', "s");
        evasionMap.put('$', "s");
        evasionMap.put('7', "t");
        evasionMap.put('+', "t");
        evasionMap.put('*', "i");
        evasionMap.put('9', "g");
        evasionMap.put('k', "g");
        evasionMap.put('q', "g");
        evasionMap.put('v', "b");
        evasionMap.put('w', "u");
        evasionMap.put('c', "s");
        evasionMap.put('x', "s");
        evasionMap.put('.', "");
        evasionMap.put(',', "");
        evasionMap.put('-', "");
        evasionMap.put('_', "");
        evasionMap.put(' ', "");
        evasionMap.put('/', "");
        evasionMap.put('\\', "");
        evasionMap.put('`', "");
        evasionMap.put('~', "");
        evasionMap.put('\'', "");
        evasionMap.put('"', "");
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

        // Pattern.CASE_INSENSITIVE
        return Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    public String filterMessage(String message, Player player) {
        if (!enabled) return message;

        String originalMessage = message;

        for (String word : blockedWords) {
            Pattern evasionRegex = createEvasionPattern(word);
            if (evasionRegex.matcher(originalMessage).find()) {
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
                                "Using inappropriate language"
                        );
                        notifyStaff(player, originalMessage, word);
                        return message;
                    case "REPLACE":
                    default:
                        String replacement = "*".repeat(word.length());
                        message = evasionRegex.matcher(message).replaceAll(replacement);
                        notifyStaff(player, originalMessage, word);
                }
            }
        }

        return message;
    }

    private String normalizeMessage(String message) {
        if (!detectEvasion) return message.toLowerCase();

        StringBuilder normalized = new StringBuilder();
        for (char c : message.toLowerCase().toCharArray()) {
            normalized.append(evasionMap.getOrDefault(c, String.valueOf(c)));
        }
        return normalized.toString();
    }

    private String removeRepeatedLetters(String message) {
        if (maxRepeatedLetters <= 0) return message;

        StringBuilder result = new StringBuilder();
        char lastChar = '\0';
        int count = 0;

        for (char c : message.toCharArray()) {
            if (c == lastChar) {
                count++;
                if (count <= maxRepeatedLetters) {
                    result.append(c);
                }
            } else {
                result.append(c);
                lastChar = c;
                count = 1;
            }
        }

        return result.toString();
    }

    private boolean containsWord(String message, String word) {
        return message.contains(word) ||
                message.matches(".*\\b" + Pattern.quote(word) + "\\b.*");
    }

    private void notifyStaff(Player player, String message, String filtered) {
        String notification = plugin.getMessageManager().getMessage("filter-notify")
                .replace("%player%", player.getName())
                .replace("%message%", message)
                .replace("%word%", filtered);

        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("quantumpunish.notify"))
                .forEach(p -> p.sendMessage(notification));
    }
}