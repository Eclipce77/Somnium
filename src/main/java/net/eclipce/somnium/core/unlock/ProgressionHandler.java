package net.eclipce.somnium.core.unlock;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.core.unlock.conditions.AbilityUsageCondition;
import net.eclipce.somnium.core.unlock.conditions.CompositeCondition;
import net.eclipce.somnium.core.unlock.conditions.KillCondition;
import net.eclipce.somnium.network.SomniumNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Central handler for the progression system. Subscribes to Forge events
 * and updates per-player progress toward locked abilities, unlocking them
 * when their conditions are met.
 *
 * <h3>How it works</h3>
 * <p>When a relevant game event occurs (entity killed, ability activated),
 * this handler iterates through the player's granted powers, finds locked
 * abilities with matching conditions, updates their progress, and checks
 * if any conditions are now met. Newly met conditions trigger an automatic
 * unlock via {@link SomniumPlayerData#unlockAbility}.</p>
 *
 * <h3>Event routing</h3>
 * <ul>
 *     <li>{@link LivingDeathEvent} → {@link KillCondition} progress</li>
 *     <li>{@link #onAbilityActivated} (called from activation code) →
 *         {@link AbilityUsageCondition} progress</li>
 * </ul>
 *
 * <p>The handler automatically handles {@link CompositeCondition} by
 * recursively updating child conditions of the appropriate type.</p>
 *
 * @see UnlockCondition
 */
@Mod.EventBusSubscriber(modid = Somnium.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProgressionHandler {

    // ═══════════════════════════════════════════════════════════════════
    //  Ability activation tracking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when an ability is successfully activated on the server.
     * Updates usage-based unlock conditions for all locked abilities
     * in the player's granted powers.
     *
     * <p>This should be called from the ability activation flow (e.g.,
     * {@code ActivateAbilityPacket} handler) after a successful activation.
     * It is NOT an event subscriber — it's called directly.</p>
     *
     * @param player      the player who activated the ability
     * @param abilityType the ability that was activated
     */
    public static void onAbilityActivated(ServerPlayer player, AbilityType abilityType) {
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) return;

        // Grant composition growth for ability usage (unless opted out)
        if (!abilityType.isCompositionOptOut()) {
            net.eclipce.somnium.core.composition.CompositionData comp = data.getComposition();
            double oldValue = comp.getValue();
            double gained = comp.addGrowth(
                    net.eclipce.somnium.core.composition.CompositionData.GAIN_ABILITY_USE,
                    net.eclipce.somnium.core.composition.CompositionSource.ABILITY_USE);
            if (gained > 0) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new net.eclipce.somnium.event.SomniumCompositionEvent(
                                player, oldValue, comp.getValue(), gained,
                                net.eclipce.somnium.core.composition.CompositionSource.ABILITY_USE));
            }
        }

        ResourceLocation usedKey = SomniumRegistries.getAbilityKey(abilityType);
        if (usedKey == null) return;

        boolean changed = false;

        for (Power power : data.getGrantedPowers()) {
            if (!power.isProgressionEnabled()) continue;
            ResourceLocation powerKey = SomniumRegistries.getPowerKey(power);
            if (powerKey == null) continue;

            for (Power.PowerAbilityEntry entry : power.getEntries()) {
                AbilityType lockedAbility = entry.getAbilityType();
                if (lockedAbility == null) continue;
                if (data.isAbilityUnlocked(lockedAbility)) continue;

                UnlockCondition condition = entry.getUnlockCondition();
                if (condition == null) continue;

                String progressKey = makeProgressKey(powerKey, lockedAbility);
                CompoundTag progress = data.getUnlockProgress(progressKey);
                if (progress == null) continue;

                if (updateUsageCondition(condition, progress, usedKey)) {
                    changed = true;
                    data.setUnlockProgress(progressKey, progress);

                    if (condition.isMet(progress)) {
                        data.unlockAbility(lockedAbility, power);
                        data.removeUnlockProgress(progressKey);
                    }
                }
            }
        }

        if (changed) {
            SomniumNetwork.syncToClient(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Kill tracking (Forge event)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handles entity kills. Updates kill-based unlock conditions for
     * the attacking player (if the attacker is a player).
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // ── Death penalty: composition loss ──
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            SomniumPlayerData deadData = SomniumCapability.get(deadPlayer);
            if (deadData != null) {
                // Check gamerule (default: composition loss on death is enabled)
                boolean lossEnabled = deadPlayer.level().getGameRules()
                        .getBoolean(net.eclipce.somnium.SomniumGameRules.COMPOSITION_LOSS_ON_DEATH);
                if (lossEnabled) {
                    net.eclipce.somnium.core.composition.CompositionData comp =
                            deadData.getComposition();
                    double lost = comp.applyDeathPenalty(
                            net.eclipce.somnium.core.composition.CompositionData.DEFAULT_DEATH_LOSS_PERCENT,
                            net.eclipce.somnium.core.composition.CompositionData.DEFAULT_DEATH_LOSS_CAP);
                    if (lost > 0) {
                        Somnium.LOGGER.debug("Player {} lost {} composition on death",
                                deadPlayer.getName().getString(), String.format("%.1f", lost));
                    }
                }
            }
        }

        // ── Kill tracking for progression ──
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) return;

        boolean changed = false;

        for (Power power : data.getGrantedPowers()) {
            if (!power.isProgressionEnabled()) continue;
            ResourceLocation powerKey = SomniumRegistries.getPowerKey(power);
            if (powerKey == null) continue;

            for (Power.PowerAbilityEntry entry : power.getEntries()) {
                AbilityType lockedAbility = entry.getAbilityType();
                if (lockedAbility == null) continue;
                if (data.isAbilityUnlocked(lockedAbility)) continue;

                UnlockCondition condition = entry.getUnlockCondition();
                if (condition == null) continue;

                String progressKey = makeProgressKey(powerKey, lockedAbility);
                CompoundTag progress = data.getUnlockProgress(progressKey);
                if (progress == null) continue;

                if (updateKillCondition(condition, progress, player, data)) {
                    changed = true;
                    data.setUnlockProgress(progressKey, progress);

                    if (condition.isMet(progress)) {
                        data.unlockAbility(lockedAbility, power);
                        data.removeUnlockProgress(progressKey);
                    }
                }
            }
        }

        if (changed) {
            SomniumNetwork.syncToClient(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Condition update logic (recursive for CompositeCondition)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Recursively updates usage-based conditions. Returns true if any
     * progress was changed.
     */
    private static boolean updateUsageCondition(UnlockCondition condition,
                                                CompoundTag progress,
                                                ResourceLocation usedAbilityKey) {
        if (condition instanceof AbilityUsageCondition usage) {
            ResourceLocation targetKey = usage.getTargetAbilityKey();
            if (usedAbilityKey.equals(targetKey)) {
                usage.incrementCount(progress);
                return true;
            }
        } else if (condition instanceof CompositeCondition composite) {
            boolean anyChanged = false;
            for (int i = 0; i < composite.getChildren().size(); i++) {
                CompoundTag childProgress = composite.getChildProgress(progress, i);
                if (updateUsageCondition(composite.getChildren().get(i),
                        childProgress, usedAbilityKey)) {
                    // Write child progress back into composite tag
                    progress.put(String.valueOf(i), childProgress);
                    anyChanged = true;
                }
            }
            return anyChanged;
        }
        return false;
    }

    /**
     * Recursively updates kill-based conditions. Checks whether the
     * required active ability is currently active on the player.
     * Returns true if any progress was changed.
     */
    private static boolean updateKillCondition(UnlockCondition condition,
                                               CompoundTag progress,
                                               ServerPlayer player,
                                               SomniumPlayerData data) {
        if (condition instanceof KillCondition kill) {
            if (kill.hasAbilityRequirement()) {
                // Check if the required ability is currently active
                ResourceLocation requiredKey = kill.getRequiredActiveAbilityKey();
                if (requiredKey != null) {
                    AbilityInstance instance = data.getAbilityInstance(requiredKey);
                    if (instance == null || !instance.isActive()) {
                        return false;
                    }
                }
            }
            kill.incrementKills(progress);
            return true;
        } else if (condition instanceof CompositeCondition composite) {
            boolean anyChanged = false;
            for (int i = 0; i < composite.getChildren().size(); i++) {
                CompoundTag childProgress = composite.getChildProgress(progress, i);
                if (updateKillCondition(composite.getChildren().get(i),
                        childProgress, player, data)) {
                    progress.put(String.valueOf(i), childProgress);
                    anyChanged = true;
                }
            }
            return anyChanged;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Progress initialization (called from SomniumPlayerData.grantPower)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes progression tracking for a newly granted power.
     * Creates progress entries for abilities with conditions and
     * immediately unlocks any that are already met (retroactive check).
     *
     * <p>Called from {@link SomniumPlayerData#grantPower(Power)} when
     * the power has progression enabled.</p>
     *
     * @param data  the player's data
     * @param power the newly granted power
     */
    public static void initializeProgressForPower(SomniumPlayerData data, Power power) {
        ResourceLocation powerKey = SomniumRegistries.getPowerKey(power);
        if (powerKey == null) return;

        for (Power.PowerAbilityEntry entry : power.getEntries()) {
            AbilityType abilityType = entry.getAbilityType();
            if (abilityType == null) continue;

            UnlockCondition condition = entry.getUnlockCondition();

            // No condition or AlwaysUnlocked → unlock immediately
            if (condition == null) {
                data.unlockAbility(abilityType, power);
                continue;
            }

            CompoundTag initialProgress = condition.createInitialProgress();

            // Retroactive check: if condition is already met with fresh progress
            if (condition.isMet(initialProgress)) {
                data.unlockAbility(abilityType, power);
            } else {
                // Start tracking progress
                String progressKey = makeProgressKey(powerKey, abilityType);
                data.setUnlockProgress(progressKey, initialProgress);
            }
        }
    }

    /**
     * Cleans up progression data when a power is revoked.
     *
     * @param data  the player's data
     * @param power the revoked power
     */
    public static void removeProgressForPower(SomniumPlayerData data, Power power) {
        ResourceLocation powerKey = SomniumRegistries.getPowerKey(power);
        if (powerKey == null) return;

        for (Power.PowerAbilityEntry entry : power.getEntries()) {
            AbilityType abilityType = entry.getAbilityType();
            if (abilityType == null) continue;
            String progressKey = makeProgressKey(powerKey, abilityType);
            data.removeUnlockProgress(progressKey);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Progress key generation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a composite key for storing per-ability-per-power progress.
     * Format: "powerNamespace:powerPath|abilityNamespace:abilityPath"
     *
     * @param powerKey    the power's registry key
     * @param abilityType the ability type
     * @return the composite progress key, or null if the ability isn't registered
     */
    @Nullable
    public static String makeProgressKey(ResourceLocation powerKey, AbilityType abilityType) {
        ResourceLocation abilityKey = SomniumRegistries.getAbilityKey(abilityType);
        if (abilityKey == null) return null;
        return powerKey.toString() + "|" + abilityKey.toString();
    }

    // Private constructor — utility class with static event subscribers
    private ProgressionHandler() {}
}