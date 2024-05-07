package menu.providers;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuItemIngredient {
    private String name;
    private List<MenuItemFeature> features = new ArrayList<>();

    public MenuItemIngredient(String name) {
        this.name = name;
    }

    public void addFeature(MenuItemFeature feature) {
        features.add(feature);
    }
}
