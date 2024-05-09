package menu.bot;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Log4j2
public class BiteBoardProperties {
    private static final String CONFIG_FILE_PATH = "/bot/bite-board-bot.properties";
    private static Properties properties;

    public static final String DATA_STORAGE_PATH = "dataStoragePath";

    public static final String LANGUAGE = "language";

    public static final String MENSA_MENU_IMAGE_PREVIEW_SERVICE = "mensaMenuImagePreviewService";
    public static final String GOOGLE_IMAGE_API_KEY = "googleImageApiKey";
    public static final String GOOGLE_IMAGE_API_APPLICATION_ID = "googleImageApiApplicationId";

    public static final String DISCORD_CLIENT_ID = "discordClientId";
    public static final String DISCORD_BOT_TOKEN = "discordBotToken";

    public static Properties getProperties() {
        if (properties == null) {
            loadProperties();
        }
        return properties;
    }

    private static synchronized void loadProperties() {
        properties = new Properties();
        try (final InputStream inputStream = BiteBoardProperties.class.getResourceAsStream(CONFIG_FILE_PATH)) {
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                log.error("Unable to load configuration file: {}", CONFIG_FILE_PATH);
            }
        } catch (IOException e) {
            log.error("Error loading configuration file: {}", e.getMessage());
        }
    }
}
