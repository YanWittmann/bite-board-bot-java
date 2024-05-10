package menu.providers;

import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Log4j2
public abstract class MenuItemsProvider {

    public abstract String getName();

    public abstract String getDisplayMenuLink();

    public abstract String getProviderThumbnail();

    public abstract CompletableFuture<List<MenuItem>> getMenuItemsForDate(MenuTime date);

    /**
     * Will post these emojis under the menu items.<br>
     * For example, use the following for the numbers 1-10:<br>
     * "\u0031\u20E3", "\u0032\u20E3", "\u0033\u20E3", "\u0034\u20E3", "\u0035\u20E3", "\u0036\u20E3", "\u0037\u20E3", "\u0038\u20E3", "\u0039\u20E3"
     *
     * @param menuItems List of menu items
     * @return List of reaction emojis to be used for the menu items
     */
    public abstract List<String> getMenuEmojis(List<MenuItem> menuItems);

    public String toMdString() {
        return "[" + this.getName() + "](<" + this.getDisplayMenuLink() + ">)";
    }
}
