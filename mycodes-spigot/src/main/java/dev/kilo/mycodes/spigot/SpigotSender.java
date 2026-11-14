package dev.kilo.mycodes.spigot;

import dev.kilo.mycodes.common.PlatformSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Обёртка отправителя команд Spigot для общего сервиса команд. */
public class SpigotSender implements PlatformSender {

    private final CommandSender handle;

    public SpigotSender(CommandSender handle) {
        this.handle = handle;
    }

    public CommandSender handle() {
        return handle;
    }

    @Override
    public UUID uuid() {
        return handle instanceof Player ? ((Player) handle).getUniqueId() : null;
    }

    @Override
    public String name() {
        return handle.getName();
    }

    @Override
    public boolean isPlayer() {
        return handle instanceof Player;
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public void sendLegacy(String legacyText) {
        for (String line : legacyText.split("\n")) {
            handle.sendMessage(line);
        }
    }

    @Override
    public void sendLink(String legacyText, String url) {
        if (handle instanceof Player) {
            Player player = (Player) handle;
            player.spigot().sendMessage(new ComponentBuilder(legacyText)
                    .event(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .create());
        } else {
            handle.sendMessage(legacyText + " " + url);
        }
    }
}
