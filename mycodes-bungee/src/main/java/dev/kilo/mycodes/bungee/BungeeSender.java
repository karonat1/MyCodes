package dev.kilo.mycodes.bungee;

import dev.kilo.mycodes.common.PlatformSender;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.UUID;

/** Обёртка отправителя команд BungeeCord для общего сервиса команд. */
public class BungeeSender implements PlatformSender {

    private final CommandSender handle;

    public BungeeSender(CommandSender handle) {
        this.handle = handle;
    }

    @Override
    public UUID uuid() {
        return handle instanceof ProxiedPlayer ? ((ProxiedPlayer) handle).getUniqueId() : null;
    }

    @Override
    public String name() {
        return handle.getName();
    }

    @Override
    public boolean isPlayer() {
        return handle instanceof ProxiedPlayer;
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public void sendLegacy(String legacyText) {
        for (String line : legacyText.split("\n")) {
            handle.sendMessage(TextComponent.fromLegacyText(line));
        }
    }

    @Override
    public void sendLink(String legacyText, String url) {
        TextComponent link = new TextComponent(TextComponent.fromLegacyText(legacyText));
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        handle.sendMessage(link);
    }
}
