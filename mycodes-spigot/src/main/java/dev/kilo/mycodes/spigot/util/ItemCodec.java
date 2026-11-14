package dev.kilo.mycodes.spigot.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Сериализация ItemStack ↔ base64 (для ITEM-наград, передаётся в промокоде). */
public final class ItemCodec {

    private ItemCodec() {
    }

    /** ItemStack → base64, или null если предмет пуст */
    public static String toBase64(ItemStack item) {
        if (item == null) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
            out.writeObject(item);
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /** base64 → ItemStack, или null */
    public static ItemStack fromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] data = Base64.getDecoder().decode(base64.replace("\n", "").replace("\r", "").replace(" ", ""));
            BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data));
            ItemStack item = (ItemStack) in.readObject();
            in.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
