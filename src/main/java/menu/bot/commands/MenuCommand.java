package menu.bot.commands;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import menu.bot.BotData;
import menu.providers.MenuItem;
import menu.providers.*;
import menu.service.ImageSearcher;
import menu.service.ImageUtils;
import menu.service.LanguageManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
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
                        createFetchMenuForDaySubcommand("overmorrow"),
                        createFetchMenuForDaySubcommand("monday"),
                        createFetchMenuForDaySubcommand("tuesday"),
                        createFetchMenuForDaySubcommand("wednesday"),
                        createFetchMenuForDaySubcommand("thursday"),
                        createFetchMenuForDaySubcommand("friday")
                );
    }

    private SubcommandData createFetchMenuForDaySubcommand(String nameKey) {
        final SubcommandData subcommand = new SubcommandData(
                nameKey,
                LanguageManager.get().getTranslation("command.menu.options." + nameKey + ".description")
        );
        for (Map.Entry<String, DiscordLocale> langToLocale : LanguageManager.get().getAvailableDiscordLocales().entrySet()) {
            subcommand.setNameLocalization(langToLocale.getValue(), LanguageManager.get().getTranslation("command.menu.options." + nameKey + ".name", langToLocale.getKey()));
            subcommand.setDescriptionLocalization(langToLocale.getValue(), LanguageManager.get().getTranslation("command.menu.options." + nameKey + ".description", langToLocale.getKey()));
        }
        return subcommand;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("menu")) return;

        event.deferReply().queue();

        final String user = event.getUser().getName();
        final String subcommandName = event.getSubcommandName();
        final MenuItemsProvider menuProvider = botData.findUserPreferredMenuProvider(user, menuProviders);

        if (menuProvider == null) {
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.menu.response.noMenuProvider")).queue();
            return;
        }

        log.info("Fetching menu for user [{}] using provider [{}] with subcommand [{}]", user, menuProvider.getName(), subcommandName);

        final MenuCommandData menuCommandData = parseCommand(event);
        final ConstructedMenuEmbed menuEmbedResult;
        try {
            menuEmbedResult = constructMenuEmbed(menuProvider, menuCommandData);
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error fetching menu for user [{}] using provider [{}] with subcommand [{}]", user, menuProvider.getName(), subcommandName, e);
            event.getHook().sendMessage(LanguageManager.get().getTranslation("command.menu.response.error")).queue();
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
                .setTitle(menuCommandData.getEmbedTitle() + " - " + menuCommandData.getTargetDate().toString())
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

    public List<ConstructedMenuImageEmbed> constructImageEmbed(ImageSearcher.ImageSearch imageSearch, List<MenuItem> menuItems) {
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
                        .setTitle("Preview")
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

    public static MenuCommandData parseCommand(SlashCommandInteractionEvent interaction) {
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
