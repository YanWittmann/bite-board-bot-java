package menu.providers;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MenuItemFeature {
    private String shortId;
    private String name;
    private String type;
}
