package menu.bot;

import lombok.extern.log4j.Log4j2;
import menu.service.ApplicationStateLogger;

import java.io.File;
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

    public static final String MENU_VOTING_ON_USER_REQUEST = "menuVotingOnUserRequest";
    public static final String MENU_VOTING_ON_SCHEDULED_REQUEST = "menuVotingOnScheduledRequest";

    public static Properties getProperties() {
        if (properties == null) {
            try {
                loadProperties();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return properties;
    }

    private static synchronized void loadProperties() throws IOException {
        properties = new Properties();

        ApplicationStateLogger.logApplicationStartupStepMessage("Loading bot properties");

        final String configuredExternalConfigPath = System.getenv("BITE_BOARD_CONFIG_PATH");
        if (configuredExternalConfigPath != null) {
            ApplicationStateLogger.logApplicationStartupStepMessageFollowup("Loading external configuration from: " + configuredExternalConfigPath);
            final File externalConfigFile = new File(configuredExternalConfigPath);
            if (externalConfigFile.exists()) {
                try (final InputStream inputStream = externalConfigFile.toURI().toURL().openStream()) {
                    properties.load(inputStream);
                }
                ApplicationStateLogger.logApplicationStartupStepMessageFollowup("Loaded " + properties.size() + " properties from external configuration");
                return;
            } else {
                throw new RuntimeException("Configured external configuration file does not exist: " + configuredExternalConfigPath);
            }
        }

        ApplicationStateLogger.logApplicationStartupStepMessageFollowup("Loading internal configuration from: " + CONFIG_FILE_PATH);
        try (final InputStream inputStream = BiteBoardProperties.class.getResourceAsStream(CONFIG_FILE_PATH)) {
            if (inputStream != null) {
                properties.load(inputStream);
                ApplicationStateLogger.logApplicationStartupStepMessageFollowup("Loaded " + properties.size() + " properties from internal configuration");
            } else {
                throw new RuntimeException("Unable to load configuration file: " + CONFIG_FILE_PATH);
            }
        }
    }
}
