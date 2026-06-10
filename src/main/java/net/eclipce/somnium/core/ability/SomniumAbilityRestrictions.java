package net.eclipce.somnium.core.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side, per-player temporary action restrictions used by abilities — currently
 * "disable punching" and "disable movement". Each restriction is armed for a number of
 * ticks and auto-expires, so a forgotten disarm can never leave a player permanently
 * stuck. Abilities arm them on activation (and may re-arm each tick for hold/charge
 * abilities); enforcement happens in the event handlers below.
 *
 * <h3>Why duration-based rather than a hard on/off flag</h3>
 * <p>Abilities already know how long their effect lasts (the same tick counts they pass
 * for animation length). Arming "for N ticks" means the restriction self-clears even if
 * the player disconnects mid-ability or an end-callback is missed — no leak, no stuck
 * player. Hold/charged abilities that don't know their end in advance simply re-arm for a
 * short window every tick in their tick callback; the moment they stop re-arming, the
 * window lapses and the player is freed.</p>
 *
 * <h3>What each restriction does</h3>
 * <ul>
 *   <li><b>Punching</b> — cancels the player's melee attacks (both the attack-entity event
 *       and the broader living-attack path where the player is the source). The player can
 *       still look around and (unless also movement-locked) move.</li>
 *   <li><b>Movement</b> — zeroes horizontal movement each tick while leaving the camera
 *       free, so the player is rooted in place but can still aim. Vertical velocity (gravity
 *       / fall) is left alone so the player isn't frozen in the air.</li>
 * </ul>
 *
 * <p>Register the handler on the Forge event bus once during common setup:
 * {@code MinecraftForge.EVENT_BUS.register(SomniumAbilityRestrictions.class);} — or rely on
 * the {@code @Mod.EventBusSubscriber} annotation if the modid matches your setup.</p>
 */
@Mod.EventBusSubscriber(modid = "somnium")
public final class SomniumAbilityRestrictions {

    /** Game-tick (level time) through which punching is disabled, per player UUID. */
    private static final Map<UUID, Long> noPunchUntil = new ConcurrentHashMap<>();
    /** Game-tick (level time) through which movement is disabled, per player UUID. */
    private static final Map<UUID, Long> noMoveUntil  = new ConcurrentHashMap<>();

    // ─── Arming API (called from ability code, server-side) ─────────────────────

    /**
     * Disables the player's punching for {@code durationTicks} ticks from now. Re-arming
     * with a later expiry extends the window; arming with a shorter one does not cut an
     * existing longer window short (we keep the max), so overlapping abilities compose
     * sanely.
     */
    public static void disablePunching(ServerPlayer player, int durationTicks) {
        if (durationTicks <= 0) return;
        long until = player.level().getGameTime() + durationTicks;
        noPunchUntil.merge(player.getUUID(), until, Math::max);
    }

    /**
     * Disables the player's movement for {@code durationTicks} ticks from now. See
     * {@link #disablePunching} for the extend-don't-shorten semantics.
     */
    public static void disableMovement(ServerPlayer player, int durationTicks) {
        if (durationTicks <= 0) return;
        long until = player.level().getGameTime() + durationTicks;
        noMoveUntil.merge(player.getUUID(), until, Math::max);
    }

    /** Immediately clears both restrictions for a player (e.g. on ability cancel). */
    public static void clear(UUID uuid) {
        noPunchUntil.remove(uuid);
        noMoveUntil.remove(uuid);
    }

    // ─── Queries ────────────────────────────────────────────────────────────────

    public static boolean isPunchingDisabled(Player player) {
        Long until = noPunchUntil.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    public static boolean isMovementDisabled(Player player) {
        Long until = noMoveUntil.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    // ─── Enforcement ──────────────────────────────────────────────────────────

    /**
     * Cancels the player's melee attack on another entity while punching is disabled.
     * AttackEntityEvent fires server-side when a player left-clicks an entity.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (isPunchingDisabled(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Backstop: cancel any damage whose direct source is a player whose punching is
     * disabled. Catches attack paths that don't route through AttackEntityEvent.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker
                && isPunchingDisabled(attacker)) {
            event.setCanceled(true);
        }
    }

    /**
     * Zeroes horizontal movement each server tick while movement is disabled. Camera/look
     * is untouched (that's a separate input path), and vertical velocity is preserved so
     * gravity and fall still behave. Also expires lapsed entries to keep the maps small.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        long now = player.level().getGameTime();

        // Opportunistic cleanup of expired entries (cheap, runs per player tick).
        Long punchUntil = noPunchUntil.get(player.getUUID());
        if (punchUntil != null && now >= punchUntil) noPunchUntil.remove(player.getUUID());

        Long moveUntil = noMoveUntil.get(player.getUUID());
        if (moveUntil != null && now < moveUntil) {
            net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
            // Keep vertical (gravity / jumps already in progress), zero horizontal.
            player.setDeltaMovement(0.0, v.y, 0.0);
            player.hurtMarked = true; // force velocity sync to the client
        } else if (moveUntil != null && now >= moveUntil) {
            noMoveUntil.remove(player.getUUID());
        }
    }

    private SomniumAbilityRestrictions() {}
}