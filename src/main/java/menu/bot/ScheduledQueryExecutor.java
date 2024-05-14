package menu.bot;

import lombok.extern.log4j.Log4j2;
import menu.bot.commands.MenuCommand;
import menu.providers.MenuItem;
import menu.providers.MenuItemsProvider;
import menu.providers.MenuItemsProviderManager;
import menu.providers.MenuTime;
import menu.service.ApplicationStateLogger;
import menu.service.ImageSearcher;
import menu.service.LanguageManager;
import menu.service.TimeUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Log4j2
public class ScheduledQueryExecutor {

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    private final ImageSearcher.ImageSearch imageSearch;
    private final MenuCommand menuCommand;
    private final JDA jda;
    private final MenuItemsProviderManager menuProviders;

    public ScheduledQueryExecutor(JDA jda, MenuCommand menuCommand, ImageSearcher.ImageSearch imageSearch, MenuItemsProviderManager menuProviders) {
        this.imageSearch = imageSearch;
        this.menuCommand = menuCommand;
        this.jda = jda;
        this.menuProviders = menuProviders;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Schedule all the provided periodic menu queries.
     *
     * @param queries The periodic menu information to be scheduled.
     */
    public void scheduleAll(List<BotData.BotSendPeriodicMenuInfo> queries) {
        ApplicationStateLogger.logStartupSetupScheduleRegularMenuPosting();
        cancelAllScheduledTasks();

        for (BotData.BotSendPeriodicMenuInfo query : queries) {
            scheduleQuery(query);
        }
    }

    /**
     * Schedule a single query.
     *
     * @param query The query information containing when and what to post.
     */
    private void scheduleQuery(BotData.BotSendPeriodicMenuInfo query) {
        final String[] timeParts = query.getTime().split(":");
        if (timeParts.length != 3) {
            log.error("Invalid time format for periodic menu, expected HH:MM:SS: {}", query);
            return;
        }
        final LocalTime targetLocalTime = LocalTime.of(Integer.parseInt(timeParts[0]), Integer.parseInt(timeParts[1]), Integer.parseInt(timeParts[2]));
        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime targetTime = now.with(targetLocalTime);

        final boolean isInPast = now.isAfter(targetTime);
        if (isInPast) {
            targetTime = targetTime.plusDays(1);
        }

        long delay = Duration.between(now, targetTime).getSeconds();

        ApplicationStateLogger.logApplicationStartupStepMessageFollowup("Scheduling for channel [" + query.getChannelId() + "] at [" + query.getTime() +
                // next in dd:HH:MM:SS
                "], next in [" + String.format("%02d:%02d:%02d:%02d", delay / 86400, (delay % 86400) / 3600, (delay % 3600) / 60, delay % 60) + "] " +
                (isInPast ? "(time has passed today, scheduled for tomorrow)" : "(scheduling for today)"));

        final ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            scheduler.execute(() -> {
                try {
                    postMenu(query);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("[Interrupted] Failed to execute menu post: {}", e.getMessage(), e);
                } catch (ExecutionException e) {
                    log.error("[ExecutionException] Failed to execute menu post: {}", e.getMessage(), e);
                }
            });
        }, delay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);

        scheduledTasks.put(query.getChannelId() + ":" + query.getTime(), future);
    }

    /**
     * Post the menu for a scheduled query.
     *
     * @param query The query information.
     */
    private void postMenu(BotData.BotSendPeriodicMenuInfo query) throws ExecutionException, InterruptedException {
        final String channelId = query.getChannelId();
        final MenuItemsProvider provider = menuProviders.get(query.getProvider());

        if (provider == null) {
            log.error("Provider for channel {} is not available: {}", channelId, query.getProvider());
            return;
        }

        final TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.error("Channel {} not found to post the periodic menu into", channelId);
            return;
        }

        ZonedDateTime zdt = TimeUtils.getUtcNow();
        zdt = zdt.plusMinutes(query.getAddTime());
        final Instant queryTime = zdt.toInstant();

        final MenuTime menuTime = new MenuTime(zdt.getYear(), zdt.getMonthValue(), zdt.getDayOfMonth());
        MenuCommand.ConstructedMenuEmbed menuEmbed = menuCommand.constructMenuEmbed(provider, new MenuCommand.MenuCommandData("Menu", Date.from(queryTime), menuTime));

        if (menuEmbed.getMenuItems().isEmpty()) {
            channel.sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.periodicMenu.noMenuForToday", provider.toMdString(), TimeUtils.formatDay(zdt))).queue();
            log.info("No periodic menu found for channel [{}] with provider [{}] for [{}]", channelId, provider.getName(), queryTime.toString());
            return;
        }

        log.info("Posting periodic menu for channel [{}] with provider [{}] for [{}]", channelId, provider.getName(), queryTime.toString());
        final MessageCreateAction messageAction = channel.sendMessageEmbeds(menuEmbed.getMenuEmbed());
        final Message message = messageAction.complete();
        menuCommand.attachReactions(provider, menuEmbed.getMenuItems(), message, BiteBoardProperties.MENU_VOTING_ON_SCHEDULED_REQUEST);

        final List<MenuItem> menuItems = menuEmbed.getMenuItems();

        final List<MenuCommand.ConstructedMenuImageEmbed> imageEmbeds = MenuCommand.constructImageEmbed(imageSearch, menuItems);
        if (!imageEmbeds.isEmpty()) {
            log.info("Posting [{}] image embeds for channel [{}] on [{}]", imageEmbeds.size(), channel.getId(), queryTime.toString());
            for (MenuCommand.ConstructedMenuImageEmbed imageEmbed : imageEmbeds) {
                final MessageCreateAction action = channel.sendMessageEmbeds(imageEmbed.getImageEmbed());
                if (imageEmbed.getImageFile() != null) {
                    action.addFiles(FileUpload.fromData(imageEmbed.getImageFile()));
                }
                action.queue();
            }
        }
    }

    /**
     * Cancel all scheduled tasks.
     */
    private void cancelAllScheduledTasks() {
        ApplicationStateLogger.logApplicationStartupStepMessageFollowup("Clearing [" + scheduledTasks.size() + "] scheduled tasks");
        scheduledTasks.values().forEach(future -> future.cancel(true));
        scheduledTasks.clear();
    }

    /**
     * Shutdown the executor and cancel all tasks.
     */
    public void shutdown() {
        cancelAllScheduledTasks();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}