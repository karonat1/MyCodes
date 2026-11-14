package dev.kilo.mycodes.spigot.command;

import dev.kilo.mycodes.common.command.CommandService;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.SpigotSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Исполнитель /mycodes (/mcd), /code, /promo (/promocode).
 */
public class SpigotCommandHandler implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "menu", "list", "reload", "code", "create");

    private final MyCodesSpigotPlugin plugin;
    private final CommandService service;

    public SpigotCommandHandler(MyCodesSpigotPlugin plugin, CommandService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("mycodes")) {
            service.executeMain(new SpigotSender(sender), label, args);
        } else if (name.equals("code") || name.equals("promo")) {
            service.executeRedeem(new SpigotSender(sender), args.length > 0 ? args[0] : "");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        String name = command.getName().toLowerCase();
        if (name.equals("mycodes")) {
            if (args.length == 1) {
                for (String sub : SUBCOMMANDS) {
                    if (sub.startsWith(args[0].toLowerCase())) {
                        out.add(sub);
                    }
                }
            }
        } else if (args.length == 1 && (name.equals("code") || name.equals("promo"))) {
            // подсказка кодов игрокам НЕ даём (иначе раскроем промокоды)
        }
        return out;
    }
}
