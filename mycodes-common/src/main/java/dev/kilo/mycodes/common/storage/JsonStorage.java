package dev.kilo.mycodes.common.storage;

import dev.kilo.mycodes.common.model.Activation;
import dev.kilo.mycodes.common.model.PendingLink;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.TgBinding;
import dev.kilo.mycodes.common.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Хранилище в JSON-файлах: promos.json, activations.json, pending.json, tg-bindings.json.
 * Файлы перезаписываются целиком (атомарно, через tmp + rename).
 */
public class JsonStorage implements Storage {

    private static final int MAX_ACTIVATIONS = 50_000;

    private final Path folder;
    private final Object lock = new Object();

    private List<PromoCode> promos = new ArrayList<PromoCode>();
    private List<Activation> activations = new ArrayList<Activation>();
    private List<PendingLink> pending = new ArrayList<PendingLink>();
    private List<TgBinding> bindings = new ArrayList<TgBinding>();

    public JsonStorage(Path dataFolder) {
        this.folder = dataFolder;
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(folder);
        List<PromoCode> p = readAll("promos.json", PromoCode[].class);
        List<Activation> a = readAll("activations.json", Activation[].class);
        List<PendingLink> pl = readAll("pending.json", PendingLink[].class);
        List<TgBinding> b = readAll("tg-bindings.json", TgBinding[].class);
        if (p != null) promos = p;
        if (a != null) activations = a;
        if (pl != null) pending = pl;
        if (b != null) bindings = b;
    }

    @Override
    public void close() {
    }

    private <T> List<T> readAll(String file, Class<T[]> type) {
        try {
            Path p = folder.resolve(file);
            if (!Files.exists(p)) return new ArrayList<T>();
            byte[] raw = Files.readAllBytes(p);
            if (raw.length == 0) return new ArrayList<T>();
            T[] arr = JsonUtil.fromJson(new String(raw, StandardCharsets.UTF_8), type);
            return arr == null ? new ArrayList<T>() : new ArrayList<T>(Arrays.asList(arr));
        } catch (Exception e) {
            return null;
        }
    }

    private <T> void writeAll(String file, List<T> list) throws IOException {
        Path tmp = folder.resolve(file + ".tmp");
        Path target = folder.resolve(file);
        String json = JsonUtil.toPretty(list);
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ── Промокоды ──

    @Override
    public List<PromoCode> loadPromos() {
        synchronized (lock) {
            return new ArrayList<PromoCode>(promos);
        }
    }

    @Override
    public void savePromo(PromoCode promo) throws Exception {
        synchronized (lock) {
            boolean found = false;
            for (int i = 0; i < promos.size(); i++) {
                if (promos.get(i).id.equals(promo.id)) {
                    promos.set(i, promo);
                    found = true;
                    break;
                }
            }
            if (!found) promos.add(promo);
            writeAll("promos.json", promos);
        }
    }

    @Override
    public void deletePromo(String promoId) throws Exception {
        synchronized (lock) {
            for (int i = 0; i < promos.size(); i++) {
                if (promos.get(i).id.equals(promoId)) {
                    promos.remove(i);
                    break;
                }
            }
            writeAll("promos.json", promos);
        }
    }

    // ── Активации ──

    @Override
    public boolean hasActivated(String promoId, UUID playerId) {
        synchronized (lock) {
            for (Activation a : activations) {
                if (a.promoId.equals(promoId) && a.playerId.equals(playerId.toString())) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public int countByTelegram(String promoId, String telegramId) {
        if (telegramId == null) return 0;
        synchronized (lock) {
            int n = 0;
            for (Activation a : activations) {
                if (a.promoId.equals(promoId) && telegramId.equals(a.telegramId)) n++;
            }
            return n;
        }
    }

    @Override
    public void addActivation(Activation activation) throws Exception {
        synchronized (lock) {
            activation.id = activations.size() + 1;
            activations.add(activation);
            if (activations.size() > MAX_ACTIVATIONS) {
                activations = new ArrayList<Activation>(
                        activations.subList(activations.size() - MAX_ACTIVATIONS, activations.size()));
            }
            writeAll("activations.json", activations);
        }
    }

    @Override
    public List<Activation> outstandingRewards(UUID playerId) {
        synchronized (lock) {
            List<Activation> out = new ArrayList<Activation>();
            for (Activation a : activations) {
                if (!a.rewarded && playerId.toString().equals(a.playerId)) out.add(a);
            }
            return out;
        }
    }

    @Override
    public void markRewarded(Activation activation) throws Exception {
        synchronized (lock) {
            for (Activation a : activations) {
                if (a.id == activation.id) {
                    a.rewarded = true;
                    activation.rewarded = true;
                    break;
                }
            }
            writeAll("activations.json", activations);
        }
    }

    @Override
    public void markRewardedById(long activationId) throws Exception {
        synchronized (lock) {
            for (Activation a : activations) {
                if (a.id == activationId) {
                    a.rewarded = true;
                    break;
                }
            }
            writeAll("activations.json", activations);
        }
    }

    // ── Telegram-привязки ──

    @Override
    public TgBinding getBinding(UUID playerId) {
        synchronized (lock) {
            for (TgBinding b : bindings) {
                if (b.playerId.equals(playerId.toString())) return b;
            }
            return null;
        }
    }

    @Override
    public TgBinding getBindingByTg(String telegramId) {
        if (telegramId == null) return null;
        synchronized (lock) {
            for (TgBinding b : bindings) {
                if (telegramId.equals(b.telegramId)) return b;
            }
            return null;
        }
    }

    @Override
    public void saveBinding(TgBinding binding) throws Exception {
        synchronized (lock) {
            boolean found = false;
            for (int i = 0; i < bindings.size(); i++) {
                if (bindings.get(i).playerId.equals(binding.playerId)) {
                    bindings.set(i, binding);
                    found = true;
                    break;
                }
            }
            if (!found) bindings.add(binding);
            writeAll("tg-bindings.json", bindings);
        }
    }

    // ── Ожидающие подтверждения ссылки ──

    @Override
    public void savePendingLink(PendingLink link) throws Exception {
        synchronized (lock) {
            PendingLink old = null;
            for (PendingLink p : pending) {
                if (p.token.equals(link.token)) old = p;
            }
            if (old != null) pending.remove(old);
            pending.add(link);
            writeAll("pending.json", pending);
        }
    }

    @Override
    public PendingLink getPendingLink(String token) {
        synchronized (lock) {
            for (PendingLink p : pending) {
                if (p.token.equals(token)) return p;
            }
            return null;
        }
    }

    @Override
    public void deletePendingLink(String token) throws Exception {
        synchronized (lock) {
            for (int i = 0; i < pending.size(); i++) {
                if (pending.get(i).token.equals(token)) {
                    pending.remove(i);
                    break;
                }
            }
            writeAll("pending.json", pending);
        }
    }

    @Override
    public void cleanExpiredPendingLinks(long now) throws Exception {
        synchronized (lock) {
            boolean changed = false;
            for (int i = 0; i < pending.size(); i++) {
                if (pending.get(i).isExpired(now)) {
                    pending.remove(i);
                    i--;
                    changed = true;
                }
            }
            if (changed) {
                writeAll("pending.json", pending);
            }
        }
    }
}
