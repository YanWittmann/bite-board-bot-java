package menu.bot.commands;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import menu.bot.BotData;
import menu.providers.MenuItem;
import menu.providers.*;
import menu.service.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Log4j2
public class MenuCommand extends ListenerAdapter {

    private final static List<String> MENU_FETCH_SUBCOMMANDS = Arrays.asList("today", "tomorrow", "overmorrow", "monday", "tuesday", "wednesday", "thursday", "friday");

    private final MenuItemsProviderManager menuProviders;
    private final ImageSearcher.ImageSearch imageSearch;
    private final BotData botData;

    public MenuCommand(MenuItemsProviderManager menuProviders, ImageSearcher.ImageSearch imageSearch, BotData botData) {
        this.menuProviders = menuProviders;
        this.imageSearch = imageSearch;
        this.botData = botData;
    }

    public CommandData getCommandData() {
        ApplicationStateLogger.logApplicationStartupStepMessageFollowup("Creating command data for menu command");
        final CommandDataImpl commandData = new CommandDataImpl("menu", LanguageManager.get().getTranslation("command.menu.description"));

        for (String subcommand : MENU_FETCH_SUBCOMMANDS) {
            commandData.addSubcommands(createFetchMenuForDaySubcommand(subcommand));
        }

        createSettingSubcommands().forEach(commandData::addSubcommands);

        return commandData;
    }

    private SubcommandData createFetchMenuForDaySubcommand(String nameKey) {
        final SubcommandData subcommand = new SubcommandData(
                nameKey,
                LanguageManager.get().getTranslation("command.menu.options." + nameKey + ".description")
        );
        populateSubcommandLocalization(subcommand, "command.menu.options." + nameKey + ".name", "command.menu.options." + nameKey + ".description");
        return subcommand;
    }

    private List<SubcommandData> createSettingSubcommands() {
        final List<SubcommandData> subcommands = new ArrayList<>();
        final List<String> availableMenuProviderNames = new ArrayList<>(menuProviders.getProviders().keySet());

        final SubcommandData setMenuProviderSubcommand = new SubcommandData(
                "provider",
                LanguageManager.get().getTranslation("command.settingsmenu.options.setprovider.description")
        );
        populateSubcommandLocalization(setMenuProviderSubcommand, "command.settingsmenu.options.setprovider.name", "command.settingsmenu.options.setprovider.description");
        final OptionData providerOption = new OptionData(OptionType.STRING, "provider", LanguageManager.get().getTranslation("command.settingsmenu.options.setprovider.description"), true);
        availableMenuProviderNames.forEach(provider -> providerOption.addChoice(provider, provider));
        setMenuProviderSubcommand.addOptions(providerOption);
        subcommands.add(setMenuProviderSubcommand);

        final SubcommandData listMenuProvidersSubcommand = new SubcommandData(
                "listproviders",
                LanguageManager.get().getTranslation("command.settingsmenu.options.listproviders.description")
        );
        populateSubcommandLocalization(listMenuProvidersSubcommand, "command.settingsmenu.options.listproviders.name", "command.settingsmenu.options.listproviders.description");
        subcommands.add(listMenuProvidersSubcommand);

        final SubcommandData setPeriodicMenuSubcommand = new SubcommandData(
                "schedule",
                LanguageManager.get().getTranslation("command.settingsmenu.options.periodicMenu.description")
        );
        populateSubcommandLocalization(setPeriodicMenuSubcommand, "command.settingsmenu.options.periodicMenu.name", "command.settingsmenu.options.periodicMenu.description");
        final OptionData providerOptionForPeriodic = new OptionData(OptionType.STRING, "provider", LanguageManager.get().getTranslation("command.settingsmenu.options.periodicMenu.provider.description"), true);
        availableMenuProviderNames.forEach(provider -> providerOptionForPeriodic.addChoice(provider, provider));
        setPeriodicMenuSubcommand.addOptions(providerOptionForPeriodic);
        setPeriodicMenuSubcommand.addOption(OptionType.STRING, "time", LanguageManager.get().getTranslation("command.settingsmenu.options.periodicMenu.time.description"), true);
        setPeriodicMenuSubcommand.addOption(OptionType.INTEGER, "add", LanguageManager.get().getTranslation("command.settingsmenu.options.periodicMenu.addTime.description"), true);
        subcommands.add(setPeriodicMenuSubcommand);

        final SubcommandData setTimeSubcommand = new SubcommandData(
                "time",
                LanguageManager.get().getTranslation("command.settingsmenu.options.time.description")
        );
        populateSubcommandLocalization(setTimeSubcommand, null, "command.settingsmenu.options.time.description");
        subcommands.add(setTimeSubcommand);

        return subcommands;
    }

    private void populateSubcommandLocalization(SubcommandData setMenuProviderSubcommand, String nameKey, String descriptionKey) {
        for (Map.Entry<String, DiscordLocale> langToLocale : LanguageManager.get().getAvailableDiscordLocales().entrySet()) {
            if (nameKey != null) {
                setMenuProviderSubcommand.setNameLocalization(langToLocale.getValue(), LanguageManager.get().getTranslation(nameKey, langToLocale.getKey()));
            }
            if (descriptionKey != null) {
                setMenuProviderSubcommand.setDescriptionLocalization(langToLocale.getValue(), LanguageManager.get().getTranslation(descriptionKey, langToLocale.getKey()));
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("menu")) {
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.generic.invalidCommandName")).queue();
            return;
        }

        event.deferReply().queue();

        try {
            final String user = event.getUser().getName();
            final String subcommandName = event.getSubcommandName();
            log.info("Received [{}] command from user [{}] in [{}/{}/{}] with subcommand [{}] and options {}",
                    event.getName(), user,
                    event.getGuild() == null ? "null" : event.getGuild().getName(), event.getChannel().getName(), event.getChannel().getId(),
                    subcommandName, event.getOptions());

            if (subcommandName == null) {
                event.getHook().sendMessage(LanguageManager.get().getTranslation("command.settingsmenu.response.unknownSubcommand.description")).queue();
                return;
            }

            if (MENU_FETCH_SUBCOMMANDS.contains(subcommandName)) {
                fetchMenuSubcommandExecution(event, user, subcommandName);
            } else {
                switch (subcommandName) {
                    case "provider":
                        setMenuProviderSubcommandExecution(event, user);
                        break;
                    case "listproviders":
                        listMenuProvidersSubcommandExecution(event);
                        break;
                    case "schedule":
                        scheduleMenuSubcommandExecution(event, user);
                        break;
                    case "time":
                        echoUtcTimeSubcommandExecution(event);
                        break;
                    default:
                        event.getHook().sendMessage(LanguageManager.get().getTranslation("command.settingsmenu.response.unknownSubcommand.description")).queue();
                        break;
                }
            }
        } catch (Exception e) {
            log.error("Error processing menu command", e);
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.generic.errorExecutingCommand")).queue();
        }
    }

    private void setMenuProviderSubcommandExecution(SlashCommandInteractionEvent event, String user) {
        final String provider = event.getOption("provider").getAsString();
        final Set<String> providerNames = menuProviders.getProviders().keySet();

        if (!providerNames.contains(provider)) {
            event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.setprovider.providerNotAvailable", provider)).queue();
            log.error("User {} tried to set provider to {}, but it is not available", user, provider);
            return;
        }

        botData.setUserPreferredMenuProvider(user, provider);
        event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.setprovider.success", provider)).queue();
        log.info("User {} set provider to {}", user, provider);
    }

    private void listMenuProvidersSubcommandExecution(SlashCommandInteractionEvent event) {
        if (menuProviders.getProviders().values().isEmpty()) {
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.settingsmenu.response.listproviders.noProviders")).queue();
        }

        final StringBuilder providersList = new StringBuilder();
        for (MenuItemsProvider provider : menuProviders.getProviders().values()) {
            providersList.append("- ").append(provider.toMdString()).append("\n");
        }
        event.getHook().sendMessage(LanguageManager.get().getTranslation("command.settingsmenu.response.listproviders.success") + "\n" + providersList).queue();
    }

    private void scheduleMenuSubcommandExecution(SlashCommandInteractionEvent event, String user) {
        if (!botData.isUserRole(user, "periodic")) {
            event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.periodicMenu.noPermission")).queue();
            log.error("User {} tried to set periodic menu, but has no permission", user);
            return;
        }

        if (event.getOption("provider") == null) {
            event.getHook().sendMessage("Missing option: provider").queue();
            log.error("User {} tried to set periodic menu without provider", user);
            return;
        }
        final String provider = event.getOption("provider").getAsString();
        final MenuItemsProvider foundProvider = menuProviders.get(provider);

        if (foundProvider == null) {
            event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.periodicMenu.providerNotAvailable", provider)).queue();
            log.error("User {} tried to set periodic menu with provider {}, but it is not available", user, provider);
            return;
        }

        // Time Validation
        final String time = event.getOption("time").getAsString();
        final String[] timeParts = time.split(":");
        final boolean isValidFormat = timeParts.length == 3
                && Integer.parseInt(timeParts[0]) <= 23 && Integer.parseInt(timeParts[1]) <= 59 && Integer.parseInt(timeParts[2]) <= 59
                && Integer.parseInt(timeParts[0]) >= 0 && Integer.parseInt(timeParts[1]) >= 0 && Integer.parseInt(timeParts[2]) >= 0;

        if (!isValidFormat) {
            event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.periodicMenu.invalidTime", time)).queue();
            log.error("User {} tried to set periodic menu with invalid time {}", user, time);
            return;
        }

        final long addTime = event.getOption("add").getAsLong();
        final String channelId = event.getChannel().getId();

        botData.setPeriodicMenuChannel(channelId, time, provider, (int) addTime);

        event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.periodicMenu.success", time, foundProvider.toMdString(), addTime)).queue();
        log.info("User {} set periodic menu for channel {} to {} with provider {}", user, channelId, time, provider);
    }

    private void echoUtcTimeSubcommandExecution(SlashCommandInteractionEvent event) {
        event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.time.currentTime", TimeUtils.formatTime(TimeUtils.getUtcNow()))).queue();
    }

    private void fetchMenuSubcommandExecution(SlashCommandInteractionEvent event, String user, String subcommandName) {
        final MenuItemsProvider menuProvider = botData.findUserPreferredMenuProvider(user, menuProviders);

        if (menuProvider == null) {
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.menu.response.menu.noMenuProvider")).queue();
            return;
        }

        log.info("Fetching menu for user [{}] using provider [{}] with subcommand [{}]", user, menuProvider.getName(), subcommandName);

        final MenuCommandData menuCommandData = parseFetchMenuCommand(event);
        final ConstructedMenuEmbed menuEmbedResult;
        try {
            menuEmbedResult = constructMenuEmbed(menuProvider, menuCommandData);
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error fetching menu for user [{}] using provider [{}] with subcommand [{}]", user, menuProvider.getName(), subcommandName, e);
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.menu.response.error")).queue();
            return;
        }

        if (menuEmbedResult.getMenuItems().isEmpty()) {
            event.getHook().sendMessage(LanguageManager.get().fillTranslation("command.settingsmenu.response.periodicMenu.noMenuForToday", menuProvider.toMdString(), TimeUtils.formatDay(menuCommandData.getTargetDate()))).queue();
            log.info("No menu found for user [{}] using provider [{}] with subcommand [{}]", user, menuProvider.getName(), subcommandName);
            return;
        }

        event.getHook().sendMessageEmbeds(menuEmbedResult.getMenuEmbed()).queue();

        final List<ConstructedMenuImageEmbed> imageEmbeds = constructImageEmbed(imageSearch, menuEmbedResult.getMenuItems());
        for (ConstructedMenuImageEmbed imageEmbed : imageEmbeds) {
            final WebhookMessageCreateAction<Message> action = event.getHook().sendMessageEmbeds(imageEmbed.getImageEmbed());
            if (imageEmbed.getImageFile() != null) {
                action.addFiles(FileUpload.fromData(imageEmbed.getImageFile()));
            }
            action.queue();
        }
    }

    @Data
    public static class MenuCommandData {
        private final String embedTitle;
        private final Date targetDate;
        private final MenuTime menuTime;
    }

    @Data
    public static class ConstructedMenuEmbed {
        private final MessageEmbed menuEmbed;
        private final List<MenuItem> menuItems;
    }

    @Data
    public static class ConstructedMenuImageEmbed {
        private final MessageEmbed imageEmbed;
        private final File imageFile;
    }

    public ConstructedMenuEmbed constructMenuEmbed(MenuItemsProvider menuProvider, MenuCommandData menuCommandData) throws ExecutionException, InterruptedException {
        final List<MenuItem> menuItems = menuProvider.getMenuItemsForDate(menuCommandData.getMenuTime()).get();

        final EmbedBuilder menuEmbed = new EmbedBuilder()
                .setTitle(menuCommandData.getEmbedTitle() + " - " + TimeUtils.formatDay(menuCommandData.getTargetDate()))
                .setDescription(LanguageManager.get().getTranslation("command.menu.response.menu.description"))
                .setColor(Color.decode("#0099ff"))
                .setTimestamp(new Date().toInstant())
                .setFooter(menuProvider.getName(), null)
                .setThumbnail(menuProvider.getProviderThumbnail());

        final Set<MenuItemFeature> allFeatures = new HashSet<>();

        for (MenuItem item : menuItems) {
            final StringBuilder itemDescriptionBuilder = new StringBuilder();

            if (item.getIngredients().size() > 1) {
                final List<String> ingredientNames = item.getIngredients().stream()
                        .skip(1)
                        .map(MenuItemIngredient::getName)
                        .collect(Collectors.toList());
                itemDescriptionBuilder.append(String.join(", ", ingredientNames));
            }

            if (itemDescriptionBuilder.length() == 0) {
                itemDescriptionBuilder.append(LanguageManager.get().getTranslation("command.menu.response.menu.noIngredientsDescription"));
            }

            final Set<MenuItemFeature> features = new HashSet<>();
            for (MenuItemIngredient ingredient : item.getIngredients()) {
                for (MenuItemFeature feature : ingredient.getFeatures()) {
                    features.add(feature);
                    allFeatures.add(feature);
                }
            }
            if (!features.isEmpty()) {
                final List<String> featureShortIds = features.stream()
                        .map(MenuItemFeature::getShortId)
                        .collect(Collectors.toList());
                itemDescriptionBuilder.append("\n").append(String.join(", ", featureShortIds));
            }

            final StringBuilder titleBuilder = new StringBuilder();
            if (!item.getIngredients().isEmpty()) {
                titleBuilder.append(item.getIngredients().get(0).getName());
            }
            titleBuilder.append(" (").append(item.getName() != null ? item.getName() : LanguageManager.get().getTranslation("command.menu.response.menu.noMenuName")).append(")");
            if (item.getPrice() != null && item.getUnit() != null) {
                titleBuilder.append(" - ").append(item.getPrice());
                if (!item.getUnit().equals("Portion")) {
                    titleBuilder.append(" ").append(item.getUnit());
                }
            }
            menuEmbed.addField(titleBuilder.toString(), itemDescriptionBuilder.toString(), false);
        }

        if (!allFeatures.isEmpty()) {
            List<String> featureDescriptions = allFeatures.stream()
                    .map(f -> f.getShortId() + ": " + f.getName())
                    .collect(Collectors.toList());
            String ingredientsEmbedContent = String.join(", ", featureDescriptions);
            menuEmbed.addField(LanguageManager.get().getTranslation("command.menu.response.menu.ingredients"), ingredientsEmbedContent, false);
        } else {
            log.warn("No features found in menu items");
        }

        return new ConstructedMenuEmbed(menuEmbed.build(), menuItems);
    }

    public static List<ConstructedMenuImageEmbed> constructImageEmbed(ImageSearcher.ImageSearch imageSearch, List<MenuItem> menuItems) {
        final ImageSearcher.ImageDisplayMode preferredImageDisplayMode = imageSearch.preferredImageDisplayMode();

        final List<String> irrelevantImageItems = Arrays.asList("Salatbuffet", "Dessert");
        final List<MenuItem> relevantItems = menuItems.stream()
                .filter(item -> !irrelevantImageItems.contains(item.getName() != null ? item.getName() : ""))
                .collect(Collectors.toList());

        final List<String> imageQueries = new ArrayList<>();
        for (MenuItem item : relevantItems) {
            if (!item.isShouldFetchImages()) continue;

            final String query = "Mensa Gericht " + item.getIngredients().stream()
                    .map(MenuItemIngredient::getName)
                    .filter(i -> !Arrays.asList("frische Kräuter", "Beilagensalat").contains(i))
                    .collect(Collectors.joining(" "));
            imageQueries.add(query);
        }

        if (preferredImageDisplayMode == ImageSearcher.ImageDisplayMode.SEPARATE) {
            final List<ConstructedMenuImageEmbed> embeds = new ArrayList<>();
            for (String query : imageQueries) {
                try {
                    final List<String> images = imageSearch.searchImages(query);
                    if (!images.isEmpty()) {
                        final List<String> filteredImages = images.stream()
                                .filter(i -> i.endsWith(".jpg") || i.endsWith(".png") || i.endsWith(".jpeg"))
                                .filter(i -> i.startsWith("https://") || i.startsWith("http://"))
                                .collect(Collectors.toList());

                        final int maxImages = 1;
                        final List<String> selectedImages = filteredImages.subList(0, Math.min(filteredImages.size(), maxImages));
                        final String image = selectedImages.get((int) (Math.random() * selectedImages.size()));
                        log.info("Fetched image for {}: {}", query, image);

                        final EmbedBuilder imageEmbed = new EmbedBuilder()
                                .setTitle("Preview")
                                .setImage(image)
                                .setColor(Color.decode("#0099ff"))
                                .setTimestamp(new Date().toInstant());

                        embeds.add(new ConstructedMenuImageEmbed(imageEmbed.build(), null));
                    }
                } catch (IOException e) {
                    log.error("Failed to fetch images for query: {}", query, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            return embeds;

        } else if (preferredImageDisplayMode == ImageSearcher.ImageDisplayMode.COMBINED) {

            try {
                final int desiredHeight = 300;
                final BufferedImage combinedImage = ImageUtils.createCombinedImageFromQueries(imageSearch, imageQueries, desiredHeight);
                final File file = new File("tmp-combined-mensa-image.png");
                ImageIO.write(combinedImage, "png", file);

                final MessageEmbed imageEmbed = new EmbedBuilder()
                        .setTitle(LanguageManager.get().getTranslation("command.menu.response.image.combined.title"))
                        .setColor(Color.decode("#0099ff"))
                        .setTimestamp(new Date().toInstant())
                        .setImage("attachment://" + file.getName())
                        .build();

                return Collections.singletonList(new ConstructedMenuImageEmbed(imageEmbed, file));
            } catch (Exception e) {
                log.error("Failed to create combined image for menu items: {}", e.getMessage(), e);
            }
        }

        return Collections.emptyList();
    }

    public static MenuCommandData parseFetchMenuCommand(SlashCommandInteractionEvent interaction) {
        LocalDate targetDate = LocalDate.now();
        final String subcommand = interaction.getSubcommandName();

        if (subcommand == null) {
            return new MenuCommandData("Today's Menu",
                    Date.from(targetDate.atStartOfDay().atZone(TimeZone.getDefault().toZoneId()).toInstant()),
                    new MenuTime(targetDate.getYear(), targetDate.getMonthValue(), targetDate.getDayOfMonth()));
        }

        switch (subcommand) {
            case "today":
                targetDate = LocalDate.now();
                break;
            case "tomorrow":
                targetDate = LocalDate.now().plusDays(1);
                break;
            case "overmorrow":
                targetDate = LocalDate.now().plusDays(2);
                break;
            case "monday":
                targetDate = LocalDate.now().with(DayOfWeek.MONDAY);
                if (targetDate.isBefore(LocalDate.now())) {
                    targetDate = targetDate.plusWeeks(1);
                }
                break;
            case "tuesday":
                targetDate = LocalDate.now().with(DayOfWeek.TUESDAY);
                if (targetDate.isBefore(LocalDate.now())) {
                    targetDate = targetDate.plusWeeks(1);
                }
                break;
            case "wednesday":
                targetDate = LocalDate.now().with(DayOfWeek.WEDNESDAY);
                if (targetDate.isBefore(LocalDate.now())) {
                    targetDate = targetDate.plusWeeks(1);
                }
                break;
            case "thursday":
                targetDate = LocalDate.now().with(DayOfWeek.THURSDAY);
                if (targetDate.isBefore(LocalDate.now())) {
                    targetDate = targetDate.plusWeeks(1);
                }
                break;
            case "friday":
                targetDate = LocalDate.now().with(DayOfWeek.FRIDAY);
                if (targetDate.isBefore(LocalDate.now())) {
                    targetDate = targetDate.plusWeeks(1);
                }
                break;
            default:
                break;
        }

        String title = "Today's Menu";
        if (subcommand.equals(LanguageManager.get().getTranslation("command.menu.options.tomorrow.name"))) {
            title = LanguageManager.get().getTranslation("command.menu.options.tomorrow.description");
        } else if (subcommand.equals(LanguageManager.get().getTranslation("command.menu.options.overmorrow.name"))) {
            title = LanguageManager.get().getTranslation("command.menu.options.overmorrow.description");
        } else if (subcommand.equals(LanguageManager.get().getTranslation("command.menu.options.monday.name"))) {
            title = LanguageManager.get().getTranslation("command.menu.options.monday.description");
        } else if (subcommand.equals(LanguageManager.get().getTranslation("command.menu.options.tuesday.name"))) {
            title = LanguageManager.get().getTranslation("command.menu.options.tuesday.description");
        } else if (subcommand.equals(LanguageManager.get().getTranslation("command.menu.options.wednesday.name"))) {
            title = LanguageManager.get().getTranslation("command.menu.options.wednesday.description");
        } else if (subcommand.equals(LanguageManager.get().getTranslation("command.menu.options.thursday.name"))) {
            title = LanguageManager.get().getTranslation("command.menu.options.thursday.description");
        } else if (subcommand.equals(LanguageManager.get().getTranslation("command.menu.options.friday.name"))) {
            title = LanguageManager.get().getTranslation("command.menu.options.friday.description");
        }

        return new MenuCommandData(title,
                Date.from(targetDate.atStartOfDay().atZone(TimeZone.getDefault().toZoneId()).toInstant()),
                new MenuTime(targetDate.getYear(), targetDate.getMonthValue(), targetDate.getDayOfMonth()));
    }
}
