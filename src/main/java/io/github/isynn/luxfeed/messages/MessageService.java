package io.github.isynn.luxfeed.messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads the active language file and turns {@link Message} entries into components.
 *
 * <p>Messages are written in the MiniMessage format. A message missing from the server's
 * language file falls back to the copy bundled in the plugin jar, then to bundled English.
 */
public final class MessageService {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "fr");

    private final YamlConfiguration messages;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private MessageService(YamlConfiguration messages, Logger logger) {
        this.messages = messages;
        this.logger = logger;
    }

    /**
     * Creates the service for the requested language.
     *
     * @param requestedLanguage language code from the configuration, may be {@code null}
     * @param languageFolder folder holding the editable {@code <code>.yml} files
     * @param bundledResources opens a resource of the plugin jar, returns {@code null} if absent
     */
    public static MessageService load(String requestedLanguage, Path languageFolder,
                                      Function<String, InputStream> bundledResources, Logger logger) {
        String language = resolveLanguage(requestedLanguage, logger);

        YamlConfiguration fallback = loadBundled(DEFAULT_LANGUAGE, bundledResources, logger);
        if (!language.equals(DEFAULT_LANGUAGE)) {
            YamlConfiguration bundled = loadBundled(language, bundledResources, logger);
            bundled.setDefaults(fallback);
            fallback = bundled;
        }

        YamlConfiguration messages = loadEditable(languageFolder.resolve(fileName(language)), logger);
        messages.setDefaults(fallback);
        return new MessageService(messages, logger);
    }

    /** Path of a language file inside the plugin jar. */
    public static String resourcePath(String language) {
        return "lang/" + fileName(language);
    }

    public Component get(Message message, TagResolver... placeholders) {
        return miniMessage.deserialize(template(message), placeholders);
    }

    /** Message without formatting, for places that only accept plain strings. */
    public String plain(Message message) {
        return PlainTextComponentSerializer.plainText().serialize(get(message));
    }

    /** Formats a duration such as {@code 1h 5m 3s}, rounding up to the next second. */
    public Component duration(Duration duration) {
        long totalSeconds = Math.max(0, duration.plusNanos(999_999_999).toSeconds());
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;

        List<Component> parts = new ArrayList<>(3);
        if (hours > 0) {
            parts.add(amount(Message.TIME_HOURS, hours));
        }
        if (minutes > 0) {
            parts.add(amount(Message.TIME_MINUTES, minutes));
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(amount(Message.TIME_SECONDS, seconds));
        }
        return Component.join(JoinConfiguration.spaces(), parts);
    }

    private Component amount(Message unit, long value) {
        return get(unit, Placeholder.unparsed("amount", Long.toString(value)));
    }

    private String template(Message message) {
        String template = messages.getString(message.path());
        if (template == null) {
            logger.warning("Missing message '" + message.path() + "' in every language file.");
            return message.path();
        }
        return template;
    }

    private static String resolveLanguage(String requestedLanguage, Logger logger) {
        if (requestedLanguage == null) {
            return DEFAULT_LANGUAGE;
        }
        String language = requestedLanguage.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            logger.warning("Unsupported language '" + requestedLanguage + "' in config.yml, using '"
                    + DEFAULT_LANGUAGE + "'. Supported languages: " + SUPPORTED_LANGUAGES + ".");
            return DEFAULT_LANGUAGE;
        }
        return language;
    }

    private static YamlConfiguration loadBundled(String language, Function<String, InputStream> resources,
                                                 Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        try (InputStream input = resources.apply(resourcePath(language))) {
            if (input == null) {
                logger.warning("Bundled language file " + resourcePath(language) + " is missing from the jar.");
                return configuration;
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                configuration.load(reader);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            logger.log(Level.WARNING, "Could not read bundled language file " + resourcePath(language) + ".",
                    exception);
        }
        return configuration;
    }

    private static YamlConfiguration loadEditable(Path file, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (!Files.isRegularFile(file)) {
            logger.warning("Language file " + file + " not found, using the bundled messages.");
            return configuration;
        }
        try {
            configuration.load(file.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warning("Language file " + file + " is invalid, using the bundled messages: "
                    + exception.getMessage());
            return new YamlConfiguration();
        }
        return configuration;
    }

    private static String fileName(String language) {
        return language + ".yml";
    }
}
