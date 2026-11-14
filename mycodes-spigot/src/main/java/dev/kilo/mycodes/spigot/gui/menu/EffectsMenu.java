package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.common.model.EffectType;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор эффекта активации (10 эффектов). ЛКМ — выбрать, ПКМ — превью на себе.
 * Каждый эффект показывает своё «красивое» имя и описание из lang-файла.
 */
public class EffectsMenu extends Menu {

    public interface SelectHandler {
        void onSelect(EffectType type);
    }

    private static final EffectType[] ORDER = {
            EffectType.FIREWORK, EffectType.FIREWORK_RAIN, EffectType.HELIX, EffectType.DOME,
            EffectType.TOTEM, EffectType.HEARTS, EffectType.ENCHANT, EffectType.EXPLOSION,
            EffectType.WATER, EffectType.MYSTIC, EffectType.LASER, EffectType.FAIRY,
            EffectType.DRAGON, EffectType.PULSAR
    };

    private final EffectType current;
    private final SelectHandler handler;
    private final PromoCode previewRef;

    public EffectsMenu(MyCodesSpigotPlugin plugin, Player player, EffectType current,
                        SelectHandler handler, PromoCode previewRef) {
        super(plugin, player, 54, plugin.core().lang().msg("menu-title-effects"));
        this.current = current;
        this.handler = handler;
        this.previewRef = previewRef;
    }

    @Override
    protected void draw() {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        // заголовок
        set(4, ItemBuilder.of(Material.FIREWORK_ROCKET)
                .name(plugin.core().lang().msg("effects-title-name"))
                .lore(plugin.core().lang().msgList("effects-lore"))
                .build());

        // 14 эффектов: слоты 10–14, 19–23 и 28–32 (три ряда по 5)
        for (int i = 0; i < ORDER.length; i++) {
            final EffectType type = ORDER[i];
            int column = i % 5;
            int row = i / 5;
            int slot = 10 + column + row * 9;
            List<String> lore = new ArrayList<String>();
            lore.add("&7" + plugin.core().lang().msg("effect-desc-" + type.name()));
            lore.add("");
            lore.addAll(plugin.core().lang().msgList("effects-lore"));
            ItemBuilder builder = ItemBuilder.of(EditorMenu.effectMaterial(type))
                    .name("&b" + plugin.core().lang().msg("effect-" + type.name()))
                    .lore(lore);
            if (type == current) {
                builder.glow();
            }
            set(slot, builder.build(), new ClickAction() {
                @Override
                public void run() {
                    handler.onSelect(type);
                }
            });
        }
        // назад
        set(49, ItemBuilder.of(Material.ARROW).name(plugin.core().lang().msg("editor-back-name"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                if (previewRef != null) {
                    new EditorMenu(plugin, player, previewRef).open();
                } else {
                    new SettingsMenu(plugin, player).open();
                }
            }
        });
    }

    @Override
    protected void handleClick(InventoryClickEvent event) {
        super.handleClick(event);
        // правый клик = превью на себе
        if (event.isRightClick() && event.getCurrentItem() != null
                && event.getRawSlot() >= 10 && event.getRawSlot() < 33) {
            for (final EffectType type : ORDER) {
                if (EditorMenu.effectMaterial(type) == event.getCurrentItem().getType()) {
                    final int duration = previewRef != null
                            ? previewRef.effectDuration : plugin.backend().defaultEffectDuration();
                    plugin.playEffectPreview(player, type, duration);
                    return;
                }
            }
        }
    }
}
