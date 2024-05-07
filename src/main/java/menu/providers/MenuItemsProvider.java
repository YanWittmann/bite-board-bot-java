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

    public String toMdString() {
        return "[" + this.getName() + "](<" + this.getDisplayMenuLink() + ">)";
    }
}
