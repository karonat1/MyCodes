package dev.kilo.mycodes.bungee;

import dev.kilo.mycodes.common.RewardDispatcher;
import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.model.RewardData;
import dev.kilo.mycodes.common.model.RewardType;
import dev.kilo.mycodes.common.util.JsonUtil;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Выдача наград с прокси: через мост на backend (эффект + награда на сервере
 * игрока), при отсутствии моста — команда в консоли прокси.
 */
public class BungeeRewardDispatcher implements RewardDispatcher {

    private final MyCodesBungeePlugin plugin;
    /** сервера, от которых недавно приходили пакеты моста */
    private final Map<String, Long> bridgedServers = new ConcurrentHashMap<String, Long>();
    private static final long BRIDGE_TTL = 10 * 60 * 1000L;

    public BungeeRewardDispatcher(MyCodesBungeePlugin plugin) {
        this.plugin = plugin;
    }

    public void markBridged(String serverName) {
        bridgedServers.put(serverName, System.currentTimeMillis());
    }

    public boolean isBridged(String serverName) {
        Long last = bridgedServers.get(serverName);
        return last != null && System.currentTimeMillis() - last < BRIDGE_TTL;
    }

    @Override
    public boolean dispatch(UUID playerId, String playerName, String server, RewardData reward) {
        ProxyServer proxy = ProxyServer.getInstance();
        ProxiedPlayer player = proxy.getPlayer(playerId);
        if (player == null || player.getServer() == null) {
            return false; // оффлайн — останется в очереди, выдадим при следующем входе
        }
        Server target = player.getServer();
        String serverName = target.getInfo().getName();
        if (isBridged(serverName)) {
            // отправляем через мост: эффект + выдача произойдут на сервере игрока
            com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
            payload.addProperty("uuid", playerId.toString());
            payload.addProperty("name", playerName == null ? player.getName() : playerName);
            payload.add("reward", JsonUtil.parseObject(JsonUtil.toJson(reward)));
            target.sendData(BridgeProtocol.CHANNEL, BridgeProtocol.encode(BridgeProtocol.REWARD, payload.toString()));
            return true;
        }
        // моста нет: команду можно выполнить в консоли прокси, предмет — только с мостом
        if (reward.type() == RewardType.COMMAND && reward.command != null && !reward.command.isEmpty()) {
            String command = reward.command.startsWith("/") ? reward.command.substring(1) : reward.command;
            proxy.getPluginManager().dispatchCommand(proxy.getConsole(),
                    command.replace("%player%", playerName));
            return true;
        }
        plugin.getLogger().warning("Cannot deliver ITEM reward to " + playerName
                + " on server " + serverName + ": MyCodes-Spigot is not installed on that server.");
        return false;
    }

    /** Отправить данные на конкретный сервер через первого игрока (для push-рассылки) */
    public boolean sendToServer(String serverName, String type, String json) {
        ServerInfo info = ProxyServer.getInstance().getServerInfo(serverName);
        if (info == null) return false;
        for (ProxiedPlayer player : info.getPlayers()) {
            if (player.getServer() != null) {
                player.getServer().sendData(BridgeProtocol.CHANNEL,
                        BridgeProtocol.encode(type, json));
                return true;
            }
        }
        return false;
    }

    /** Рассылка на все серверы, где есть игроки */
    public void broadcastToServers(String type, String json) {
        for (ServerInfo info : ProxyServer.getInstance().getServers().values()) {
            sendToServer(info.getName(), type, json);
        }
    }
}
