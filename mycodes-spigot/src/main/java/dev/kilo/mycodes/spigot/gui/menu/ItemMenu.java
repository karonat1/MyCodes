package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import dev.kilo.mycodes.spigot.util.ItemCodec;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Меню ввода предмета-награды: положите предмет в центральный слот и нажмите
 * «Готово». Пустой слот НЕ занимает стекло — это обычный пустой слот,
 * с которым можно взаимодействовать (класть/брать предмет на курсор).
 */
public class ItemMenu extends Menu {

    public interface ItemHandler {
        void onSave(String base64);
    }

    public static final int ITEM_SLOT = 13;

    private final String existingBase64;
    private final ItemHandler handler;

    public ItemMenu(MyCodesSpigotPlugin plugin, Player player, String existingBase64, ItemHandler handler) {
        super(plugin, player, 27, plugin.core().lang().msg("menu-title-item"));
        this.existingBase64 = existingBase64;
        this.handler = handler;
    }

    public int itemSlot() {
        return ITEM_SLOT;
    }

    @Override
    protected void draw() {
        // сначала рамка-филлер, потом «дырки»
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        // слот предмета: если предмет уже задан — его копия, иначе ПУСТОЙ СЛОТ
        // (не стекло! — чтобы предмет можно было свободно класть и забирать)
        getInventory().setItem(ITEM_SLOT, null);
        ItemStack existing = ItemCodec.fromBase64(existingBase64);
        if (existing != null) {
            getInventory().setItem(ITEM_SLOT, existing.clone());
        }

        // подсказка над слотом, не мешает взаимодействию
        set(4, ItemBuilder.of(Material.HOPPER)
                .name(plugin.core().lang().msg("item-slot-name"))
                .lore(plugin.core().lang().msgList("item-slot-lore"))
                .build());

        // «Готово» — сохраняет предмет из слота
        set(22, ItemBuilder.of(Material.EMERALD_BLOCK)
                .name(plugin.core().lang().msg("item-done-name"))
                .lore(plugin.core().lang().msgList("item-done-lore"))
                .glow()
                .build(), new ClickAction() {
            @Override
            public void run() {
                finish();
            }
        });

        set(26, ItemBuilder.of(Material.BARRIER)
                .name(plugin.core().lang().msg("item-cancel-name"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                player.closeInventory();
            }
        });
    }

    @Override
    protected void handleClick(InventoryClickEvent event) {
        // клики по слоту предмета разрешены (кладём/забираем предмет)
        if (event.getRawSlot() == ITEM_SLOT) {
            event.setCancelled(false);
            return;
        }
        // shift-клик из нижнего инвентаря в слот тоже полезен
        if (event.isShiftClick() && event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            event.setCancelled(false);
            return;
        }
        super.handleClick(event);
    }

    @Override
    protected void onClose() {
        // при закрытии без «Готово» предмет, оставленный в слоте, теряется —
        // возвращаем его игроку
        ItemStack inSlot = getInventory().getItem(ITEM_SLOT);
        if (inSlot != null && inSlot.getType() != Material.AIR) {
            player.getInventory().addItem(inSlot);
            player.sendMessage(plugin.core().lang().msg("item-returned"));
        }
    }

    private void finish() {
        ItemStack inSlot = getInventory().getItem(ITEM_SLOT);
        String base64 = null;
        if (inSlot != null && inSlot.getType() != Material.AIR) {
            base64 = ItemCodec.toBase64(inSlot.clone());
        }
        if (base64 == null) {
            player.sendMessage(plugin.core().lang().msg("input-invalid"));
            return;
        }
        // забираем предмет из меню
        getInventory().setItem(ITEM_SLOT, null);
        player.closeInventory();
        player.sendMessage(plugin.core().lang().msg("item-saved"));
        handler.onSave(base64);
    }

    @Override
    public void open() {
        draw();
        player.openInventory(getInventory());
    }
}
