package io.github.isynn.luxfeed.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageServiceTest {

    private static final Function<String, InputStream> JAR_RESOURCES =
            MessageServiceTest.class.getClassLoader()::getResourceAsStream;

    @TempDir
    Path languageFolder;

    private final List<String> warnings = new ArrayList<>();
    private Logger logger;

    @BeforeEach
    void setUpLogger() {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    void resolvesEnglishMessages() {
        MessageService messages = load("en");

        assertEquals("Only players can use this command.", messages.plain(Message.PLAYERS_ONLY));
        assertEquals("Your hunger and saturation have been fully restored.", messages.plain(Message.FEED_SUCCESS));
    }

    @Test
    void resolvesFrenchMessages() {
        MessageService messages = load("fr");

        assertEquals("Seuls les joueurs peuvent utiliser cette commande.", messages.plain(Message.PLAYERS_ONLY));
        assertEquals("Votre faim et votre saturation ont été entièrement restaurées.",
                messages.plain(Message.FEED_SUCCESS));
    }

    @Test
    void languageCodeIsTrimmedAndCaseInsensitive() {
        assertEquals(load("fr").plain(Message.FEED_IN_COMBAT), load(" FR ").plain(Message.FEED_IN_COMBAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "english", ""})
    void unsupportedLanguageFallsBackToEnglishWithWarning(String language) {
        MessageService messages = load(language);

        assertEquals(load("en").plain(Message.NO_PERMISSION), messages.plain(Message.NO_PERMISSION));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("Unsupported language")));
    }

    @Test
    void missingLanguageSettingUsesEnglish() {
        assertEquals(load("en").plain(Message.FEED_ERROR), load(null).plain(Message.FEED_ERROR));
    }

    @Test
    void missingBundledTranslationFallsBackToEnglish() {
        Function<String, InputStream> englishOnly =
                path -> path.equals("lang/fr.yml") ? null : JAR_RESOURCES.apply(path);

        MessageService messages = MessageService.load("fr", languageFolder, englishOnly, logger);

        assertEquals("Only players can use this command.", messages.plain(Message.PLAYERS_ONLY));
    }

    @Test
    void editedLanguageFileOverridesBundledMessagesAndKeepsMissingOnes() throws IOException {
        Files.writeString(languageFolder.resolve("fr.yml"), "feed:\n  success: \"<gold>Miam !\"\n");

        MessageService messages = load("fr");

        assertEquals("Miam !", messages.plain(Message.FEED_SUCCESS));
        assertEquals("Vous ne pouvez pas utiliser /feed en combat.", messages.plain(Message.FEED_IN_COMBAT));
    }

    @Test
    void invalidLanguageFileFallsBackToBundledMessages() throws IOException {
        Files.writeString(languageFolder.resolve("en.yml"), "feed: [unclosed\n");

        MessageService messages = load("en");

        assertEquals("You cannot use /feed while in combat.", messages.plain(Message.FEED_IN_COMBAT));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("is invalid")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr"})
    void bundledFileDefinesEveryMessage(String language) throws IOException {
        YamlConfiguration bundled;
        try (InputStream input = JAR_RESOURCES.apply(MessageService.resourcePath(language))) {
            bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        for (Message message : Message.values()) {
            assertTrue(bundled.isString(message.path()), language + ".yml lacks " + message.path());
        }
    }

    @Test
    void insertsPlaceholders() {
        MessageService messages = load("en");
        Component remaining = messages.duration(Duration.ofMinutes(5));

        Component cooldown = messages.get(Message.FEED_COOLDOWN, Placeholder.component("remaining", remaining));

        assertEquals("You must wait 5m before using /feed again.", plain(cooldown));
    }

    @Test
    void formatsDurationsPerLanguage() {
        Duration duration = Duration.ofHours(1).plusMinutes(5).plusSeconds(3);

        assertEquals("1h 5m 3s", plain(load("en").duration(duration)));
        assertEquals("1 h 5 min 3 s", plain(load("fr").duration(duration)));
    }

    @Test
    void durationOmitsZeroUnitsAndRoundsUpToTheNextSecond() {
        MessageService messages = load("en");

        assertEquals("1h", plain(messages.duration(Duration.ofHours(1))));
        assertEquals("2m 1s", plain(messages.duration(Duration.ofSeconds(120).plusMillis(1))));
        assertEquals("1s", plain(messages.duration(Duration.ofMillis(200))));
    }

    private MessageService load(String language) {
        return MessageService.load(language, languageFolder, JAR_RESOURCES, logger);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
