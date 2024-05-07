package menu.bot.commands;

import lombok.extern.log4j.Log4j2;
import menu.bot.BotData;
import menu.providers.MenuItemsProvider;
import menu.providers.MenuItemsProviderManager;
import menu.service.ImageSearcher;
import menu.service.LanguageManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;

import java.util.Map;

@Log4j2
public class MenuCommand extends ListenerAdapter {

    private final MenuItemsProviderManager menuProviders;
    private final ImageSearcher.ImageSearch imageSearch;
    private final BotData botData;

    public MenuCommand(MenuItemsProviderManager menuProviders, ImageSearcher.ImageSearch imageSearch, BotData botData) {
        this.menuProviders = menuProviders;
        this.imageSearch = imageSearch;
        this.botData = botData;
    }

    public CommandData getCommandData() {
        return new CommandDataImpl("menu", LanguageManager.get().getTranslation("command.menu.description"))
                .addSubcommands(
                        createFetchMenuForDaySubcommand("today"),
                        createFetchMenuForDaySubcommand("tomorrow"),
                        createFetchMenuForDaySubcommand("overmorrow")
                );
    }

    private SubcommandData createFetchMenuForDaySubcommand(String nameKey) {
        final SubcommandData subcommand = new SubcommandData(
                nameKey,
                LanguageManager.get().getTranslation("command.menu.options." + nameKey + ".description")
        );
        for (Map.Entry<String, DiscordLocale> langToLocale : LanguageManager.get().getAvailableDiscordLocales().entrySet()) {
            subcommand.setNameLocalization(langToLocale.getValue(), LanguageManager.get().getTranslation(langToLocale.getKey(), "command.menu.options." + nameKey + ".name"));
            subcommand.setDescriptionLocalization(langToLocale.getValue(), LanguageManager.get().getTranslation(langToLocale.getKey(), "command.menu.options." + nameKey + ".description"));
        }
        return subcommand;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("menu")) return;

        event.deferReply().queue();

        final String user = event.getUser().getAsTag();
        final String subcommandName = event.getSubcommandName();
        final MenuItemsProvider menuProvider = botData.findUserPreferredMenuProvider(user, menuProviders);

        if (menuProvider == null) {
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.menu.response.noMenuProvider")).queue();
            return;
        }

        log.info("Fetching menu for user [{}] using provider [{}] with subcommand [{}]", user, menuProvider.getName(), subcommandName);

        // debug just respond to user now
        event.getHook().sendMessage("Fetching menu for user [" + user + "] using provider [" + menuProvider.getName() + "] with subcommand [" + subcommandName + "]").queue();
    }
}
