package me.bintanq.quantum;

import me.bintanq.quantum.commands.*;
import me.bintanq.quantum.database.DatabaseManager;
import me.bintanq.quantum.listeners.ChatFilterListener;
import me.bintanq.quantum.listeners.GuiListener;
import me.bintanq.quantum.listeners.PlayerConnectionListener;
import me.bintanq.quantum.managers.*;
import me.bintanq.quantum.services.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class QuantumPunish extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PunishmentService punishmentService;
    private PlayerDataService playerDataService;
    private WarningService warningService;
    private ChatFilterService chatFilterService;
    private WebhookService webhookService;
    private MessageManager messageManager;
    private ConfigManager configManager;

    private FileConfiguration messagesConfig;
    private FileConfiguration historyMenuConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDefaultFiles();
        loadConfigurations();

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this, messagesConfig);

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerDataService = new PlayerDataService(databaseManager);
        warningService = new WarningService(databaseManager);
        webhookService = new WebhookService(this);
        chatFilterService = new ChatFilterService(this);
        punishmentService = new PunishmentService(this, databaseManager, webhookService, warningService);

        registerCommands();
        registerListeners();

        getLogger().info("QuantumPunish has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("QuantumPunish has been disabled!");
    }

    private void createDefaultFiles() {
        createDirectoryIfNotExists("menus");
        createDirectoryIfNotExists("filter");
        createDirectoryIfNotExists("webhook");

        saveResourceIfNotExists("messages.yml");
        saveResourceIfNotExists("menus/history.yml");
        saveResourceIfNotExists("filter/filter.txt");
        saveResourceIfNotExists("webhook/warn.json");
        saveResourceIfNotExists("webhook/ban.json");
        saveResourceIfNotExists("webhook/tempban.json");
        saveResourceIfNotExists("webhook/kick.json");
        saveResourceIfNotExists("webhook/mute.json");
        saveResourceIfNotExists("webhook/tempmute.json");
        saveResourceIfNotExists("webhook/unban.json");
        saveResourceIfNotExists("webhook/unmute.json");
        saveResourceIfNotExists("webhook/banip.json");
        saveResourceIfNotExists("webhook/auto-punish.json");
    }

    private void createDirectoryIfNotExists(String path) {
        File dir = new File(getDataFolder(), path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void loadConfigurations() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        File historyFile = new File(getDataFolder(), "menus/history.yml");
        historyMenuConfig = YamlConfiguration.loadConfiguration(historyFile);
    }

    private void registerCommands() {
        new BanCommand(this).register();
        new TempBanCommand(this).register();
        new UnbanCommand(this).register();
        new BanIPCommand(this).register();
        new UnbanIPCommand(this).register();
        new MuteCommand(this).register();
        new TempMuteCommand(this).register();
        new UnmuteCommand(this).register();
        new KickCommand(this).register();
        new WarnCommand(this).register();
        new HistoryCommand(this).register();
        new QInfoCommand(this).register();
        new QuantumPunishCommand(this).register();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatFilterListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
    }

    public void reloadConfigurations() {
        reloadConfig();
        loadConfigurations();
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this, messagesConfig);
        chatFilterService.reload();
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PunishmentService getPunishmentService() { return punishmentService; }
    public PlayerDataService getPlayerDataService() { return playerDataService; }
    public WarningService getWarningService() { return warningService; }
    public ChatFilterService getChatFilterService() { return chatFilterService; }
    public WebhookService getWebhookService() { return webhookService; }
    public MessageManager getMessageManager() { return messageManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }
    public FileConfiguration getHistoryMenuConfig() { return historyMenuConfig; }
}