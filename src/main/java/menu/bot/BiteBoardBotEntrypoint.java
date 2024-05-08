package menu.bot;

import lombok.extern.log4j.Log4j2;
import menu.bot.commands.MenuCommand;
import menu.providers.MenuItemsProviderManager;
import menu.providers.implementations.HochschuleMannheimTagessichtMenuProvider;
import menu.service.ImageSearcher;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.utils.ChunkingFilter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Log4j2
public class BiteBoardBotEntrypoint {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        log.info("Starting BiteBoardBot");

        final ImageSearcher.ImageSearch imageSearch = ImageSearcher.createImageSearch(BiteBoardProperties.getProperties());
        log.info("Selected image provider: {}", imageSearch.getClass().getSimpleName());

        final MenuItemsProviderManager menuProviders = new MenuItemsProviderManager();
        menuProviders.register(new HochschuleMannheimTagessichtMenuProvider());

        final BotData botData = new BotData(new File(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DATA_STORAGE_PATH)));

        final String botToken = BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DISCORD_BOT_TOKEN);
        final long clientId = Long.parseLong(BiteBoardProperties.getProperties().getProperty(BiteBoardProperties.DISCORD_CLIENT_ID));

        final MenuCommand menuCommand = new MenuCommand(menuProviders, imageSearch, botData);
        final JDA jda = JDABuilder.createDefault(botToken)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(menuCommand)
                .build();

        jda.awaitReady().updateCommands().addCommands(menuCommand.getCommandData()).queue();
    }
}
