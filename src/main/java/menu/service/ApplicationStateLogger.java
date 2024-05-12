package menu.service;

import lombok.extern.log4j.Log4j2;
import menu.bot.BotData;
import menu.providers.MenuItemsProvider;

import java.util.List;

@Log4j2
public class ApplicationStateLogger {

    public static void logApplicationSplashScreen() {
        final String projectPomVersion = BuildProperties.getProperties().getProperty(BuildProperties.PROJECT_VERSION);
        String projectHashVersion;
        try {
            projectHashVersion = JarHasher.getJarHash().substring(0, 16);
        } catch (Exception e) {
            projectHashVersion = "unknown hash, not started from jar file";
        }
        log.info("  ____   _  _            ____                           _    ____          _");
        log.info(" | __ ) (_)| |_   ___   | __ )   ___    __ _  _ __   __| |  | __ )   ___  | |_");
        log.info(" |  _ \\ | || __| / _ \\==|  _ \\  / _ \\  / _` || '__| / _` |==|  _ \\  / _ \\ | __|");
        log.info(" | |_) || || |_ |  __/==| |_) || (_) || (_| || |   | (_| |==| |_) || (_) || |_");
        log.info(" |____/ |_| \\__| \\___|  |____/  \\___/  \\__,_||_|    \\__,_|  |____/  \\___/  \\__|");
        log.info("");
        log.info("   Version: {} ~ {}", projectPomVersion, projectHashVersion);
        log.info("");
    }

    public static void logStartupStart() {
        log.info("Setting up Discord bot:");
    }

    public static void logApplicationStartupStepMessage(String message) {
        log.info(" - {}", message);
    }

    public static void logApplicationStartupStepMessageFollowup(String message) {
        log.info("   L {}", message);
    }

    public static void logStartupSelectLanguage(String language) {
        logApplicationStartupStepMessage("Selecting language: " + language);
    }

    public static void logStartupSetupMenuProviders(List<MenuItemsProvider> menuProviders) {
        logApplicationStartupStepMessage("Setting up " + menuProviders.size() + " menu providers");
    }

    public static void logStartupSetupImageSearch(String property) {
        logApplicationStartupStepMessage("Setting up image search: " + property);
    }

    public static void logStartupSetupBotData(String dataPath) {
        logApplicationStartupStepMessage("Setting up bot data from: " + dataPath);
    }

    public static void logStartupSetupBotDataFinished(BotData loaded) {
        logApplicationStartupStepMessageFollowup("Users: " + loaded.getUsers().size());
        logApplicationStartupStepMessageFollowup("Scheduled menu fetching: " + loaded.getAllPeriodicMenuChannels().size());
    }

    public static void logStartupSetupDiscordBot() {
        logApplicationStartupStepMessage("Setting up Discord bot");
    }

    public static void logStartupSetupScheduleRegularMenuPosting() {
        logApplicationStartupStepMessage("Scheduling regular menu posting timer");
    }

    public static void logStartupPhaseComplete() {
        log.info("");
        log.info(" --> Startup complete, bot is now running");
        log.info("");
    }
}
