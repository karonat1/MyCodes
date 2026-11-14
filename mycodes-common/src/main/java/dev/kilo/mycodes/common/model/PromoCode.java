package dev.kilo.mycodes.common.model;

import java.util.UUID;

/**
 * Промокод. Сериализуется в JSON хранилищем и передаётся по мосту proxy <-> backend.
 */
public class PromoCode {

    public String id = UUID.randomUUID().toString();
    /** Текст кода (оригинальный регистр), поиск регистронезависимый */
    public String code = "";
    /** Сервер, где можно активировать. null/"any" = любой сервер */
    public String server = "any";
    /** COMMAND | ITEM */
    public String type = "COMMAND";
    /** Команда консоли с %player% (для типа COMMAND) */
    public String command = "";
    /** ItemStack в base64 (для типа ITEM) */
    public String itemBase64 = "";
    /** 0 = без лимита */
    public int maxUses = 100;
    public int usedCount = 0;
    /** Каждый игрок может активировать только один раз */
    public boolean oncePerPlayer = true;
    /** Один Telegram-аккаунт может активировать этот код только один раз (анти-твинк) */
    public boolean telegramOnce = true;
    /** DEFAULT (глобальная настройка) | REQUIRED (обязательно) | OFF (выключено) */
    public String telegramMode = "DEFAULT";
    /** Эффект активации (EffectType) */
    public String effect = "FIREWORK";
    /** Длительность эффекта, сек */
    public int effectDuration = 3;
    /** «Время активации» — задержка выдачи награды, сек */
    public int rewardDelay = 0;
    /** Время создания, epoch ms */
    public long createdAt = System.currentTimeMillis();
    /** Время истечения, epoch ms. 0 = бессрочный */
    public long expiresAt = 0L;
    public boolean enabled = true;
    /** Свое сообщение игроку при активации (с градиентами), null = стандартное из lang */
    public String message = null;

    public transient boolean isEditing;

    public RewardType rewardType() {
        return RewardType.of(type);
    }

    public EffectType effectType() {
        return EffectType.of(effect);
    }

    public boolean isUnlimited() {
        return maxUses <= 0;
    }

    public boolean isExpired(long now) {
        return expiresAt > 0 && expiresAt < now;
    }

    public String serverOrNull() {
        return server == null || server.isEmpty() || server.equalsIgnoreCase("any") ? null : server;
    }

    public boolean anyServer() {
        return serverOrNull() == null;
    }

    public String codeKey() {
        return code == null ? "" : code.toLowerCase();
    }
}
