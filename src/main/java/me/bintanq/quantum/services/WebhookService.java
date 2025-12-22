package me.bintanq.quantum.services;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Punishment;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class WebhookService {
    private final QuantumPunish plugin;
    private String webhookUrl;
    private boolean enabled;

    public WebhookService(QuantumPunish plugin) {
        this.plugin = plugin;
        this.webhookUrl = plugin.getConfig().getString("webhook.url", "");
        this.enabled = plugin.getConfig().getBoolean("webhook.enabled", false);
    }

    public void sendPunishment(Punishment punishment) {
        if (!enabled || webhookUrl.isEmpty()) return;

        String templateName = punishment.getType().name().toLowerCase();
        if (punishment.getExpires() != null && !punishment.getType().name().equals("AUTO")) {
            templateName = "temp" + templateName;
        }

        File templateFile = new File(plugin.getDataFolder(), "webhook/" + templateName + ".json");
        if (!templateFile.exists()) {
            plugin.getLogger().warning("Webhook template not found: " + templateName + ".json");
            return;
        }

        try {
            String template = Files.readString(templateFile.toPath());
            String json = replacePlaceholders(template, punishment);
            sendWebhook(json);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to send webhook: " + e.getMessage());
        }
    }

    public void sendUnpunishment(String type, String target, String staff) {
        if (!enabled || webhookUrl.isEmpty()) return;

        File templateFile = new File(plugin.getDataFolder(), "webhook/" + type + ".json");
        if (!templateFile.exists()) return;

        try {
            String template = Files.readString(templateFile.toPath());
            template = template.replace("%player%", target);
            template = template.replace("%staff%", staff);
            template = template.replace("%timestamp%", formatTimestamp(System.currentTimeMillis()));
            sendWebhook(template);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to send webhook: " + e.getMessage());
        }
    }

    public void sendCustom(String type, Map<String, String> placeholders) {
        if (!enabled || webhookUrl.isEmpty()) return;

        File templateFile = new File(plugin.getDataFolder(), "webhook/" + type + ".json");
        if (!templateFile.exists()) return;

        try {
            String template = Files.readString(templateFile.toPath());
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                template = template.replace(entry.getKey(), entry.getValue());
            }
            sendWebhook(template);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to send webhook: " + e.getMessage());
        }
    }

    private String replacePlaceholders(String template, Punishment punishment) {
        int totalPoints = plugin.getWarningService().getWarningPoints(punishment.getUuid());
        return template
                .replace("%player%", punishment.getPlayerName())
                .replace("%uuid%", punishment.getUuid().toString())
                .replace("%staff%", punishment.getStaff())
                .replace("%reason%", punishment.getReason())
                .replace("%type%", punishment.getType().name())
                .replace("%total_points%", String.valueOf(totalPoints))
                .replace("%duration%", punishment.getExpires() == null ? "Permanent" :
                        plugin.getPunishmentService().formatDuration(punishment.getExpires() - punishment.getTimestamp()))
                .replace("%timestamp%", formatTimestamp(punishment.getTimestamp()))
                .replace("%expires%", punishment.getExpires() == null ? "Never" : formatTimestamp(punishment.getExpires()));
    }

    private String formatTimestamp(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(millis));
    }

    private void sendWebhook(String jsonContent) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonContent.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 204 && responseCode != 200) {
                    plugin.getLogger().warning("Webhook returned code: " + responseCode);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    public void reload() {
        this.webhookUrl = plugin.getConfig().getString("webhook.url", "");
        this.enabled = plugin.getConfig().getBoolean("webhook.enabled", false);
        plugin.getLogger().info("WebhookService has been reloaded!");
    }
}