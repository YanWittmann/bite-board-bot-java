package menu.providers;

import lombok.Data;

@Data
public class MenuTime {
    private final int year;
    private final int month;
    private final int day;

    public boolean matches(MenuTime time) {
        return year == time.year && month == time.month && day == time.day;
    }
}
