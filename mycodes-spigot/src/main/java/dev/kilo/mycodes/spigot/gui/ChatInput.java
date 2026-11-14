package dev.kilo.mycodes.spigot.gui;

import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chat input for the menus (promo codes, commands, values).
 * While input is active: any open inventory is closed and a bright hint is
 * shown (screen title + action bar). Typing the cancel word (see lang)
 * aborts the input.
 */
public class ChatInput implements Listener {

    public interface InputCallback {
        void accept(String input);
    }

    private static class Session {
        final Consumer<String> callback;
        final BukkitTask timeoutTask;

        Session(Consumer<String> callback, BukkitTask timeoutTask) {
            this.callback = callback;
            this.timeoutTask = timeoutTask;
        }
    }

    private final MyCodesSpigotPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();

    public ChatInput(MyCodesSpigotPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean isActive(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** Подсказка в action bar, пока ввод активен */
    private void showHints(Player player) {
        String bar = plugin.core().lang().msg("input-hint-actionbar",
                "cancel", plugin.core().lang().plain("cancel-word"));
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(bar));
        // повторяем полоску, пока ждём ввод (каждые 2 сек — actionbar гаснет)
        final UUID id = player.getUniqueId();
        hintTasks.put(id, Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !sessions.containsKey(id)) {
                    stopHint(id);
                    return;
                }
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(bar));
            }
        }, 50L, 40L));
    }

    private final Map<UUID, BukkitTask> hintTasks = new HashMap<UUID, BukkitTask>();

    private void stopHint(UUID id) {
        BukkitTask task = hintTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Запросить ввод: игроку показываются подсказки (title по центру экрана +
     * полоса action bar), а открытые меню/инвентари закрываются, чтобы
     * сфокусироваться на печати.
     */
    public void request(Player player, Consumer<String> callback) {
        final UUID id = player.getUniqueId();
        cancel(player, false);
        // любое открытое меню закрывается
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory() != null
                && !(player.getOpenInventory().getTopInventory().equals(player.getInventory()))
                && player.getOpenInventory().getTopInventory().getSize() > 0) {
            player.closeInventory();
        }
        // заголовок по центру экрана
        String title = plugin.core().lang().msg("input-hint-title");
        String subtitle = plugin.core().lang().msg("input-hint-subtitle",
                "cancel", plugin.core().lang().plain("cancel-word"));
        player.sendTitle(title, subtitle, 10, 40, 10);
        showHints(player);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                Session session = sessions.remove(id);
                stopHint(id);
                if (session != null) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        p.sendMessage(plugin.core().lang().msg("input-timeout"));
                    }
                }
            }
        }, 20L * 90);
        sessions.put(id, new Session(callback, timeout));
    }

    /** Молча отменить прошлую сессию игрока */
    public void cancel(Player player, boolean notify) {
        Session session = sessions.remove(player.getUniqueId());
        stopHint(player.getUniqueId());
        if (session != null) {
            session.timeoutTask.cancel();
            if (notify) {
                player.sendMessage(plugin.core().lang().msg("input-cancelled"));
            }
        }
    }

    public void cancelAll(Player player) {
        cancel(player, false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                handle(player, message);
            }
        });
    }

    private void handle(Player player, String message) {
        Session session = sessions.remove(player.getUniqueId());
        stopHint(player.getUniqueId());
        if (session == null) return;
        session.timeoutTask.cancel();
        String text = message.trim();
        String cancelWord = plugin.core().lang().plain("cancel-word");
        if (text.equalsIgnoreCase(cancelWord)) {
            player.sendMessage(plugin.core().lang().msg("input-cancelled"));
            return;
        }
        session.callback.accept(text);
    }
}
