package dev.kilo.mycodes.common;

/**
 * Протокол моста proxy <-> backend по каналу plugin-messaging «mycodes:bridge».
 * Кадр: writeUTF(type) + writeUTF(jsonPayload).
 */
public final class BridgeProtocol {

    public static final String CHANNEL = "mycodes:bridge";

    // backend → proxy
    public static final String HELLO = "hello";
    public static final String GET_DATA = "getdata";
    public static final String CREATE_PROMO = "create";
    public static final String UPDATE_PROMO = "update";
    public static final String DELETE_PROMO = "delete";
    public static final String REDEEM = "redeem";
    public static final String RELOAD = "reload";
    public static final String SETTINGS = "settings";
    public static final String REWARD_DONE = "rewarddone";
    public static final String PREVIEW_EFFECT = "preview";

    // proxy → backend
    public static final String CONFIG = "config";
    public static final String PROMOS = "promos";
    public static final String REDEEM_RESULT = "redeemresult";
    public static final String REWARD = "reward";
    public static final String DONE = "done";
    public static final String BROADCAST = "broadcast";

    private BridgeProtocol() {
    }

    public static byte[] encode(String type, String json) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF(type);
            out.writeUTF(json == null ? "" : json);
            out.flush();
        } catch (java.io.IOException impossible) {
        }
        return bytes.toByteArray();
    }

    /** @return {type, json} или null при ошибке кадра */
    public static String[] decode(byte[] data) {
        if (data == null || data.length < 4) return null;
        try {
            java.io.DataInputStream in = new java.io.DataInputStream(
                    new java.io.ByteArrayInputStream(data));
            String type = in.readUTF();
            String json = in.readUTF();
            return new String[]{type, json};
        } catch (Exception e) {
            return null;
        }
    }
}
