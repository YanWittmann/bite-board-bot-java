package menu.bot;

import lombok.extern.log4j.Log4j2;
import menu.bot.commands.MenuCommand;
import menu.providers.MenuItem;
import menu.providers.MenuItemsProvider;
import menu.providers.MenuItemsProviderManager;
import menu.providers.MenuTime;
import menu.service.ImageSearcher;
import menu.service.LanguageManager;
import menu.service.TimeUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Log4j2
public class BiteBoardBot {

    private static final long PERIODIC_MENU_CHECK_TIME_INTERVAL = 1000 * 10;

    private final ImageSearcher.ImageSearch imageSearch;
    private final MenuItemsProviderManager menuProviders = new MenuItemsProviderManager();
    private final BotData botData;
    private final MenuCommand menuCommand;
    private final JDA jda;

    public BiteBoardBot(List<MenuItemsProvider> providers) throws InterruptedException {
        LanguageManager.get().setLang(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.LANGUAGE));

        providers.forEach(menuProviders::register);

        this.imageSearch = ImageSearcher.createImageSearch(BiteBoardProperties.getProperties());
        try {
            this.botData = new BotData(new File(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DATA_STORAGE_PATH)));
        } catch (Exception e) {
            throw new RuntimeException("Error parsing bot data from file: " + e.getMessage());
        }
        this.menuCommand = new MenuCommand(menuProviders, imageSearch, botData);

        this.jda = JDABuilder.createDefault(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DISCORD_BOT_TOKEN))
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(menuCommand)
                .build();

        this.jda.awaitReady()
                .updateCommands().addCommands(menuCommand.getCommandData())
                .queue();

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    checkForPeriodicMenuPosting();
                } catch (ExecutionException | InterruptedException e) {
                    log.error("Error checking for periodic menu posting: {}", e.getMessage());
                }
            }
        }, 0, PERIODIC_MENU_CHECK_TIME_INTERVAL);
    }

    private void checkForPeriodicMenuPosting() throws ExecutionException, InterruptedException {
        final List<BotData.BotSendPeriodicMenuInfo> channelsData = this.botData.getAllPeriodicMenuChannels();

        for (BotData.BotSendPeriodicMenuInfo channelData : channelsData) {
            final String channelId = channelData.getChannelId();
            final String time = channelData.getTime();

            // Check if the scheduled time has passed within the check interval
            if (!checkUTCTime(time, BiteBoardBot.PERIODIC_MENU_CHECK_TIME_INTERVAL)) {
                continue;
            }

            if (channelId == null) {
                log.error("Channel ID is null for periodic menu posting");
                return;
            }

            final MenuItemsProvider provider = this.menuProviders.get(channelData.getProvider());
            if (provider == null) {
                log.error("Provider for channel {} is not available: {}", channelId, channelData.getProvider());
                continue;
            }

            final int addTime = channelData.getAddTime();
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(TimeUtils.getUtcNow());
            calendar.add(Calendar.MINUTE, addTime);
            Date queryTime = calendar.getTime();
            log.info("Query time {} = {} + {} minutes", queryTime.toInstant(), new Date().toInstant(), addTime);

            final MenuTime menuTime = new MenuTime(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DATE));

            log.info("Posting menu for channel {} with provider {}", channelId, provider.getName());
            MenuCommand.ConstructedMenuEmbed menuEmbed = menuCommand.constructMenuEmbed(provider, new MenuCommand.MenuCommandData("Menu", queryTime, menuTime));

            final TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log.error("Channel {} not found to post the periodic menu into", channelId);
                return;
            }

            if (menuEmbed.getMenuItems().isEmpty()) {
                channel.sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.periodicMenu.noMenuForToday", provider.toMdString(), queryTime.toString())).queue();
                log.info("No periodic menu found for channel {} on {}", channel.getId(), queryTime.toString());
                return;
            }

            channel.sendMessageEmbeds(menuEmbed.getMenuEmbed()).queue();

            final List<MenuItem> menuItems = menuEmbed.getMenuItems();

            final List<MenuCommand.ConstructedMenuImageEmbed> imageEmbeds = MenuCommand.constructImageEmbed(this.imageSearch, menuItems);
            if (!imageEmbeds.isEmpty()) {
                for (MenuCommand.ConstructedMenuImageEmbed imageEmbed : imageEmbeds) {
                    MessageCreateAction action = channel.sendMessageEmbeds(imageEmbed.getImageEmbed());
                    if (imageEmbed.getImageFile() != null) {
                        action.addFiles(FileUpload.fromData(imageEmbed.getImageFile()));
                    }
                    action.queue();
                }
            }
        }
    }

    private boolean checkUTCTime(String time, long periodicMenuCheckTimeInterval) {
        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);

        Date nowUTC = TimeUtils.getUtcNow();
        Date timeUTC = TimeUtils.createUtcTime(hours, minutes, seconds);

        long diff = nowUTC.getTime() - timeUTC.getTime();
        return diff >= 0 && diff < periodicMenuCheckTimeInterval;
    }
}
