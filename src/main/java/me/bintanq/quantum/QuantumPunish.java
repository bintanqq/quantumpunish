package me.bintanq.quantum;

import me.bintanq.quantum.commands.*;
import me.bintanq.quantum.database.DatabaseManager;
import me.bintanq.quantum.listeners.ChatFilterListener;
import me.bintanq.quantum.listeners.GuiListener;
import me.bintanq.quantum.listeners.JailListener;
import me.bintanq.quantum.listeners.PlayerConnectionListener;
import me.bintanq.quantum.managers.*;
import me.bintanq.quantum.services.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

public class QuantumPunish extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PunishmentService punishmentService;
    private PlayerDataService playerDataService;
    private WarningService warningService;
    private ChatFilterService chatFilterService;
    private WebhookService webhookService;
    private AppealService appealService;
    private MessageManager messageManager;
    private ConfigManager configManager;

    private FileConfiguration appealsMenuConfig;
    private FileConfiguration activeMenuConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration historyMenuConfig;
    private JailService jailService;
    private LaborManager laborManager;

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

        if (getConfig().getBoolean("jail-system.enabled", false)) {
            jailService = new JailService(this, databaseManager);

            if (getConfig().getBoolean("jail-system.labor.enabled", false)) {
                laborManager = new LaborManager(this);
            }

            getLogger().info("Jail System has been enabled!");
        }


        playerDataService = new PlayerDataService(databaseManager);
        warningService = new WarningService(databaseManager);
        webhookService = new WebhookService(this);
        chatFilterService = new ChatFilterService(this);
        punishmentService = new PunishmentService(this, databaseManager, webhookService, warningService);
        appealService = new AppealService(this, databaseManager);

        databaseManager.cleanupOldData(getConfig().getInt("database.cleanup-days", 90));

        registerCommands();
        registerListeners();

        getLogger().info("QuantumPunish has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (laborManager != null) {
            laborManager.shutdown();
        }
        getLogger().info("QuantumPunish has been disabled!");
    }

    private void createDefaultFiles() {
        createDirectoryIfNotExists("menus");
        createDirectoryIfNotExists("filter");
        createDirectoryIfNotExists("webhook");

        saveResource("webhook/warn.json", true);
        saveResource("webhook/ban.json", true);
        saveResource("webhook/tempban.json", true);
        saveResource("webhook/kick.json", true);
        saveResource("webhook/mute.json", true);
        saveResource("webhook/tempmute.json", true);
        saveResource("webhook/unban.json", true);
        saveResource("webhook/unmute.json", true);
        saveResource("webhook/banip.json", true);
        saveResource("webhook/auto.json", true);
        saveResource("webhook/alt-alert.json", true);
        saveResource("webhook/appeal.json", true);
        saveResource("webhook/cleanup.json", true);
        saveResource("webhook/jail.json", true);
        saveResource("webhook/unjail.json", true);

        saveResourceIfNotExists("filter/filter.txt");
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
        updateYamlConfig("config.yml");
        updateYamlConfig("messages.yml");
        updateYamlConfig("menus/history.yml");
        updateYamlConfig("menus/active.yml");
        updateYamlConfig("menus/appeals.yml");

        File messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        File historyFile = new File(getDataFolder(), "menus/history.yml");
        historyMenuConfig = YamlConfiguration.loadConfiguration(historyFile);

        File activeFile = new File(getDataFolder(), "menus/active.yml"); // Tambah ini
        activeMenuConfig = YamlConfiguration.loadConfiguration(activeFile);

        File appealsFile = new File(getDataFolder(), "menus/appeals.yml");
        appealsMenuConfig = YamlConfiguration.loadConfiguration(appealsFile);
    }

    private void updateYamlConfig(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
            return;
        }

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(file);
        InputStream defaultStream = getResource(fileName);

        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            boolean changed = false;

            for (String key : defaultConfig.getKeys(true)) {
                if (!currentConfig.contains(key)) {
                    currentConfig.set(key, defaultConfig.get(key));
                    changed = true;
                }
            }

            if (changed) {
                try {
                    currentConfig.save(file);
                    getLogger().info("Updated " + fileName + " with new configuration nodes.");
                } catch (IOException e) {
                    getLogger().severe("Could not update " + fileName + "!");
                }
            }
        }
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
        new AppealCommand(this).register();
        if (getConfig().getBoolean("jail-system.enabled", false)) {
            new JailCommand(this).register();
            new UnjailCommand(this).register();
            new JailStatusCommand(this).register();
            new JailAdminCommand(this).register();
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatFilterListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        if (getConfig().getBoolean("jail-system.enabled", false)) {
            getServer().getPluginManager().registerEvents(new JailListener(this), this);
        }
    }

    public void reloadConfigurations() {
        reloadConfig();
        loadConfigurations();
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this, messagesConfig);
        chatFilterService.reload();
        webhookService.reload();
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PunishmentService getPunishmentService() { return punishmentService; }
    public PlayerDataService getPlayerDataService() { return playerDataService; }
    public WarningService getWarningService() { return warningService; }
    public ChatFilterService getChatFilterService() { return chatFilterService; }
    public WebhookService getWebhookService() { return webhookService; }
    public AppealService getAppealService() { return appealService; }
    public MessageManager getMessageManager() { return messageManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }
    public FileConfiguration getHistoryMenuConfig() { return historyMenuConfig; }
    public FileConfiguration getActiveMenuConfig() { return activeMenuConfig; }
    public FileConfiguration getAppealsMenuConfig() { return appealsMenuConfig; }
    public JailService getJailService() { return jailService; }
    public LaborManager getLaborManager() { return laborManager; }
}