package menu.bot;

import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import menu.providers.MenuItemsProvider;
import menu.providers.MenuItemsProviderManager;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
@Getter
public class BotData {
    private final File file;

    private final List<BotUserData> users = new ArrayList<>();
    private final List<BotSendPeriodicMenuInfo> sendDailyMenuInfo = new ArrayList<>();

    private final List<Consumer<List<BotSendPeriodicMenuInfo>>> periodicMenuChangeListener = new ArrayList<>();

    public BotData(File file) {
        if (file == null) {
            throw new IllegalArgumentException("No path provided for BotData, set the [dataStoragePath] config value to a valid path");
        }
        this.file = file;
        this.readData(file);
        this.writeData();
    }

    private void readData(File file) {
        if (!file.exists()) {
            return;
        }

        try {
            final String json = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            final JSONObject data = new JSONObject(json);
            data.getJSONArray("users").forEach(user -> users.add(BotUserData.fromJson((JSONObject) user)));
            data.getJSONArray("sendDailyMenuInfo").forEach(info -> sendDailyMenuInfo.add(BotSendPeriodicMenuInfo.fromJson((JSONObject) info)));
        } catch (IOException e) {
            log.error("Error reading bot data: {}", e.getMessage());
        }
    }

    private void writeData() {
        final JSONObject data = new JSONObject();
        data.put("users", users.stream().map(BotUserData::toJson).collect(Collectors.toList()));
        data.put("sendDailyMenuInfo", sendDailyMenuInfo.stream().map(BotSendPeriodicMenuInfo::toJson).collect(Collectors.toList()));

        try {
            FileUtils.writeStringToFile(file, data.toString(4), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error writing bot data: {}", e.getMessage());
        }
    }

    public MenuItemsProvider findUserPreferredMenuProvider(String userId, MenuItemsProviderManager menuProviders) {
        BotUserData userData = findOrCreateUser(userId);
        String preferredMenuProvider = userData.getPreferredMenuProvider();
        if (preferredMenuProvider != null && menuProviders.contains(preferredMenuProvider)) {
            return menuProviders.get(preferredMenuProvider);
        }
        if (!menuProviders.isEmpty()) {
            final MenuItemsProvider alternateProvider = menuProviders.values().iterator().next();
            log.error("The preferred menu provider for user {} is not available: {}, falling back to {}", userId, preferredMenuProvider, alternateProvider.getName());
            return alternateProvider;
        } else {
            log.error("No menu providers available to fall back to, please register at least one provider.");
            return null;
        }
    }

    public void setUserPreferredMenuProvider(String userId, String provider) {
        BotUserData userData = findOrCreateUser(userId);
        userData.setPreferredMenuProvider(provider);
        writeData();
    }

    public boolean isUserRole(String userId, String role) {
        BotUserData userData = findOrCreateUser(userId);
        return userData.getRoles().contains(role);
    }

    private BotUserData findOrCreateUser(String userId) {
        return users.stream()
                .filter(user -> user.getUserId().equals(userId))
                .findFirst()
                .orElseGet(() -> {
                    final BotUserData newUser = new BotUserData(userId);
                    users.add(newUser);
                    return newUser;
                });
    }

    public List<BotSendPeriodicMenuInfo> getAllPeriodicMenuChannels() {
        return new ArrayList<>(sendDailyMenuInfo);
    }

    public void setPeriodicMenuChannel(String channelId, String time, String provider, int addTime) {
        this.sendDailyMenuInfo.removeIf(info -> info.getChannelId().equals(channelId));

        final BotSendPeriodicMenuInfo menuInfo = new BotSendPeriodicMenuInfo();
        menuInfo.setChannelId(channelId);
        menuInfo.setTime(time);
        menuInfo.setProvider(provider);
        menuInfo.setAddTime(addTime);
        sendDailyMenuInfo.removeIf(info -> info.getProvider().equals(channelId));
        sendDailyMenuInfo.add(menuInfo);

        writeData();

        for (Consumer<List<BotSendPeriodicMenuInfo>> listener : periodicMenuChangeListener) {
            listener.accept(sendDailyMenuInfo);
        }
    }

    public void addPeriodicMenuChangeListener(Consumer<List<BotSendPeriodicMenuInfo>> updateCallback) {
        periodicMenuChangeListener.add(updateCallback);
    }

    @Data
    public static class BotUserData {
        private final String userId;
        private final List<String> roles = new ArrayList<>();
        private String preferredMenuProvider = null;

        public JSONObject toJson() {
            final JSONObject json = new JSONObject();
            json.put("userId", userId);
            json.put("roles", roles);
            json.put("preferredMenuProvider", preferredMenuProvider);
            return json;
        }

        public static BotUserData fromJson(JSONObject json) {
            final BotUserData data = new BotUserData(json.getString("userId"));
            final JSONArray rolesArray = json.optJSONArray("roles");
            if (rolesArray != null) {
                rolesArray.toList().stream().map(Object::toString).forEach(data.roles::add);
            }
            data.preferredMenuProvider = json.optString("preferredMenuProvider", null);
            return data;
        }
    }

    @Data
    public static class BotSendPeriodicMenuInfo {
        private String channelId;
        private String time;
        private String provider;
        private int addTime;

        public JSONObject toJson() {
            final JSONObject json = new JSONObject();
            json.put("channelId", channelId);
            json.put("time", time);
            json.put("provider", provider);
            json.put("addTime", addTime);
            return json;
        }

        public static BotSendPeriodicMenuInfo fromJson(JSONObject json) {
            final BotSendPeriodicMenuInfo data = new BotSendPeriodicMenuInfo();
            data.channelId = json.getString("channelId");
            data.time = json.getString("time");
            data.provider = json.getString("provider");
            data.addTime = json.getInt("addTime");
            return data;
        }
    }
}
