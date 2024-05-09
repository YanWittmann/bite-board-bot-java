package menu.providers;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@Getter
public class MenuItemsProviderManager {
    private final Map<String, MenuItemsProvider> providers = new HashMap<>();

    public void register(MenuItemsProvider provider) {
        log.info("Registering provider: {} as {} ({})", provider.getName(), provider.getClass().getSimpleName(), provider.getDisplayMenuLink());
        providers.put(provider.getName(), provider);
    }

    public MenuItemsProvider get(String name) {
        return providers.get(name);
    }

    public boolean contains(String menuProvider) {
        return providers.containsKey(menuProvider);
    }

    public boolean isEmpty() {
        return providers.isEmpty();
    }

    public Collection<MenuItemsProvider> values() {
        return providers.values();
    }
}
