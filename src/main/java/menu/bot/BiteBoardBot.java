package menu.bot;

import lombok.extern.log4j.Log4j2;
import menu.bot.commands.MenuCommand;
import menu.providers.MenuItemsProvider;
import menu.providers.MenuItemsProviderManager;
import menu.service.ApplicationStateLogger;
import menu.service.ImageSearcher;
import menu.service.LanguageManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.utils.ChunkingFilter;

import java.io.File;
import java.util.List;

@Log4j2
public class BiteBoardBot {

    private static final long PERIODIC_MENU_CHECK_TIME_INTERVAL = 1000 * 10;

    private final ImageSearcher.ImageSearch imageSearch;
    private final MenuItemsProviderManager menuProviders = new MenuItemsProviderManager();
    private final BotData botData;
    private final MenuCommand menuCommand;
    private final JDA jda;
    private final ScheduledQueryExecutor scheduledQueryExecutor;

    public BiteBoardBot(List<MenuItemsProvider> providers) throws InterruptedException {
        ApplicationStateLogger.logStartupStart();
        LanguageManager.get();
        ApplicationStateLogger.logStartupSelectLanguage(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.LANGUAGE));
        LanguageManager.get().setLang(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.LANGUAGE));

        ApplicationStateLogger.logStartupSetupMenuProviders(providers);
        for (MenuItemsProvider provider : providers) {
            menuProviders.register(provider);
            ApplicationStateLogger.logApplicationStartupStepMessageFollowup(provider.getName() + " as " + provider.getClass().getSimpleName() + " (" + provider.getDisplayMenuLink() + ")");
        }

        ApplicationStateLogger.logStartupSetupImageSearch(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.MENSA_MENU_IMAGE_PREVIEW_SERVICE));
        this.imageSearch = ImageSearcher.createImageSearch(BiteBoardProperties.getProperties());

        ApplicationStateLogger.logStartupSetupBotData(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DATA_STORAGE_PATH));
        try {
            this.botData = new BotData(new File(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DATA_STORAGE_PATH)));
            this.botData.addPeriodicMenuChangeListener(this::updateScheduledTasks);
            ApplicationStateLogger.logStartupSetupBotDataFinished(this.botData);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing bot data from file: " + e.getMessage());
        }

        this.menuCommand = new MenuCommand(menuProviders, imageSearch, botData);

        ApplicationStateLogger.logStartupSetupDiscordBot();
        this.jda = JDABuilder.createDefault(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DISCORD_BOT_TOKEN))
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(menuCommand)
                .build();

        this.jda.awaitReady()
                .updateCommands().addCommands(menuCommand.getCommandData())
                .queue();

        this.scheduledQueryExecutor = new ScheduledQueryExecutor(jda, menuCommand, imageSearch, menuProviders);
        scheduledQueryExecutor.scheduleAll(this.botData.getAllPeriodicMenuChannels());

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        ApplicationStateLogger.logStartupPhaseComplete();
    }

    private void updateScheduledTasks(List<BotData.BotSendPeriodicMenuInfo> channelsData) {
        scheduledQueryExecutor.scheduleAll(channelsData);
    }

    /**
     * Gracefully shutdown the bot and the ScheduledQueryExecutor.
     */
    public void shutdown() {
        scheduledQueryExecutor.shutdown();
        jda.shutdown();
        log.info("BiteBoardBot shutdown completed.");
    }
}
