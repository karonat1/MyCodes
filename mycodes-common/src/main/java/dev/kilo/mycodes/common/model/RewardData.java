package dev.kilo.mycodes.common.model;

/**
 * Данные о награде и эффекте — передаются платформе (или по мосту на backend),
 * где она выдаётся игроку.
 */
public class RewardData {

    public String effect;
    public int effectDuration;
    public int rewardDelay;
    /** COMMAND | ITEM */
    public String rewardType;
    public String command;
    public String itemBase64;
    /** id записи активации; >0 — прокси отметит «награда выдана» после отправки */
    public long activationId = 0L;

    public RewardData() {
    }

    public RewardData(PromoCode promo) {
        this.effect = promo.effect;
        this.effectDuration = promo.effectDuration;
        this.rewardDelay = promo.rewardDelay;
        this.rewardType = promo.type;
        this.command = promo.command;
        this.itemBase64 = promo.itemBase64;
    }

    public EffectType effectType() {
        return EffectType.of(effect);
    }

    public RewardType type() {
        return RewardType.of(rewardType);
    }

    public static RewardData none() {
        RewardData d = new RewardData();
        d.effect = "FIREWORK";
        d.effectDuration = 0;
        d.rewardDelay = 0;
        d.rewardType = "COMMAND";
        d.command = "";
        return d;
    }
}
