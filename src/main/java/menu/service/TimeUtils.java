package menu.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimeUtils {

    public static Date getUtcNow() {
        final Date now = new Date();
        now.setTime(now.getTime() + TimeZone.getDefault().getOffset(now.getTime()));
        return now;
    }

    public static String formatDay(Date date) {
        return new SimpleDateFormat("E MMM dd yyyy").format(date);
    }

    public static String formatTime(Date date) {
        return new SimpleDateFormat("HH:mm:ss").format(date);
    }

    public static Date createUtcTime(int hours, int minutes, int seconds) {
        final Date now = TimeUtils.getUtcNow();
        now.setHours(hours);
        now.setMinutes(minutes);
        now.setSeconds(seconds);
        return now;
    }
}
