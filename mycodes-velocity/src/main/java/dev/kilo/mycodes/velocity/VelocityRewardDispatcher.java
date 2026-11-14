package dev.kilo.mycodes.velocity;

import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.RewardDispatcher;
import dev.kilo.mycodes.common.model.RewardData;
import dev.kilo.mycodes.common.model.RewardType;
import dev.kilo.mycodes.common.util.JsonUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Выдача наград с Velocity: через мост на backend или командой в консоли
 * прокси, если мост не установлен.
 */
public class VelocityRewardDispatcher implements RewardDispatcher {

    private final MyCodesVelocityPlugin plugin;
    private final Map<UUID, Long> connectedPlayers = new ConcurrentHashMap<UUID, Long>();
    private final Map<String, Long> bridgedServers = new ConcurrentHashMap<String, Long>();
    private static final long TTL = 10 * 60 * 1000L;

    public VelocityRewardDispatcher(MyCodesVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    public void markBridged(String serverName) {
        bridgedServers.put(serverName, System.currentTimeMillis());
    }

    public boolean isBridged(String serverName) {
        Long last = bridgedServers.get(serverName);
        return last != null && System.currentTimeMillis() - last < TTL;
    }

    /** Флаг «этот игрок сейчас на сервере с мостом» (обновляется входящими пакетами) */
    public boolean isConnected(com.velocitypowered.api.proxy.Player player) {
        Long last = connectedPlayers.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < TTL;
    }

    public void markConnected(UUID playerId) {
        connectedPlayers.put(playerId, System.currentTimeMillis());
    }

    @Override
    public boolean dispatch(UUID playerId, String playerName, String server, RewardData reward) {
        com.velocitypowered.api.proxy.Player player = plugin.proxy().getPlayer(playerId).orElse(null);
        if (player == null) return false;
        com.velocitypowered.api.proxy.ServerConnection connection = player.getCurrentServer().orElse(null);
        if (connection == null) return false;
        String serverName = connection.getServerInfo().getName();
        if (isBridged(serverName)) {
            com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
            payload.addProperty("uuid", playerId.toString());
            payload.addProperty("name", playerName == null ? player.getUsername() : playerName);
            payload.add("reward", JsonUtil.parseElement(JsonUtil.toJson(reward)));
            connection.sendPluginMessage(MyCodesVelocityPlugin.CHANNEL,
                    BridgeProtocol.encode(BridgeProtocol.REWARD, payload.toString()));
            return true;
        }
        if (reward.type() == RewardType.COMMAND && reward.command != null && !reward.command.isEmpty()) {
            String command = reward.command.startsWith("/") ? reward.command.substring(1) : reward.command;
            plugin.proxy().getCommandManager().executeAsync(
                    plugin.proxy().getConsoleCommandSource(),
                    command.replace("%player%", playerName));
            return true;
        }
        return false;
    }
}
