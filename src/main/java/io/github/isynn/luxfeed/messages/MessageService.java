package io.github.isynn.luxfeed.messages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads the active language file ({@code lang/en.yml} or {@code lang/fr.yml}, MiniMessage format).
 *
 * <p>A message missing from the server's file falls back to the copy bundled in the jar, then to
 * bundled English. An unknown language falls back to English.
 */
public final class MessageService {

    public static final List<String> LANGUAGES = List.of("en", "fr");
    private static final String DEFAULT_LANGUAGE = "en";

    private final YamlConfiguration messages;

    /**
     * @param languageFolder folder holding the editable language files
     * @param bundled        opens a resource of the plugin jar
     */
    public MessageService(String language, File languageFolder, Function<String, InputStream> bundled,
                          Logger logger) {
        String code = String.valueOf(language).trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(code)) {
            logger.warning("Unsupported language '" + language + "' in config.yml, using English.");
            code = DEFAULT_LANGUAGE;
        }

        YamlConfiguration fallback = readBundled(DEFAULT_LANGUAGE, bundled);
        if (!code.equals(DEFAULT_LANGUAGE)) {
            YamlConfiguration translation = readBundled(code, bundled);
            translation.setDefaults(fallback);
            fallback = translation;
        }
        // An invalid file is reported by Bukkit and loads as empty, so every message falls back.
        messages = YamlConfiguration.loadConfiguration(new File(languageFolder, code + ".yml"));
        messages.setDefaults(fallback);
    }

    public static String resourcePath(String language) {
        return "lang/" + language + ".yml";
    }

    public Component get(String key, TagResolver... placeholders) {
        String template = messages.getString(key);
        return MiniMessage.miniMessage().deserialize(template == null ? key : template, placeholders);
    }

    /** Formats a duration such as {@code 1h 5m 3s}, rounded up to the next second. */
    public Component duration(Duration duration) {
        long totalSeconds = Math.max(1, duration.plusNanos(999_999_999).toSeconds());
        List<Component> parts = new ArrayList<>();
        addPart(parts, "time.hours", totalSeconds / 3600);
        addPart(parts, "time.minutes", totalSeconds % 3600 / 60);
        addPart(parts, "time.seconds", totalSeconds % 60);
        return Component.join(JoinConfiguration.spaces(), parts);
    }

    private void addPart(List<Component> parts, String key, long amount) {
        if (amount > 0) {
            parts.add(get(key, Placeholder.unparsed("amount", Long.toString(amount))));
        }
    }

    private static YamlConfiguration readBundled(String language, Function<String, InputStream> bundled) {
        try (InputStream input = bundled.apply(resourcePath(language))) {
            return input == null
                    ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new YamlConfiguration();
        }
    }
}
