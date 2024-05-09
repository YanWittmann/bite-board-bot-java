package menu.service;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Log4j2
public class BuildProperties {
    private static final String CONFIG_FILE_PATH = "/build.properties";
    private static Properties properties;

    public static final String PROJECT_VERSION = "project.version";

    public static Properties getProperties() {
        if (properties == null) {
            loadProperties();
        }
        return properties;
    }

    private static synchronized void loadProperties() {
        properties = new Properties();
        try (final InputStream inputStream = BuildProperties.class.getResourceAsStream(CONFIG_FILE_PATH)) {
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
