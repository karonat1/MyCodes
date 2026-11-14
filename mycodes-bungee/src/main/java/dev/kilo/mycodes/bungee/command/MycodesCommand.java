package dev.kilo.mycodes.bungee.command;

import dev.kilo.mycodes.bungee.BungeeSender;
import dev.kilo.mycodes.common.command.CommandService;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

/** /mycodes (/mcd) на прокси. */
public class MycodesCommand extends Command {

    private final CommandService service;

    public MycodesCommand(CommandService service) {
        super("mycodes", null, "mcd");
        this.service = service;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        service.executeMain(new BungeeSender(sender), "mycodes", args);
    }
}
