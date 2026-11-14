package dev.kilo.mycodes.spigot.gui;

import dev.kilo.mycodes.common.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Построитель предметов меню. &-коды и разметка <gradient> в имени и лоре
 * автоматически конвертируются в legacy (§), если их там ещё нет.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<String>();
    private Boolean glow;

    private ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    public static ItemBuilder of(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    public ItemBuilder name(String rawName) {
        if (rawName == null) {
            meta.setDisplayName(" ");
            return this;
        }
        String legacy = rawName.indexOf('\u00A7') >= 0 ? rawName : TextUtil.toLegacy(rawName);
        meta.setDisplayName(legacy);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        for (String line : lines) {
            lore.add(line == null ? " " : line);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        for (String line : lines) {
            lore.add(line == null ? " " : line);
        }
        return this;
    }

    public ItemBuilder glow() {
        glow = Boolean.TRUE;
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemStack build() {
        List<String> renderedLore = new ArrayList<String>(lore.size());
        for (String line : lore) {
            renderedLore.add(line.indexOf('\u00A7') >= 0 ? line : TextUtil.toLegacy(line));
        }
        if (!renderedLore.isEmpty()) {
            meta.setLore(renderedLore);
        }
        if (Boolean.TRUE.equals(glow)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }
}
