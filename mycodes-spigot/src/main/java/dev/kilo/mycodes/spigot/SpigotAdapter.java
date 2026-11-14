package dev.kilo.mycodes.spigot;

import dev.kilo.mycodes.common.PlatformAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Адаптер ядра для одиночного Spigot/Paper сервера. */
public class SpigotAdapter implements PlatformAdapter {

    private final MyCodesSpigotPlugin plugin;

    public SpigotAdapter(MyCodesSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String platformName() {
        return "Spigot/Paper (standalone)";
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public Path dataFolder() {
        return plugin.getDataFolder().toPath();
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
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, command);
                }
            }
        };
    }

    @Override
    public List<String> serverNames() {
        return new ArrayList<String>();
    }

    @Override
    public void broadcastLegacy(String legacyText) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(legacyText.split("\n"));
        }
        Bukkit.getConsoleSender().sendMessage(legacyText.split("\n"));
    }
}
