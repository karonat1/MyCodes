package dev.kilo.mycodes.velocity.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import dev.kilo.mycodes.common.command.CommandService;
import dev.kilo.mycodes.velocity.MyCodesVelocityPlugin;
import dev.kilo.mycodes.velocity.VelocitySender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Команды /mycodes (/mcd), /code, /promo (/promocode) на Velocity.
 */
public final class VelocityCommands {

    private VelocityCommands() {
    }

    public static void registerAll(MyCodesVelocityPlugin plugin, CommandManager manager,
                                   final CommandService service) {

        manager.register(meta(manager, plugin, "mycodes", "mcd"), new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                service.executeMain(new VelocitySender(invocation.source()),
                        "mycodes", invocation.arguments());
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return subCommandSuggestions(invocation.arguments());
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return true; // детальные проверки делает CommandService
            }
        });

        manager.register(meta(manager, plugin, "code", new String[0]), new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                String[] args = invocation.arguments();
                service.executeRedeem(new VelocitySender(invocation.source()),
                        args.length > 0 ? args[0] : "");
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return true;
            }
        });

        manager.register(meta(manager, plugin, "promo", "promocode"), new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                String[] args = invocation.arguments();
                service.executeRedeem(new VelocitySender(invocation.source()),
                        args.length > 0 ? args[0] : "");
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return true;
            }
        });
    }

    private static CommandMeta meta(CommandManager manager, MyCodesVelocityPlugin plugin,
                                    String command, String... aliases) {
        CommandMeta.Builder builder = manager.metaBuilder(command).plugin(plugin);
        if (aliases != null && aliases.length > 0) {
            builder.aliases(aliases);
        }
        return builder.build();
    }

    private static List<String> subCommandSuggestions(String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 0) {
            out.add("help");
            out.add("menu");
            out.add("list");
            out.add("reload");
            out.add("code");
        } else if (args.length == 1) {
            for (String sub : new String[]{"help", "menu", "list", "reload", "code"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
        }
        return out;
    }
}
