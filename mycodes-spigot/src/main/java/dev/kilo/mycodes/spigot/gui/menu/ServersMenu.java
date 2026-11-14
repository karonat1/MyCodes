package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.backend.Backend;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Выбор сервера активации промокода (в сети) или «этот сервер» (одиночный режим).
 */
public class ServersMenu extends Menu {

    public interface SelectHandler {
        /** "any" = любой сервер, иначе имя сервера */
        void onSelect(String server);
    }

    private final SelectHandler handler;

    public ServersMenu(MyCodesSpigotPlugin plugin, Player player, SelectHandler handler) {
        super(plugin, player, 54, plugin.core().lang().msg("menu-title-servers"));
        this.handler = handler;
    }

    @Override
    protected void draw() {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        Backend backend = plugin.backend();
        List<String> servers = backend.servers();

        int slot = 10;
        // «любой сервер» / «этот сервер»
        String anyName = servers.isEmpty()
                ? plugin.core().lang().msg("server-current-name")
                : plugin.core().lang().msg("server-any-name");
        List<String> anyLore = servers.isEmpty()
                ? plugin.core().lang().msgList("server-current-lore")
                : plugin.core().lang().msgList("server-any-lore");
        set(slot, ItemBuilder.of(Material.EMERALD)
                .name(anyName)
                .lore(anyLore)
                .glow()
                .build(), new ClickAction() {
            @Override
            public void run() {
                handler.onSelect("any");
            }
        });

        if (servers.isEmpty()) {
            // одиночный режим — только один вариант
            return;
        }

        slot = 19;
        int placed = 0;
        for (int i = 0; i < servers.size() && placed < 24; i++) {
            final String serverName = servers.get(i);
            int column = placed % 7;
            int row = placed / 7;
            int targetSlot = 20 + column + row * 9;
            set(targetSlot, ItemBuilder.of(Material.GRASS_BLOCK)
                    .name(plugin.core().lang().msg("server-entry-name", "server", serverName))
                    .lore(plugin.core().lang().msgList("server-entry-lore"))
                    .build(), new ClickAction() {
                @Override
                public void run() {
                    handler.onSelect(serverName);
                }
            });
            placed++;
        }
    }
}
