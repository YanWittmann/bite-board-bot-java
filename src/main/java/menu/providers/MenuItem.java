package menu.providers;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuItem {
    private String name;
    private MenuTime menuTime;
    private List<MenuItemIngredient> ingredients = new ArrayList<>();
    private String ingredientsString;
    private String price;
    private String unit;
    private boolean shouldFetchImages = true;

    public MenuItem(MenuTime menuTime) {
        this.menuTime = menuTime;
    }

    public void addIngredient(MenuItemIngredient ingredient) {
        ingredients.add(ingredient);
    }
}
