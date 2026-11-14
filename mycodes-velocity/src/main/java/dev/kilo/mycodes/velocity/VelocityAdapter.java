package dev.kilo.mycodes.velocity;

import dev.kilo.mycodes.common.PlatformAdapter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Адаптер ядра для Velocity. */
public class VelocityAdapter implements PlatformAdapter {

    private final MyCodesVelocityPlugin plugin;
    private final java.util.concurrent.ExecutorService executor =
            Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "MyCodes-Async");
                    t.setDaemon(true);
                    return t;
                }
            });

    public VelocityAdapter(MyCodesVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String platformName() {
        return "Velocity (network mode)";
    }

    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public Path dataFolder() {
        return plugin.dataDirectory();
    }

    @Override
    public void logInfo(String message) {
        plugin.proxy().getConsoleCommandSource().sendMessage(LegacyComponentSerializer.legacySection()
                .deserialize("§b[MyCodes] §f" + message.replace("\n", " ")));
    }

    @Override
    public void logWarn(String message) {
        plugin.proxy().getConsoleCommandSource().sendMessage(LegacyComponentSerializer.legacySection()
                .deserialize("§e[MyCodes] " + message.replace("\n", " ")));
    }

    @Override
    public void logError(String message, Throwable t) {
        String text = "§c[MyCodes] " + message + (t == null ? "" : " (" + t + ")");
        plugin.proxy().getConsoleCommandSource().sendMessage(LegacyComponentSerializer.legacySection()
                .deserialize(text.replace("\n", " ")));
    }

    @Override
    public Executor async() {
        return executor;
    }

    @Override
    public List<String> serverNames() {
        List<String> out = new ArrayList<String>();
        for (com.velocitypowered.api.proxy.server.RegisteredServer server
                : plugin.proxy().getAllServers()) {
            out.add(server.getServerInfo().getName());
        }
        return out;
    }

    @Override
    public void broadcastLegacy(String legacyText) {
        for (String line : legacyText.split("\n")) {
            net.kyori.adventure.text.Component component =
                    LegacyComponentSerializer.legacySection().deserialize(line);
            for (com.velocitypowered.api.proxy.Player player : plugin.proxy().getAllPlayers()) {
                player.sendMessage(component);
            }
            plugin.proxy().getConsoleCommandSource().sendMessage(component);
        }
    }
}
