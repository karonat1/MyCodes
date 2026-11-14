package dev.kilo.mycodes.common.storage;

import dev.kilo.mycodes.common.model.Activation;
import dev.kilo.mycodes.common.model.PendingLink;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.TgBinding;
import dev.kilo.mycodes.common.util.JsonUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * MySQL-хранилище (опционально, storage.type: mysql).
 * Простое одиночное соединение с авто-переподключением — нагрузка мизерная.
 */
public class MySqlStorage implements Storage {

    private final String url;
    private final Properties props;
    private final String prefix;
    private final Object lock = new Object();
    private Connection conn;

    public MySqlStorage(String host, int port, String database, String user, String password,
                        String tablePrefix, boolean useSsl) throws ClassNotFoundException {
        String driver = null;
        for (String d : new String[]{
                "com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver", "org.mariadb.jdbc.Driver"}) {
            try {
                Class.forName(d);
                driver = d;
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (driver == null) {
            throw new ClassNotFoundException(
                    "MySQL/MariaDB JDBC driver not found on server. Put mysql-connector jar into server libs or use storage.type: file");
        }
        this.prefix = tablePrefix == null || tablePrefix.isEmpty() ? "mycodes_" : tablePrefix;
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSsl + "&autoReconnect=true&characterEncoding=utf8&useUnicode=true";
        this.props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password == null ? "" : password);
        props.setProperty("autoReconnect", "true");
    }

    private Connection connection() throws SQLException {
        synchronized (lock) {
            if (conn == null || conn.isClosed() || !conn.isValid(2)) {
                try {
                    if (conn != null) {
                        try {
                            conn.close();
                        } catch (SQLException ignored) {
                        }
                    }
                    conn = DriverManager.getConnection(url, props);
                } catch (SQLException e) {
                    throw new SQLException("MyCodes MySQL connection failed: " + e.getMessage(), e);
                }
            }
            return conn;
        }
    }

    private void quietlyClose(Statement st, ResultSet rs) {
        try {
            if (rs != null) rs.close();
        } catch (SQLException ignored) {
        }
        try {
            if (st != null) st.close();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void init() throws Exception {
        Connection c = connection();
        Statement st = null;
        try {
            st = c.createStatement();
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "promos (" +
                    "id VARCHAR(36) NOT NULL, code VARCHAR(64) NOT NULL, data LONGTEXT, " +
                    "created BIGINT, PRIMARY KEY (id), KEY code_idx (code)) " +
                    "CHARACTER SET utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "activations (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, promo_id VARCHAR(36) NOT NULL, " +
                    "code VARCHAR(64), player_id VARCHAR(36) NOT NULL, player_name VARCHAR(32), " +
                    "time BIGINT, telegram_id VARCHAR(64), rewarded TINYINT(1) DEFAULT 0, " +
                    "PRIMARY KEY (id), KEY promo_player (promo_id, player_id), " +
                    "KEY player_idx (player_id)) CHARACTER SET utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "tg_bindings (" +
                    "player_id VARCHAR(36) NOT NULL, telegram_id VARCHAR(64) NOT NULL, " +
                    "player_name VARCHAR(32), bound_at BIGINT, PRIMARY KEY (player_id), " +
                    "KEY tg_idx (telegram_id)) CHARACTER SET utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "pending_links (" +
                    "token VARCHAR(32) NOT NULL, promo_id VARCHAR(36) NOT NULL, " +
                    "player_id VARCHAR(36), player_name VARCHAR(32), created BIGINT, expires BIGINT, " +
                    "PRIMARY KEY (token)) CHARACTER SET utf8mb4");
        } finally {
            quietlyClose(st, null);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                if (conn != null) conn.close();
            } catch (SQLException ignored) {
            }
            conn = null;
        }
    }

    // ── Промокоды ──

    @Override
    public List<PromoCode> loadPromos() {
        List<PromoCode> out = new ArrayList<PromoCode>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement("SELECT data FROM " + prefix + "promos");
            rs = ps.executeQuery();
            while (rs.next()) {
                PromoCode promo = JsonUtil.fromJson(rs.getString("data"), PromoCode.class);
                if (promo != null) out.add(promo);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
        return out;
    }

    @Override
    public void savePromo(PromoCode promo) {
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement(
                    "INSERT INTO " + prefix + "promos (id, code, data, created) VALUES (?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE code=VALUES(code), data=VALUES(data)");
            ps.setString(1, promo.id);
            ps.setString(2, promo.code == null ? "" : promo.code);
            ps.setString(3, JsonUtil.toJson(promo));
            ps.setLong(4, promo.createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, null);
        }
    }

    @Override
    public void deletePromo(String promoId) {
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement("DELETE FROM " + prefix + "promos WHERE id=?");
            ps.setString(1, promoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, null);
        }
    }

    // ── Активации ──

    @Override
    public boolean hasActivated(String promoId, UUID playerId) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement(
                    "SELECT 1 FROM " + prefix + "activations WHERE promo_id=? AND player_id=? LIMIT 1");
            ps.setString(1, promoId);
            ps.setString(2, playerId.toString());
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
    }

    @Override
    public int countByTelegram(String promoId, String telegramId) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement(
                    "SELECT COUNT(*) FROM " + prefix + "activations WHERE promo_id=? AND telegram_id=?");
            ps.setString(1, promoId);
            ps.setString(2, telegramId);
            rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
    }

    @Override
    public void addActivation(Activation activation) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement(
                    "INSERT INTO " + prefix + "activations " +
                            "(promo_id, code, player_id, player_name, time, telegram_id, rewarded) " +
                            "VALUES (?,?,?,?,?,?,0)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, activation.promoId);
            ps.setString(2, activation.code);
            ps.setString(3, activation.playerId);
            ps.setString(4, activation.playerName);
            ps.setLong(5, activation.time);
            ps.setString(6, activation.telegramId);
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            if (rs.next()) activation.id = rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
    }

    @Override
    public List<Activation> outstandingRewards(UUID playerId) {
        List<Activation> out = new ArrayList<Activation>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement(
                    "SELECT * FROM " + prefix + "activations WHERE player_id=? AND rewarded=0");
            ps.setString(1, playerId.toString());
            rs = ps.executeQuery();
            while (rs.next()) {
                Activation a = new Activation();
                a.id = rs.getLong("id");
                a.promoId = rs.getString("promo_id");
                a.code = rs.getString("code");
                a.playerId = rs.getString("player_id");
                a.playerName = rs.getString("player_name");
                a.time = rs.getLong("time");
                a.telegramId = rs.getString("telegram_id");
                a.rewarded = false;
                out.add(a);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
        return out;
    }

    @Override
    public void markRewarded(Activation activation) {
        markRewardedById(activation.id);
    }

    @Override
    public void markRewardedById(long activationId) {
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement(
                    "UPDATE " + prefix + "activations SET rewarded=1 WHERE id=?");
            ps.setLong(1, activationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, null);
        }
    }

    // ── Telegram-привязки ──

    @Override
    public TgBinding getBinding(UUID playerId) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement(
                    "SELECT * FROM " + prefix + "tg_bindings WHERE player_id=?");
            ps.setString(1, playerId.toString());
            rs = ps.executeQuery();
            return rs.next() ? binding(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
    }

    @Override
    public TgBinding getBindingByTg(String telegramId) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement(
                    "SELECT * FROM " + prefix + "tg_bindings WHERE telegram_id=? LIMIT 1");
            ps.setString(1, telegramId);
            rs = ps.executeQuery();
            return rs.next() ? binding(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
    }

    private TgBinding binding(ResultSet rs) throws SQLException {
        TgBinding b = new TgBinding();
        b.playerId = rs.getString("player_id");
        b.telegramId = rs.getString("telegram_id");
        b.playerName = rs.getString("player_name");
        b.boundAt = rs.getLong("bound_at");
        return b;
    }

    @Override
    public void saveBinding(TgBinding binding) {
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement(
                    "INSERT INTO " + prefix + "tg_bindings (player_id, telegram_id, player_name, bound_at) " +
                            "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE telegram_id=VALUES(telegram_id), " +
                            "player_name=VALUES(player_name)");
            ps.setString(1, binding.playerId);
            ps.setString(2, binding.telegramId);
            ps.setString(3, binding.playerName);
            ps.setLong(4, binding.boundAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, null);
        }
    }

    // ── Ожидающие подтверждения ссылки ──

    @Override
    public void savePendingLink(PendingLink link) {
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement(
                    "REPLACE INTO " + prefix + "pending_links " +
                            "(token, promo_id, player_id, player_name, created, expires) VALUES (?,?,?,?,?,?)");
            ps.setString(1, link.token);
            ps.setString(2, link.promoId);
            ps.setString(3, link.playerId);
            ps.setString(4, link.playerName);
            ps.setLong(5, link.createdAt);
            ps.setLong(6, link.expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, null);
        }
    }

    @Override
    public PendingLink getPendingLink(String token) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection().prepareStatement(
                    "SELECT * FROM " + prefix + "pending_links WHERE token=?");
            ps.setString(1, token);
            rs = ps.executeQuery();
            if (!rs.next()) return null;
            PendingLink p = new PendingLink();
            p.token = rs.getString("token");
            p.promoId = rs.getString("promo_id");
            p.playerId = rs.getString("player_id");
            p.playerName = rs.getString("player_name");
            p.createdAt = rs.getLong("created");
            p.expiresAt = rs.getLong("expires");
            return p;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, rs);
        }
    }

    @Override
    public void deletePendingLink(String token) {
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement(
                    "DELETE FROM " + prefix + "pending_links WHERE token=?");
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, null);
        }
    }

    @Override
    public void cleanExpiredPendingLinks(long now) {
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement(
                    "DELETE FROM " + prefix + "pending_links WHERE expires > 0 AND expires < ?");
            ps.setLong(1, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            quietlyClose(ps, null);
        }
    }
}
