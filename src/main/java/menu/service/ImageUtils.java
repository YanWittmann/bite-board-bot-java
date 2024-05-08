package menu.service;

import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
public class ImageUtils {

    public static BufferedImage createCombinedImageFromQueries(ImageSearcher.ImageSearch imageSearch, List<String> imageQueries, int desiredHeight) {
        List<String> imageUrls = imageQueries.stream()
                .map(query -> {
                    try {
                        final List<String> urls = imageSearch.searchImages(query);
                        if (urls == null || urls.isEmpty()) {
                            log.warn("No images found for query: {}", query);
                            return null;
                        }
                        return urls.get(0);
                    } catch (IOException | InterruptedException e) {
                        log.error("Failed to search images for query: {}", query, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(imageUrl -> imageUrl.startsWith("https://") || imageUrl.startsWith("http://"))
                .collect(Collectors.toList());

        if (imageUrls.isEmpty()) {
            log.warn("No images found for queries: {}", imageQueries);
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        List<BufferedImage> loadedImages = imageUrls.stream()
                .map(url -> {
                    try {
                        return SiteFetcher.fetchImageByUrl(url);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException("Failed to load image from url: " + url, e);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        double totalWidth = loadedImages.stream()
                .mapToDouble(image -> (double) image.getWidth() / image.getHeight())
                .sum() * desiredHeight;

        BufferedImage combinedImage = new BufferedImage((int) totalWidth, desiredHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = combinedImage.createGraphics();

        int currentX = 0;
        for (BufferedImage img : loadedImages) {
            int width = (int) (((double) img.getWidth() / img.getHeight()) * desiredHeight);
            g2d.drawImage(img, currentX, 0, width, desiredHeight, null);
            currentX += width;
        }

        g2d.dispose();
        return combinedImage;
    }
}