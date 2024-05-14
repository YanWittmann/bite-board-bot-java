package menu.service;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

public class TimeUtils {

    public static ZonedDateTime getUtcNow() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    public static String formatDay(Date date) {
        return new SimpleDateFormat("E MMM dd yyyy").format(date);
    }

    public static String formatTime(ZonedDateTime zonedDateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone(zonedDateTime.getZone()));
        return sdf.format(Date.from(zonedDateTime.toInstant()));
    }

    public static String formatDay(ZonedDateTime zonedDateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("E MMM dd yyyy");
        sdf.setTimeZone(TimeZone.getTimeZone(zonedDateTime.getZone()));
        return sdf.format(Date.from(zonedDateTime.toInstant()));
    }
}
