package dev.kilo.mycodes.bungee.command;

import dev.kilo.mycodes.bungee.BungeeSender;
import dev.kilo.mycodes.common.command.CommandService;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

/** /promo (/promocode) на прокси. */
public class PromoCommand extends Command {

    private final CommandService service;

    public PromoCommand(CommandService service) {
        super("promo", null, "promocode");
        this.service = service;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        service.executeRedeem(new BungeeSender(sender), args.length > 0 ? args[0] : "");
    }
}
