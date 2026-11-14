package dev.kilo.mycodes.spigot.gui;

import dev.kilo.mycodes.common.util.TextUtil;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Базовый класс меню (сундук-GUI). Все клики отменяются и маршрутизируются
 * в onClick подкласса. Титулы обрезаются до лимита клиента.
 */
public abstract class Menu implements InventoryHolder {

    protected final MyCodesSpigotPlugin plugin;
    protected final Player player;
    private final Inventory inventory;
    /** LOST: динамические предметы по слотам для быстрой маршрутизации кликов */
    private final Map<Integer, ClickAction> actions = new HashMap<Integer, ClickAction>();

    public interface ClickAction {
        void run();
    }

    protected Menu(MyCodesSpigotPlugin plugin, Player player, int size, String legacyTitle) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, size,
                TextUtil.truncateVisible(legacyTitle, 30));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    /** Нарисовать содержимое (вызывается при каждом открытии) */
    protected abstract void draw();

    /** Клики маршрутизируются через actions; переопределяется для особых случаев */
    protected void handleClick(InventoryClickEvent event) {
        ClickAction action = actions.get(event.getRawSlot());
        if (action != null) {
            action.run();
        }
    }

    protected void set(int slot, ItemStack item, ClickAction action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void fill(Material material, String name) {
        ItemStack filler = ItemBuilder.of(material).name(name == null ? " " : name).build();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public void open() {
        draw();
        player.openInventory(inventory);
    }

    protected void clickSound() {
        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    // вызывается из MenuListener
    void fire(InventoryClickEvent event) {
        handleClick(event);
    }

    /** Вызывается при закрытии меню (default пустой) */
    protected void onClose() {
    }
}
