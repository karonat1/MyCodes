package dev.kilo.mycodes.velocity;

import com.velocitypowered.api.command.CommandSource;
import dev.kilo.mycodes.common.PlatformSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

/** Обёртка отправителя команд Velocity для общего сервиса команд. */
public class VelocitySender implements PlatformSender {

    private final CommandSource handle;

    public VelocitySender(CommandSource handle) {
        this.handle = handle;
    }

    @Override
    public UUID uuid() {
        return handle instanceof com.velocitypowered.api.proxy.Player
                ? ((com.velocitypowered.api.proxy.Player) handle).getUniqueId() : null;
    }

    @Override
    public String name() {
        return handle instanceof com.velocitypowered.api.proxy.Player
                ? ((com.velocitypowered.api.proxy.Player) handle).getUsername() : "CONSOLE";
    }

    @Override
    public boolean isPlayer() {
        return handle instanceof com.velocitypowered.api.proxy.Player;
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public void sendLegacy(String legacyText) {
        for (String line : legacyText.split("\n")) {
            handle.sendMessage(LegacyComponentSerializer.legacySection().deserialize(line));
        }
    }

    @Override
    public void sendLink(String legacyText, String url) {
        Component link = LegacyComponentSerializer.legacySection()
                .deserialize(legacyText)
                .clickEvent(ClickEvent.openUrl(url));
        handle.sendMessage(link);
    }
}
