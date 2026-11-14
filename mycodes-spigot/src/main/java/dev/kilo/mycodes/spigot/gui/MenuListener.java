package dev.kilo.mycodes.spigot.gui;

import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.gui.menu.ItemMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Маршрутизация кликов в меню, отмена перетаскивания.
 * ItemMenu (ввод предмета-награды) сам разрешает взаимодействие со слотом.
 */
public class MenuListener implements Listener {

    private final MyCodesSpigotPlugin plugin;

    public MenuListener(MyCodesSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Menu)) return;
        Menu menu = (Menu) holder;
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!menu.player().equals(event.getWhoClicked())) return;

        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        // ItemMenu: клики по нижнему (своему) инвентарю разрешены — игрок
        // должен иметь возможность держать предмет на курсоре
        if (menu instanceof ItemMenu && !inTop) {
            return; // handling полностью отдаётся ванильной логике
        }

        event.setCancelled(true);

        // ItemMenu: слот предмета — свободное взаимодействие (класть/брать)
        if (menu instanceof ItemMenu && raw == ((ItemMenu) menu).itemSlot()) {
            event.setCancelled(false);
            return;
        }
        // shift-клик из нижнего инвентаря в меню предметов — разрешён
        if (menu instanceof ItemMenu && event.isShiftClick() && !inTop) {
            event.setCancelled(false);
            return;
        }

        if (raw < 0 || !inTop) return;
        menu.clickSound();
        menu.fire(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Menu)) return;
        if (holder instanceof ItemMenu) {
            // в ItemMenu драг разрешён только в слот предмета
            ItemMenu itemMenu = (ItemMenu) holder;
            boolean allInItemSlot = !event.getRawSlots().isEmpty()
                    && event.getRawSlots().size() == 1
                    && event.getRawSlots().iterator().next() == itemMenu.itemSlot();
            event.setCancelled(!allInItemSlot);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Menu) {
            ((Menu) holder).onClose();
        }
    }
}
