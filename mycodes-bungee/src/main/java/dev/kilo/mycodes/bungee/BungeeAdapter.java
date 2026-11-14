package dev.kilo.mycodes.bungee;

import dev.kilo.mycodes.common.PlatformAdapter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Адаптер ядра для BungeeCord/Waterfall. */
public class BungeeAdapter implements PlatformAdapter {

    private final MyCodesBungeePlugin plugin;

    public BungeeAdapter(MyCodesBungeePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String platformName() {
        return "BungeeCord (network mode)";
    }

    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public Path dataFolder() {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        }
        return folder.toPath();
    }

    @Override
    public void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void logWarn(String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void logError(String message, Throwable t) {
        if (t != null) {
            plugin.getLogger().severe(message + ": " + t);
        } else {
            plugin.getLogger().severe(message);
        }
    }

    @Override
    public Executor async() {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                ProxyServer.getInstance().getScheduler().runAsync(plugin, command);
            }
        };
    }

    @Override
    public List<String> serverNames() {
        return new ArrayList<String>(ProxyServer.getInstance().getServers().keySet());
    }

    @Override
    public void broadcastLegacy(String legacyText) {
        for (String line : legacyText.split("\n")) {
            net.md_5.bungee.api.chat.BaseComponent[] components =
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(line);
            for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
                player.sendMessage(components);
            }
            ProxyServer.getInstance().getConsole().sendMessage(components);
        }
    }
}
