package net.eclipce.somnium.core.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks XP and level progress for individual powers.
 *
 * <p>Each power has its own XP pool and level. Level is calculated
 * from accumulated XP using a configurable curve. Abilities can
 * require a minimum power level via {@code PowerLevelCondition}.</p>
 *
 * <h3>XP Curve</h3>
 * <p>Default: {@code xpForLevel = BASE_XP * (level ^ 1.5)}</p>
 * <ul>
 *     <li>Level 1: 100 XP</li>
 *     <li>Level 5: 1118 XP</li>
 *     <li>Level 10: 3162 XP</li>
 *     <li>Level 20: 8944 XP</li>
 * </ul>
 *
 * <h3>Addon dev usage</h3>
 * <pre>{@code
 * // In ability Properties:
 * new Properties().powerXP(5)  // grants 5 power XP on use
 *
 * // In Power.builder:
 * .ability(() -> METEOR.get(), new PowerLevelCondition(10))
 *
 * // Query level:
 * int level = data.getPowerProgress().getLevel(powerKey);
 * }</pre>
 */
public class PowerProgress {

    /** Base XP for level 1. */
    public static final double BASE_XP = 100.0;

    /** Exponent for XP curve. */
    public static final double XP_EXPONENT = 1.5;

    /**
     * Per-power XP accumulation. Key = power registry key.
     */
    private final Map<ResourceLocation, Double> powerXP = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /** Gets accumulated XP for a power. */
    public double getXP(ResourceLocation powerKey) {
        return powerXP.getOrDefault(powerKey, 0.0);
    }

    /** Gets the current level for a power (derived from XP). */
    public int getLevel(ResourceLocation powerKey) {
        return xpToLevel(getXP(powerKey));
    }

    /** Gets XP required to reach the next level. */
    public double getXPForNextLevel(ResourceLocation powerKey) {
        int currentLevel = getLevel(powerKey);
        return levelToXP(currentLevel + 1);
    }

    /** Gets XP progress within the current level (0.0 to 1.0). */
    public double getLevelProgress(ResourceLocation powerKey) {
        double xp = getXP(powerKey);
        int level = xpToLevel(xp);
        double currentThreshold = levelToXP(level);
        double nextThreshold = levelToXP(level + 1);
        double range = nextThreshold - currentThreshold;
        if (range <= 0) return 0;
        return (xp - currentThreshold) / range;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  XP manipulation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Adds XP to a power.
     *
     * @param powerKey the power's registry key
     * @param amount   XP to add
     * @return the new total XP
     */
    public double addXP(ResourceLocation powerKey, double amount) {
        double current = getXP(powerKey);
        double newXP = current + amount;
        powerXP.put(powerKey, newXP);
        return newXP;
    }

    /** Sets XP for a power directly. */
    public void setXP(ResourceLocation powerKey, double xp) {
        powerXP.put(powerKey, Math.max(0, xp));
    }

    /** Sets level for a power (sets XP to that level's threshold). */
    public void setLevel(ResourceLocation powerKey, int level) {
        powerXP.put(powerKey, levelToXP(Math.max(0, level)));
    }

    /** Resets a power's XP to 0. */
    public void resetPower(ResourceLocation powerKey) {
        powerXP.remove(powerKey);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  XP curve
    // ═══════════════════════════════════════════════════════════════════

    /** Converts a level to the total XP required to reach it. */
    public static double levelToXP(int level) {
        if (level <= 0) return 0;
        return BASE_XP * Math.pow(level, XP_EXPONENT);
    }

    /** Converts total XP to a level (floor). */
    public static int xpToLevel(double xp) {
        if (xp <= 0) return 0;
        return (int) Math.floor(Math.pow(xp / BASE_XP, 1.0 / XP_EXPONENT));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Serialization
    // ═══════════════════════════════════════════════════════════════════

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Double> entry : powerXP.entrySet()) {
            tag.putDouble(entry.getKey().toString(), entry.getValue());
        }
        return tag;
    }

    public static PowerProgress deserialize(CompoundTag tag) {
        PowerProgress progress = new PowerProgress();
        for (String key : tag.getAllKeys()) {
            progress.powerXP.put(new ResourceLocation(key), tag.getDouble(key));
        }
        return progress;
    }

    public void copyFrom(PowerProgress source) {
        this.powerXP.clear();
        this.powerXP.putAll(source.powerXP);
    }
}