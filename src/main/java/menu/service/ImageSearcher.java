package menu.service;

import lombok.extern.log4j.Log4j2;
import menu.bot.BiteBoardProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@Log4j2
public class ImageSearcher {
    public enum ImageDisplayMode {
        SEPARATE,
        COMBINED
    }

    public abstract static class ImageSearch {
        public abstract List<String> searchImages(String query) throws IOException, InterruptedException;

        public abstract ImageDisplayMode preferredImageDisplayMode();
    }

    public static class GoogleImagePageSearch extends ImageSearch {
        @Override
        public List<String> searchImages(String query) throws IOException {
            final String encodedQuery = URLEncoder.encode(query, "UTF-8");
            final String url = "https://www.google.com/search?tbm=isch&q=" + encodedQuery;

            try {
                final Document html = SiteFetcher.performGetAndParseHtml(url);

                return html.getElementsByTag("img").stream()
                        .map(element -> element.attr("src"))
                        .filter(src -> src.startsWith("https://") || src.startsWith("http://"))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            } catch (IOException e) {
                log.error("Error fetching images from Google: {}", e.getMessage());
                return new ArrayList<>();
            }
        }

        @Override
        public ImageDisplayMode preferredImageDisplayMode() {
            return ImageDisplayMode.COMBINED;
        }
    }

    public static class GoogleImageApiSearch extends ImageSearch {
        private final String apiKey;
        private final String applicationId;

        public GoogleImageApiSearch(String apiKey, String applicationId) {
            this.apiKey = apiKey;
            this.applicationId = applicationId;
        }

        @Override
        public List<String> searchImages(String query) throws IOException, InterruptedException {
            final String url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey +
                    "&cx=" + applicationId +
                    "&q=" + URLEncoder.encode(query, "UTF-8") +
                    "&searchType=image&imgType=photo";

            final JSONObject jsonResponse = SiteFetcher.fetchJSONObject(url);

            final List<String> imageUrls = new ArrayList<>();
            final JSONArray items = jsonResponse.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String imageUrl = item.getString("link");
                imageUrls.add(imageUrl);
            }

            return imageUrls;
        }

        @Override
        public ImageDisplayMode preferredImageDisplayMode() {
            return ImageDisplayMode.COMBINED;
        }
    }

    public static class DummyImageSearch extends ImageSearch {
        @Override
        public List<String> searchImages(String query) {
            return Collections.emptyList();
        }

        @Override
        public ImageDisplayMode preferredImageDisplayMode() {
            return ImageDisplayMode.SEPARATE;
        }
    }

    public static ImageSearch createImageSearch(Properties properties) {
        final String menuImagePreviewService = properties.getProperty(BiteBoardProperties.MENSA_MENU_IMAGE_PREVIEW_SERVICE);

        if (menuImagePreviewService == null) {
            throw new IllegalArgumentException("No image search service specified, specify property [" + BiteBoardProperties.MENSA_MENU_IMAGE_PREVIEW_SERVICE + "] to use either: googlePage, googleApi, none");
        }

        if (menuImagePreviewService.equals("googlePage")) {
            return new GoogleImagePageSearch();
        } else if (menuImagePreviewService.equals("googleApi")) {
            if (properties.containsKey(BiteBoardProperties.GOOGLE_IMAGE_API_KEY) && properties.containsKey(BiteBoardProperties.GOOGLE_IMAGE_API_APPLICATION_ID)) {
                return new GoogleImageApiSearch(
                        properties.getProperty(BiteBoardProperties.GOOGLE_IMAGE_API_KEY),
                        properties.getProperty(BiteBoardProperties.GOOGLE_IMAGE_API_APPLICATION_ID)
                );
            } else {
                log.error("No valid Google API image search configuration found, using dummy image search. Required fields: googleImageApiKey, googleImageApiApplicationId");
            }
        } else if (!menuImagePreviewService.equals("none")) {
            throw new IllegalArgumentException("Invalid image search service specified: " + menuImagePreviewService);
        }

        return new DummyImageSearch();
    }
}
