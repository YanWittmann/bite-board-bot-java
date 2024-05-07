package menu.service;

import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Log4j2
public class SiteFetcher {
    private static final OkHttpClient client = new OkHttpClient();

    public static Document performGetAndParseHtml(String url) throws IOException {
        try {
            final Request request = new Request.Builder()
                    .url(url)
                    .build();

            log.info("[GET/Document] Fetching from URL: {}", request);

            final String html;
            try (Response response = client.newCall(request).execute()) {
                html = response.body().string();
            }
            return Jsoup.parse(html);
        } catch (IOException e) {
            throw new IOException("[GET/Document] Could not load or parse the URL: " + url, e);
        }
    }

    public static Document performPostAndParseHtml(String url, RequestBody requestBody, Headers headers) throws IOException {
        try {
            final Headers.Builder headersBuilder = new Headers.Builder()
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0");

            if (headers != null) {
                headersBuilder.addAll(headers);
            }

            final Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .headers(headersBuilder.build())
                    .build();

            log.info("[POST/Document] Fetching from URL: {}", request);

            final String html;
            try (Response response = client.newCall(request).execute()) {
                html = response.body().string();
            }
            return Jsoup.parse(html);
        } catch (IOException e) {
            throw new IOException("[POST/Document] Could not load or parse the URL: " + url, e);
        }
    }

    public static JSONObject fetchJSONObject(String url) throws IOException {
        try {
            final Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String jsonString = response.body().string();
                return new JSONObject(jsonString);
            }
        } catch (IOException e) {
            throw new IOException("[GET/JSONObject] Could not fetch JSON from URL: " + url, e);
        }
    }

    public static JSONArray fetchJSONArray(String url) throws IOException {
        try {
            final Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String jsonString = response.body().string();
                return new JSONArray(jsonString);
            }
        } catch (IOException e) {
            throw new IOException("[GET/JSONArray] Could not fetch JSON from URL: " + url, e);
        }
    }

    public static BufferedImage fetchImageByUrl(String url) throws IOException, InterruptedException {
        try {
            final Request request = new Request.Builder()
                    .url(url)
                    .build();

            final byte[] imageBytes;
            try (Response response = client.newCall(request).execute()) {
                imageBytes = response.body().bytes();
            }
            return bytesToImage(imageBytes);
        } catch (IOException e) {
            throw new IOException("[GET/BufferedImage] Could not load the image from URL: " + url, e);
        }
    }

    private static BufferedImage bytesToImage(byte[] imageBytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            throw new RuntimeException("Could not convert image bytes to BufferedImage", e);
        }
    }
}
