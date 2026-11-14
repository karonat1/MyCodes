package dev.kilo.mycodes.spigot.effects;

import dev.kilo.mycodes.common.model.EffectType;
import dev.kilo.mycodes.common.util.TextUtil;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Р”РµСЃСЏС‚СЊ РєСЂР°СЃРѕС‡РЅС‹С… СЌС„С„РµРєС‚РѕРІ Р°РєС‚РёРІР°С†РёРё РїСЂРѕРјРѕРєРѕРґР°. Р’СЃС‘ С‡РµСЂРµР· РѕС„РёС†РёР°Р»СЊРЅС‹Р№
 * Spigot API вЂ” СЃРѕРІРјРµСЃС‚РёРјРѕ СЃ 1.16.5 вЂ¦ 1.20.1 Р±РµР· РїРµСЂРµСЃР±РѕСЂРєРё.
 *
 * <p>Р”Р»РёС‚РµР»СЊРЅРѕСЃС‚СЊ (СЃРµРє) Р·Р°РґР°С‘С‚СЃСЏ РїСЂРѕРјРѕРєРѕРґРѕРј, Р·РІСѓРєРё вЂ” РЅР°СЃС‚СЂРѕР№РєРѕР№ effects.sounds.
 */
public final class Effects {

    private Effects() {
    }

    private static final Random RANDOM = new Random();

    /** РџСЂРѕРёРіСЂР°С‚СЊ СЌС„С„РµРєС‚ Р°РєС‚РёРІР°С†РёРё (РІС‹Р·С‹РІР°С‚СЊ РёР· РіР»Р°РІРЅРѕРіРѕ РїРѕС‚РѕРєР°!) */
    public static void play(Plugin plugin, Player player, EffectType type, int durationSec, boolean sounds) {
        int ticks = Math.max(1, durationSec) * 20;
        switch (type) {
            case FIREWORK:
                fireworkSalvo(player, sounds);
                break;
            case FIREWORK_RAIN:
                loop(plugin, player, ticks / 8, 8, 1, sounds, new Runnable() {
                    @Override
                    public void run() {
                        spawnFirework(player.getLocation().add(0, 2.5, 0), 1, sounds);
                    }
                });
                break;
            case HELIX:
                helix(plugin, player, ticks, sounds);
                break;
            case DOME:
                dome(plugin, player, Math.min(ticks, 60), sounds);
                break;
            case TOTEM:
                totem(plugin, player, sounds);
                break;
            case HEARTS:
                hearts(plugin, player, ticks, sounds);
                break;
            case ENCHANT:
                enchant(plugin, player, ticks, sounds);
                break;
            case EXPLOSION:
                explosion(plugin, player, sounds);
                break;
            case WATER:
                water(plugin, player, ticks, sounds);
                break;
            case MYSTIC:
                mystic(plugin, player, Math.min(ticks, 80), sounds);
                break;
            case LASER:
                laser(plugin, player, Math.min(ticks, 100), sounds);
                break;
            case FAIRY:
                fairy(plugin, player, ticks, sounds);
                break;
            case DRAGON:
                dragon(plugin, player, sounds);
                break;
            case PULSAR:
                pulsar(plugin, player, ticks, sounds);
                break;
            default:
                fireworkSalvo(player, sounds);
        }
        if (sounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    // в”Ђв”Ђ 1. FIREWORK: Р·Р°Р»Рї С„РµР№РµСЂРІРµСЂРєР° в”Ђв”Ђ
    private static void fireworkSalvo(Player player, boolean sounds) {
        Location base = player.getLocation();
        spawnFirework(base.clone().add(0, 1.2, 0), 0, sounds);
        spawnFirework(base.clone().add(1.5, 2.2, -1.2), 0, sounds);
        spawnFirework(base.clone().add(-1.5, 2.2, 1.2), 0, sounds);
        if (sounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1f);
        }
    }

    private static void spawnFirework(Location location, int delayTicks, boolean sounds) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.fromRGB(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256)))
                .withFade(Color.fromRGB(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256)))
                .trail(true)
                .flicker(RANDOM.nextBoolean())
                .build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        // РґР»СЏ РјРіРЅРѕРІРµРЅРЅРѕРіРѕ РІР·СЂС‹РІР° вЂ” РґРµС‚РѕРЅРёСЂСѓРµРј (delayTicks РЅРµ РєСЂРёС‚РёС‡РµРЅ, РІСЃС‘ СЂР°РІРЅРѕ power=0)
        firework.detonate();
    }

    // в”Ђв”Ђ 3. HELIX: РґРІРѕР№РЅР°СЏ СЃРїРёСЂР°Р»СЊ в”Ђв”Ђ
    private static void helix(Plugin plugin, Player player, int ticks, boolean sounds) {
        final double[] angle = {0};
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 2;
                if (t > ticks) {
                    cancel();
                    if (sounds) {
                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.2f);
                    }
                    return;
                }
                Location loc = player.getLocation();
                angle[0] += Math.PI / 8;
                double y = (double) t / ticks * 2.6;
                for (int strand = 0; strand < 2; strand++) {
                    double a = angle[0] + strand * Math.PI;
                    double x = Math.cos(a) * 1.1;
                    double z = Math.sin(a) * 1.1;
                    Location point = loc.clone().add(x, y, z);
                    player.spawnParticle(Particle.FLAME, point, 1, 0, 0, 0, 0);
                    if (t % 6 == 0) {
                        player.spawnParticle(Particle.CRIT_MAGIC, point, 3, 0.05, 0.05, 0.05, 0.02);
                    }
                }
                if (t % 40 == 0 && sounds) {
                    player.playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 0.4f, 1.6f);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // в”Ђв”Ђ 4. DOME: СЃРІРµС‚СЏС‰РёР№СЃСЏ РєСѓРїРѕР» СЃ РіСЂР°РґРёРµРЅС‚РѕРј в”Ђв”Ђ
    private static void dome(Plugin plugin, Player player, int ticks, boolean sounds) {
        final Color[] from = {Color.fromRGB(0, 229, 255)};
        final Color[] to = {Color.fromRGB(124, 77, 255)};
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                t += 4;
                if (t > ticks) {
                    cancel();
                    return;
                }
                double progress = (double) t / ticks;
                double radius = 2.6;
                double phi = Math.PI * (1.0 - progress * 0.5); // РєСѓРїРѕР» РѕРїСѓСЃРєР°РµС‚СЃСЏ СЃРІРµСЂС…Сѓ
                Color c = lerpColor(from[0], to[0], progress);
                Particle.DustOptions dust = new Particle.DustOptions(c, 1.4f);
                for (int i = 0; i < 14; i++) {
                    double theta = Math.PI * 2 * i / 14 + progress * Math.PI;
                    double x = radius * Math.sin(phi) * Math.cos(theta);
                    double z = radius * Math.sin(phi) * Math.sin(theta);
                    double y = radius * Math.cos(phi);
                    player.spawnParticle(Particle.REDSTONE, center.clone().add(x, y + 0.5, z), 1, 0, 0, 0, 0, dust);
                }
                if (t == 4 && sounds) {
                    player.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    // в”Ђв”Ђ 5. TOTEM: СЌС„С„РµРєС‚ С‚РѕС‚РµРјР° в”Ђв”Ђ
    private static void totem(Plugin plugin, Player player, boolean sounds) {
        Location loc = player.getLocation();
        if (sounds) {
            player.playSound(loc, Sound.ITEM_TOTEM_USE, 1f, 1f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 2;
                if (t > 60) {
                    cancel();
                    return;
                }
                Location l = player.getLocation().add(0, 1 + Math.sin(t / 8.0) * 0.6, 0);
                double angle = t / 8.0;
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * Math.PI * 2 / 3;
                    player.spawnParticle(Particle.TOTEM, l.clone().add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9), 2, 0.02, 0.02, 0.02, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // в”Ђв”Ђ 6. HEARTS: РѕР±Р»Р°РєРѕ СЃРµСЂРґРµС† в”Ђв”Ђ
    private static void hearts(Plugin plugin, Player player, int ticks, boolean sounds) {
        if (sounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.1f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 3;
                if (t > ticks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                for (int i = 0; i < 2; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = 0.8 + RANDOM.nextDouble() * 0.7;
                    Location point = loc.clone().add(Math.cos(a) * r, 1.2 + RANDOM.nextDouble() * 0.8, Math.sin(a) * r);
                    player.spawnParticle(Particle.HEART, point, 1, 0, 0, 0, 0);
                }
                if (t % 30 == 0 && sounds) {
                    player.playSound(loc, Sound.ENTITY_VILLAGER_YES, 0.4f, 1.3f);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    // в”Ђв”Ђ 7. ENCHANT: РјР°РіРёС‡РµСЃРєРёР№ РІРёС…СЂСЊ С‡Р°СЂ в”Ђв”Ђ
    private static void enchant(Plugin plugin, Player player, int ticks, boolean sounds) {
        if (sounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 2;
                if (t > ticks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                double angle = t / 6.0;
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * Math.PI * 2 / 3;
                    Location point = loc.clone().add(Math.cos(a) * 1.3, 0.2 + (t % 40) / 40.0 * 2.0, Math.sin(a) * 1.3);
                    player.spawnParticle(Particle.ENCHANTMENT_TABLE, point, 4, 0.1, 0.1, 0.1, 0.5);
                }
                if (t % 20 == 0) {
                    player.spawnParticle(Particle.CRIT_MAGIC, loc.clone().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.3);
                    if (sounds) {
                        player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // в”Ђв”Ђ 8. EXPLOSION: Р±РµР·РѕРїР°СЃРЅС‹Р№ РІР·СЂС‹РІ СЃ РїРѕРґР±СЂРѕСЃРѕРј в”Ђв”Ђ
    private static void explosion(Plugin plugin, Player player, boolean sounds) {
        Location loc = player.getLocation();
        player.spawnParticle(Particle.EXPLOSION_HUGE, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        player.spawnParticle(Particle.EXPLOSION_NORMAL, loc.clone().add(0, 1, 0), 30, 0.3, 0.3, 0.3, 0.1);
        if (sounds) {
            player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1f);
        }
        Vector velocity = player.getLocation().getDirection();
        velocity.setY(0.9);
        player.setVelocity(velocity.multiply(0.4));
        // РєРѕРЅС„РµС‚С‚Рё РїРѕСЃР»Рµ РІР·СЂС‹РІР°
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 4;
                if (t > 40) {
                    cancel();
                    return;
                }
                Color c = Color.fromRGB(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256));
                player.spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1.5, 0), 12, 1.2, 0.6, 1.2, 0,
                        new Particle.DustOptions(c, 1.2f));
            }
        }.runTaskTimer(plugin, 6L, 4L);
    }

    // в”Ђв”Ђ 9. WATER: РІРѕРґСЏРЅРѕР№ РІРёС…СЂСЊ в”Ђв”Ђ
    private static void water(Plugin plugin, Player player, int ticks, boolean sounds) {
        if (sounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 1f);
            player.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 0.8f, 1.2f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 2;
                if (t > ticks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                double angle = t / 5.0;
                double y = (double) t / ticks * 2.2;
                double radius = 1.4 - y * 0.3;
                for (int i = 0; i < 2; i++) {
                    double a = angle + i * Math.PI;
                    Location point = loc.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                    player.spawnParticle(Particle.WATER_WAKE, point, 1, 0, 0, 0, 0);
                    player.spawnParticle(Particle.WATER_BUBBLE, point, 2, 0.05, 0.05, 0.05, 0.02);
                }
                if (t % 16 == 0) {
                    player.spawnParticle(Particle.WATER_SPLASH, loc.clone().add(0, 1, 0), 25, 0.7, 0.4, 0.7, 0.15);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // в”Ђв”Ђ 10. MYSTIC: РјРёСЃС‚РёС‡РµСЃРєРёР№ РїРѕСЂС‚Р°Р» в”Ђв”Ђ
    private static void mystic(Plugin plugin, Player player, int ticks, boolean sounds) {
        if (sounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.9f, 1.4f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 2;
                if (t > ticks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                // СЃС‚РѕР»Р± РїРѕСЂС‚Р°Р»Р°
                double y = (t % 40) / 40.0 * 3.0;
                double angle = t / 4.0;
                player.spawnParticle(Particle.PORTAL, loc.clone().add(Math.cos(angle) * 0.7, y, Math.sin(angle) * 0.7), 6, 0.1, 0.1, 0.1, 0.05);
                // Р»СѓС‡Рё РёР· End Rod
                if (t % 10 == 0) {
                    for (int i = 0; i < 4; i++) {
                        double a = angle + i * Math.PI / 2;
                        player.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2.6, 0), 1,
                                (float) (Math.cos(a) * 0.25), -0.6f, (float) (Math.sin(a) * 0.25), 0.01);
                    }
                }
                if (t == 20 && sounds) {
                    player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // в”Ђв”Ђ 11. LASER: Р»Р°Р·РµСЂРЅС‹Р№ Р»СѓС‡ в”Ђв”Ђ
    private static void laser(Plugin plugin, Player player, int ticks, boolean sounds) {
        if (sounds) {
            player.playSound(player.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1.8f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 4;
                if (t > ticks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                double phase = t / 12.0;
                // РІСЂР°С‰Р°СЋС‰РёРµСЃСЏ РІРµСЂС‚РёРєР°Р»СЊРЅС‹Рµ Р»СѓС‡Рё
                for (int i = 0; i < 3; i++) {
                    double angle = phase + i * Math.PI * 2 / 3;
                    double x = Math.cos(angle) * 1.4;
                    double z = Math.sin(angle) * 1.4;
                    for (int y = 0; y <= 8; y++) {
                        player.spawnParticle(Particle.REDSTONE,
                                loc.clone().add(x, 0.2 + y * 0.3, z), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(0, 255, 180), 1.3f));
                        player.spawnParticle(Particle.END_ROD,
                                loc.clone().add(x, 0.2 + y * 0.3, z), 1, 0, 0, 0, 0);
                    }
                }
                if (t % 24 == 0 && sounds) {
                    player.playSound(loc, Sound.ENTITY_GUARDIAN_ATTACK, 0.4f, 2f);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    // в”Ђв”Ђ 12. FAIRY: С„РµР№СЃРєР°СЏ РїС‹Р»СЊ в”Ђв”Ђ
    private static void fairy(Plugin plugin, Player player, int ticks, boolean sounds) {
        if (sounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 3;
                if (t > ticks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                // СЂРѕР№ РјРµСЂС†Р°СЋС‰РёС… РёСЃРєСЂ РІРѕРєСЂСѓРі РёРіСЂРѕРєР°
                for (int i = 0; i < 4; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2;
                    double radius = 0.6 + RANDOM.nextDouble() * 1.2;
                    double y = 0.3 + RANDOM.nextDouble() * 2.0;
                    Location point = loc.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    player.spawnParticle(Particle.FIREWORKS_SPARK, point, 1, 0.02, 0.02, 0.02, 0.01);
                    if (RANDOM.nextBoolean()) {
                        player.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                    }
                }
                Color c = Color.fromRGB(255, 200 + RANDOM.nextInt(56), 120);
                player.spawnParticle(Particle.REDSTONE, loc.clone().add(0, 1, 0), 6, 0.5, 0.6, 0.5, 0,
                        new Particle.DustOptions(c, 1.1f));
                if (t % 30 == 0 && sounds) {
                    player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 2.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    // в”Ђв”Ђ 13. DRAGON: РґС‹С…Р°РЅРёРµ РґСЂР°РєРѕРЅР° в”Ђв”Ђ
    private static void dragon(Plugin plugin, Player player, boolean sounds) {
        Location loc = player.getLocation();
        if (sounds) {
            player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 0.7f);
            player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.8f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 2;
                if (t > 60) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                // РєР»СѓР±С‹ РґСЂР°РєРѕРЅСЊРµРіРѕ РґС‹С…Р°РЅРёСЏ СЂР°СЃС€РёСЂСЏСЋС‰РёРјСЃСЏ РєРѕР»СЊС†РѕРј
                double radius = 1.0 + t / 30.0;
                double height = 0.5 + Math.sin(t / 10.0) * 0.4;
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12 + t / 14.0;
                    Location point = base.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                    player.spawnParticle(Particle.DRAGON_BREATH, point, 2, 0.05, 0.1, 0.05, 0.01);
                }
                if (t % 20 == 0) {
                    player.spawnParticle(Particle.PORTAL, base.clone().add(0, 1.4, 0), 30, 0.3, 0.4, 0.3, 0.2);
                    if (sounds) {
                        player.playSound(base, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.6f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // в”Ђв”Ђ 14. PULSAR: РїСѓР»СЊСЃР°СЂ вЂ” СЂР°СЃС€РёСЂСЏСЋС‰РёРµСЃСЏ РєРѕР»СЊС†Р° в”Ђв”Ђ
    private static void pulsar(Plugin plugin, Player player, int ticks, boolean sounds) {
        if (sounds) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                t += 3;
                if (t > ticks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                double progress = (double) t / ticks;
                double radius = 0.5 + (t % 30) / 30.0 * 3.0;
                Color from = Color.fromRGB(170, 0, 255);
                Color to = Color.fromRGB(0, 225, 255);
                Color c = lerpColor(from, to, progress);
                // РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅРѕРµ РєРѕР»СЊС†Рѕ
                for (int i = 0; i < 20; i++) {
                    double angle = Math.PI * 2 * i / 20;
                    Location point = loc.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
                    player.spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(c, 1.3f));
                }
                // РІРµСЂС‚РёРєР°Р»СЊРЅРѕРµ РєРѕР»СЊС†Рѕ (СЂРѕС‚РѕСЂ)
                for (int i = 0; i < 14; i++) {
                    double angle = Math.PI * 2 * i / 14 + t / 8.0;
                    Location point = loc.clone().add(
                            Math.cos(angle) * radius * 0.8,
                            1.0 + Math.sin(angle) * radius * 0.8,
                            0);
                    player.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                }
                if (t % 30 == 6 && sounds) {
                    player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f + (float) progress);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    // в”Ђв”Ђ helpers в”Ђв”Ђ

    private static void loop(Plugin plugin, Player player, int repeats, long period, int startDelay, boolean sounds, Runnable action) {
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                count++;
                action.run();
                if (count >= repeats) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, startDelay, period);
    }

    private static Color lerpColor(Color a, Color b, double t) {
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
