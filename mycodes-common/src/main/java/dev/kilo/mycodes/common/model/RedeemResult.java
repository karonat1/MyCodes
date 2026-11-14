package dev.kilo.mycodes.common.model;

/**
 * Результат активации промокода.
 */
public class RedeemResult {

    public enum Status { SUCCESS, PENDING_TELEGRAM, ERROR }

    public Status status;
    /** Готовое legacy-сообщение (градиенты уже отрисованы) */
    public String message;
    /** Для SUCCESS: награда и эффект */
    public RewardData reward;
    /** Для PENDING_TELEGRAM: персональная ссылка подтверждения */
    public String linkUrl;
    /** Для PENDING_TELEGRAM: текст ссылки */
    public String linkText;
    /** Промокод (для SUCCESS/PENDING) */
    public String promoCode;

    public static RedeemResult error(String message) {
        RedeemResult r = new RedeemResult();
        r.status = Status.ERROR;
        r.message = message;
        return r;
    }

    public static RedeemResult success(String message, String promoCode, RewardData reward) {
        RedeemResult r = new RedeemResult();
        r.status = Status.SUCCESS;
        r.message = message;
        r.promoCode = promoCode;
        r.reward = reward;
        return r;
    }

    public static RedeemResult pending(String message, String promoCode, String url, String linkText) {
        RedeemResult r = new RedeemResult();
        r.status = Status.PENDING_TELEGRAM;
        r.message = message;
        r.promoCode = promoCode;
        r.linkUrl = url;
        r.linkText = linkText;
        return r;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
