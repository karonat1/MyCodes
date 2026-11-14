package dev.kilo.mycodes.spigot.reward;

import dev.kilo.mycodes.common.RewardDispatcher;
import dev.kilo.mycodes.common.model.RewardData;
import dev.kilo.mycodes.common.model.RewardType;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.effects.Effects;
import dev.kilo.mycodes.spigot.util.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Выдача награды на локальном сервере: эффект активации, затем (с задержкой
 * «времени активации») команда от консоли или предмет.
 */
public class RewardService implements RewardDispatcher {

    private final MyCodesSpigotPlugin plugin;

    public RewardService(MyCodesSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean dispatch(UUID playerId, String playerName, String server, RewardData reward) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        give(player, playerName == null ? player.getName() : playerName, reward);
        return true;
    }

    /** Выдать награду эффектно: сначала эффект, потом (после delay) само вознаграждение */
    public void give(final Player player, final String playerName, final RewardData reward) {
        if (reward == null) return;
        // эффект активации
        if (reward.effectDuration > 0) {
            Effects.play(plugin, player, reward.effectType(),
                    reward.effectDuration, plugin.backend().effectSounds());
        }
        // крупное подтверждение по центру экрана + полоса action bar
        String title = plugin.core().lang().msg("redeem-title");
        String subtitle = plugin.core().lang().msg("redeem-subtitle");
        player.sendTitle(title, subtitle, 5, 30, 10);
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                        plugin.core().lang().msg("redeem-actionbar")));
        final long delayTicks = Math.max(0, reward.rewardDelay) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                execute(player, playerName, reward);
            }
        }, delayTicks);
    }

    private void execute(Player player, String playerName, RewardData reward) {
        if (reward.type() == RewardType.ITEM) {
            ItemStack item = ItemCodec.fromBase64(reward.itemBase64);
            if (item == null) {
                plugin.getLogger().warning("Reward item could not be deserialized for " + playerName);
                return;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                for (ItemStack left : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
                player.sendMessage(plugin.core().lang().msg("item-drop-full-inventory"));
            }
        } else {
            String command = reward.command == null ? "" : reward.command.trim();
            if (command.isEmpty()) {
                plugin.getLogger().warning("Empty reward command for " + playerName);
                return;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", playerName));
        }
    }
}
