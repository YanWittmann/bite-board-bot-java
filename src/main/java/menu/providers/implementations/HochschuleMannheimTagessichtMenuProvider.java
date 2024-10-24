package menu.providers.implementations;

import lombok.extern.log4j.Log4j2;
import menu.providers.*;
import menu.service.SiteFetcher;
import okhttp3.FormBody;
import okhttp3.Headers;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Log4j2
public class HochschuleMannheimTagessichtMenuProvider extends MenuItemsProvider {

    @Override
    public String getName() {
        return "Hochschule Mannheim";
    }

    @Override
    public String getDisplayMenuLink() {
        return "https://www.stw-ma.de/essen-trinken/speiseplaene/mensa-an-der-hs/";
    }

    @Override
    public String getProviderThumbnail() {
        return "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQPpP_niFgiON6iSyRENQKGY2VdVsccUg2nI45u2N1L2Q&s";
    }

    @Override
    public CompletableFuture<List<MenuItem>> getMenuItemsForDate(MenuTime date) {
        log.info("Loading menu for date: {}", date);
        return CompletableFuture.supplyAsync(() -> {
            try {
                // location=611&lang=de&date=2024-09-25&mode=day
                final FormBody formBody = new FormBody.Builder()
                        .add("date", date.getYear() + "-" + String.format("%02d", date.getMonth()) + "-" + String.format("%02d", date.getDay()))
                        .add("location", "611")
                        .add("lang", "de")
                        .add("mode", "day")
                        .build();

                final Headers headers = new Headers.Builder()
                        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .add("Accept-Language", "de")
                        .add("Content-Type", "application/x-www-form-urlencoded")
                        .add("Upgrade-Insecure-Requests", "1")
                        .build();

                final Document document = SiteFetcher.performPostAndParseHtml("https://api.stw-ma.de/tl1/menuplan", formBody, headers, response -> new JSONObject(response).getString("content"));
                final List<MenuItem> items = parseMenuItems(document, date);
                return items.stream()
                        .filter(item -> date.matches(item.getMenuTime()))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                log.error("Could not load or parse the menu for the given date: {}", date, e);
                throw new RuntimeException("Could not load or parse the menu for the given date: " + date, e);
            }
        });
    }

    @Override
    public List<String> getMenuEmojis(List<MenuItem> menuItems) {
        return Arrays.asList("\u0031\u20E3", "\u0032\u20E3", "\u0033\u20E3");
    }

    private List<MenuItem> parseMenuItems(Document document, MenuTime queryDate) {
        final List<MenuItemFeature> menuItemFeatures = parseMenuItemFeatures(document);
        final List<MenuItem> menuItems = new ArrayList<>();

        final Elements rows = document.select(".speiseplan-table tr");

        for (Element row : rows) {
            try {
                final MenuItem menuItem = new MenuItem(queryDate);

                final String menuName = row.selectFirst(".speiseplan-table-menu-headline strong").text().trim();
                final String menuDescription = row.selectFirst(".speiseplan-table-menu-content").text().trim();
                final String menuPrice = row.selectFirst(".speiseplan-table-col-last .price").text().trim();
                final String menuUnit = row.selectFirst(".speiseplan-table-col-last .customSelection").text().trim();

                menuItem.setName(menuName);
                menuItem.setPrice(menuPrice);
                menuItem.setUnit(menuUnit);

                if (Arrays.asList("Salatbuffet", "Dessert").contains(menuItem.getName())) {
                    menuItem.setShouldFetchImages(false);
                }

                final String[] components = menuDescription.split(", ");
                for (String component : components) {
                    final String name = component.replaceAll("\\([^)]*\\)", "").trim();
                    final String[] features;
                    if (component.matches(".+\\(([^)]*)\\)")) {
                        features = component.split("\\(")[1].split("\\)")[0].split(",");
                    } else {
                        features = new String[0];
                        log.warn("Menu item [{}] does not specify features: {}", menuItem.getName(), component);
                    }

                    final MenuItemIngredient ingredient = new MenuItemIngredient(name);
                    menuItem.getIngredients().add(ingredient);

                    for (String feature : features) {
                        MenuItemFeature featureObj = menuItemFeatures.stream()
                                .filter(f -> f.getShortId().equals(feature.trim()))
                                .findFirst()
                                .orElse(null);
                        if (featureObj != null) {
                            ingredient.getFeatures().add(featureObj);
                        } else {
                            log.warn("Feature not found: {}", feature);
                        }
                    }
                }

                menuItems.add(menuItem);
            } catch (Exception e) {
                log.error("Could not parse menu item: {}", row.outerHtml(), e);
            }
        }

        return menuItems;
    }

    private List<MenuItemFeature> parseMenuItemFeatures(Document document) {
        final List<MenuItemFeature> features = new ArrayList<>();
        final Elements featureContainerElements = document.select(".speiseplan-label-content");

        if (featureContainerElements.isEmpty()) {
            log.warn("No feature container elements found");
            return features;
        }

        final Element featureContainerElement = featureContainerElements.stream()
                .max(Comparator.comparingInt(Element::childNodeSize))
                .orElse(null);

        String currentType = "";
        for (Node child : featureContainerElement.childNodes()) {
            if (child instanceof Element) {
                final Element element = (Element) child;
                if (element.hasClass("speiseplan-category")) {
                    currentType = element.text().trim().replace(":", "");
                } else if (element.hasClass("speiseplan-label")) {
                    final String id = element.selectFirst("sup b").text().trim();
                    String name = element.childNode(2).toString().trim();
                    if (name.isEmpty()) {
                        name = element.childNode(4).toString().trim();
                    }
                    if (!id.isEmpty() && !name.isEmpty()) {
                        features.add(new MenuItemFeature(id, name, currentType));
                    } else {
                        log.warn("Could not parse feature " + id + " " + name + " " + currentType + " from " + element.outerHtml());
                    }
                }
            }
        }

        if (features.isEmpty()) {
            log.warn("No features found");
        } else {
            log.info("Found {} features", features.size());
        }

        return features;
    }
}