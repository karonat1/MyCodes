package dev.kilo.mycodes.common.command;

import dev.kilo.mycodes.common.MyCodesCore;
import dev.kilo.mycodes.common.PlatformSender;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.util.TextUtil;

import java.util.List;

/**
 * Общая обработка команд /mcd (/mycodes) и /code (/promo, /promocode).
 * Платформа реализует только выдачу награды, меню и broadcast-хуки.
 */
public class CommandService {

    public interface Hooks {
        /** Открыть меню (Spigot) или объяснить, как получить меню (прокси) */
        void openMenu(PlatformSender sender);

        /** Активация: платформа решает — локально или через мост на прокси. null — ответ придёт асинхронно */
        RedeemResult redeem(PlatformSender sender, String code);

        /** Имя сервера игрока (для проверки server-ограничений), null = одиночный */
        String currentServer(PlatformSender sender);

        /** Полная реализация /mcd reload: локально — core.reload(), в сети — запрос прокси */
        void reload(PlatformSender sender);

        /** true если команда пришла через backend-мост — тогда core.reload() делает прокси */
        boolean isRemoteMode();

        /** Актуальный список промокодов (на backend — кеш моста) */
        List<PromoCode> promos();
    }

    public static final String PERM_USE = "mycodes.use";
    public static final String PERM_MENU = "mycodes.menu";
    public static final String PERM_LIST = "mycodes.list";
    public static final String PERM_CREATE = "mycodes.create";
    public static final String PERM_EDIT = "mycodes.edit";
    public static final String PERM_DELETE = "mycodes.delete";
    public static final String PERM_RELOAD = "mycodes.reload";
    public static final String PERM_ADMIN = "mycodes.admin";

    private static final int PAGE_SIZE = 8;

    private final MyCodesCore core;
    private final Hooks hooks;

    public CommandService(MyCodesCore core, Hooks hooks) {
        this.core = core;
        this.hooks = hooks;
    }

    /** /mcd, /mycodes */
    public void executeMain(PlatformSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        if (sub.equals("help") || sub.equals("?")) {
            help(sender);
        } else if (sub.equals("reload")) {
            if (!has(sender, PERM_RELOAD)) {
                denied(sender);
                return;
            }
            hooks.reload(sender);
        } else if (sub.equals("menu")) {
            if (!has(sender, PERM_MENU)) {
                denied(sender);
                return;
            }
            hooks.openMenu(sender);
        } else if (sub.equals("list")) {
            if (!has(sender, PERM_LIST)) {
                denied(sender);
                return;
            }
            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            renderList(sender, page);
        } else if (sub.equals("code")) {
            if (args.length >= 2) {
                executeRedeem(sender, args[1]);
            } else {
                sender.sendLegacy(core.lang().msg("redeem-usage"));
            }
        } else if (sub.equals("create")) {
            sender.sendLegacy(core.lang().msg("create-menu-hint"));
        } else {
            sender.sendLegacy(core.lang().msg("unknown-subcommand"));
            help(sender);
        }
    }

    /** /code, /promo, /promocode */
    public void executeRedeem(PlatformSender sender, String code) {
        if (!has(sender, PERM_USE)) {
            denied(sender);
            return;
        }
        if (!sender.isPlayer()) {
            sender.sendLegacy(core.lang().msg("player-only"));
            return;
        }
        code = code == null ? "" : code.trim();
        if (code.isEmpty()) {
            sender.sendLegacy(core.lang().msg("redeem-usage"));
            return;
        }
        RedeemResult result = hooks.redeem(sender, code);
        if (result == null) {
            // bridge-режим: ответ придёт асинхронно
            return;
        }
        sendResult(sender, result);
    }

    public void sendResult(PlatformSender sender, RedeemResult result) {
        String[] lines = result.message == null ? new String[0] : result.message.split("\n");
        for (String line : lines) {
            sender.sendLegacy(line);
        }
        if (result.status == RedeemResult.Status.PENDING_TELEGRAM) {
            sender.sendLegacy(" ");
            sender.sendLegacy(core.lang().msg("telegram-pending-note"));
            sender.sendLink(result.linkText == null ? result.linkUrl : result.linkText,
                    result.linkUrl);
        }
    }

    private void help(PlatformSender sender) {
        for (String line : core.lang().msgList("help")) {
            sender.sendLegacy(line);
        }
    }

    private void denied(PlatformSender sender) {
        sender.sendLegacy(core.lang().msg("no-permission"));
    }

    private boolean has(PlatformSender sender, String permission) {
        return sender.hasPermission(PERM_ADMIN) || sender.hasPermission(permission);
    }

    // ── Список промокодов ──

    public void renderList(PlatformSender sender, int page) {
        List<PromoCode> promos = hooks.promos();
        if (promos.isEmpty()) {
            sender.sendLegacy(core.lang().msg("list-empty"));
            return;
        }
        int pages = (promos.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, pages));
        sender.sendLegacy(core.lang().msg("list-header",
                "page", String.valueOf(page), "pages", String.valueOf(pages)));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(promos.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            PromoCode promo = promos.get(i);
            sender.sendLegacy(renderPromoLine(promo));
        }
        sender.sendLegacy(core.lang().msg("list-pages-format",
                "page", String.valueOf(page), "pages", String.valueOf(pages)));
    }

    public String renderPromoLine(PromoCode promo) {
        StringBuilder builder = new StringBuilder();
        List<String> template = core.lang().msgList("list-line");
        for (int i = 0; i < template.size(); i++) {
            String line = template.get(i)
                    .replace("%code%", promo.code)
                    .replace("%status%", core.lang().msg(promo.enabled ? "status-enabled" : "status-disabled"))
                    .replace("%type%", core.lang().msg(promo.rewardType() == dev.kilo.mycodes.common.model.RewardType.ITEM ? "type-item" : "type-command"))
                    .replace("%server%", promo.anyServer() ? core.lang().msg("server-any") : promo.server)
                    .replace("%uses%", String.valueOf(promo.usedCount))
                    .replace("%max%", promo.isUnlimited() ? core.lang().msg("unlimited") : String.valueOf(promo.maxUses))
                    .replace("%effect%", core.lang().msg("effect-" + promo.effectType().name().toLowerCase()))
                    .replace("%duration%", String.valueOf(promo.effectDuration))
                    .replace("%delay%", String.valueOf(promo.rewardDelay))
                    .replace("%expire%", promo.expiresAt > 0 ? core.promos().formatDate(promo.expiresAt) : core.lang().msg("expire-never"))
                    .replace("%tg%", core.lang().msg("tg-mode-" + (promo.telegramMode == null ? "DEFAULT" : promo.telegramMode.toUpperCase())));
            builder.append(line);
            if (i < template.size() - 1) builder.append('\n');
        }
        return builder.toString();
    }

    public MyCodesCore core() {
        return core;
    }
}
