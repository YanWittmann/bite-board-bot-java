package menu.service;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Log4j2
public class LanguageManager {

    private static LanguageManager instance;

    public static LanguageManager get() {
        if (instance == null) {
            instance = new LanguageManager();
            try {
                instance.setupLanguages();
            } catch (IOException e) {
                throw new RuntimeException("Error setting up languages: " + e.getMessage());
            }
        }
        return instance;
    }

    private static final String LANG_DIR = "/bot/lang/";
    private final Map<String, JSONObject> languages = new HashMap<>();
    private String currentLanguage = "en";

    public void setupLanguages() throws IOException {
        final String[] files = getResourceListing(getClass(), LANG_DIR);

        if (files.length == 0) {
            throw new RuntimeException("No language files found in [" + LANG_DIR + "]");
        }

        for (String file : files) {
            if (file.endsWith(".json")) {
                String lang = file.replace(".json", "");
                JSONObject langData = parseJsonFile(LANG_DIR + file);
                languages.put(lang, langData);
            }
        }
    }

    public void setLang(String lang) {
        if (!languages.containsKey(lang)) {
            throw new IllegalArgumentException("Language [" + lang + "] not supported, available languages: [" + String.join(", ", languages.keySet()) + "]");
        }
        currentLanguage = lang;
    }

    public String getLang() {
        return currentLanguage;
    }

    public Set<String> getAvailableLanguages() {
        return languages.keySet();
    }

    public Map<String, DiscordLocale> getAvailableDiscordLocales() {
        final Map<String, DiscordLocale> locales = new HashMap<>();
        for (String lang : languages.keySet()) {
            for (DiscordLocale locale : DiscordLocale.values()) {
                if (locale.toString().startsWith(lang)) {
                    locales.put(lang, locale);
                }
            }
        }
        return locales;
    }

    public String getTranslation(String key) {
        return getTranslation(key, currentLanguage);
    }

    public String getTranslation(String key, String lang) {
        String[] keys = key.split("\\.");
        Object translation = languages.get(lang);

        for (String k : keys) {
            if (translation instanceof JSONObject) {
                translation = ((JSONObject) translation).opt(k);
            } else {
                return key;
            }
        }

        if (translation != null) {
            return translation.toString();
        }

        log.warn("Translation not found for key [{}] in lang [{}]", key, lang);
        return key;
    }

    public String fillTranslation(String key, String... args) {
        String translation = getTranslation(key);

        for (int i = 0; i < args.length; i++) {
            translation = translation.replace("{" + i + "}", args[i]);
        }

        return translation;
    }

    private JSONObject parseJsonFile(String resourceName) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourceName);
             InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(is), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            return new JSONObject(sb.toString());
        }
    }

    private String[] getResourceListing(Class<?> clazz, String path) throws IOException {
        try (InputStream in = clazz.getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines.toArray(new String[0]);
        }
    }
}