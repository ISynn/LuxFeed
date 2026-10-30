package io.github.isynn.luxfeed;

import io.github.isynn.luxfeed.combat.CombatCheck;
import io.github.isynn.luxfeed.combat.StubCombatCheck;
import io.github.isynn.luxfeed.command.FeedCommand;
import io.github.isynn.luxfeed.cooldown.CooldownService;
import io.github.isynn.luxfeed.cooldown.CooldownSettings;
import io.github.isynn.luxfeed.cooldown.CooldownTier;
import io.github.isynn.luxfeed.database.SQLiteDatabase;
import io.github.isynn.luxfeed.messages.Message;
import io.github.isynn.luxfeed.messages.MessageService;
import java.io.File;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuxFeedPlugin extends JavaPlugin {

    private static final String DATABASE_FILE = "luxfeed.db";
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private ExecutorService databaseExecutor;
    private SQLiteDatabase database;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultLanguageFiles();
        MessageService messages = MessageService.load(getConfig().getString("language"),
                getDataFolder().toPath().resolve("lang"), this::getResource, getLogger());

        CooldownSettings cooldownSettings =
                CooldownSettings.fromConfig(getConfig().getConfigurationSection("cooldowns"), getLogger());
        registerCooldownPermissions(cooldownSettings);

        // A single thread runs every database task, one after another (see CooldownService).
        databaseExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("LuxFeed-Database").daemon().factory());
        database = new SQLiteDatabase(getDataFolder().toPath().resolve(DATABASE_FILE));
        databaseExecutor.execute(this::connectDatabase);
        CooldownService cooldowns =
                new CooldownService(cooldownSettings, database, databaseExecutor, Clock.systemUTC());

        // No combat system is connected yet: swap this for an adapter to the network's combat plugin.
        CombatCheck combatCheck = new StubCombatCheck();

        FeedCommand feedCommand = new FeedCommand(messages, combatCheck, cooldowns,
                getServer().getScheduler().getMainThreadExecutor(this), getLogger());
        registerCommand("feed", messages.plain(Message.COMMAND_DESCRIPTION), feedCommand);
    }

    @Override
    public void onDisable() {
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    getLogger().warning("Database tasks did not finish in time and were abandoned.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (database != null) {
            try {
                database.close();
            } catch (SQLException exception) {
                getLogger().log(Level.WARNING, "Could not close the database cleanly.", exception);
            }
        }
    }

    private void connectDatabase() {
        try {
            database.connect();
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "Could not open " + DATABASE_FILE
                    + "; /feed will report an error until the database becomes available.", exception);
        }
    }

    /**
     * Registers the permissions of tiers added in config.yml that plugin.yml does not declare, so
     * that operators do not receive them implicitly.
     */
    private void registerCooldownPermissions(CooldownSettings settings) {
        PluginManager pluginManager = getServer().getPluginManager();
        for (CooldownTier tier : settings.tiers()) {
            if (pluginManager.getPermission(tier.permission()) == null) {
                pluginManager.addPermission(new Permission(tier.permission(),
                        "Applies the '" + tier.name() + "' /feed cooldown.", PermissionDefault.FALSE));
            }
        }
    }

    /** Copies the bundled language files once so server owners can edit them; existing files are kept. */
    private void saveDefaultLanguageFiles() {
        for (String language : MessageService.SUPPORTED_LANGUAGES) {
            String path = MessageService.resourcePath(language);
            if (!new File(getDataFolder(), path).exists()) {
                saveResource(path, false);
            }
        }
    }
}
