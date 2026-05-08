package net.eclipce.somnium.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common (server-side) configuration. Controls gameplay values
 * like death penalties, composition gains, and overuse timing.
 */
public class SomniumCommonConfig {

    public static final ForgeConfigSpec SPEC;

    // ── Death Penalty ──
    public static final ForgeConfigSpec.DoubleValue DEATH_LOSS_PERCENT;
    public static final ForgeConfigSpec.DoubleValue DEATH_LOSS_CAP;

    // ── Composition Gains ──
    public static final ForgeConfigSpec.DoubleValue GAIN_ABILITY_USE;
    public static final ForgeConfigSpec.DoubleValue GAIN_STAMINA_RECOVERY;
    public static final ForgeConfigSpec.DoubleValue GAIN_OVERUSE_SURVIVAL;
    public static final ForgeConfigSpec.DoubleValue DIMINISHING_DIVISOR;

    // ── Overuse Timing ──
    public static final ForgeConfigSpec.IntValue OVERUSE_WINDOW_TICKS;
    public static final ForgeConfigSpec.IntValue GRACE_HALT_TICKS;
    public static final ForgeConfigSpec.DoubleValue LARGE_DRAIN_THRESHOLD;

    // ── Health Scaling ──
    public static final ForgeConfigSpec.IntValue HEALTH_PER_COMPOSITION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Death Penalty Settings").push("death_penalty");
        DEATH_LOSS_PERCENT = builder
                .comment("Percentage of composition lost on death (0.0 - 1.0)")
                .defineInRange("lossPercent", 0.05, 0.0, 1.0);
        DEATH_LOSS_CAP = builder
                .comment("Maximum composition points lost on death")
                .defineInRange("lossCap", 50.0, 0.0, 10000.0);
        builder.pop();

        builder.comment("Composition Growth Rates").push("composition");
        GAIN_ABILITY_USE = builder
                .comment("Base composition gained per ability activation")
                .defineInRange("gainAbilityUse", 0.05, 0.0, 100.0);
        GAIN_STAMINA_RECOVERY = builder
                .comment("Base composition gained per full stamina recovery cycle")
                .defineInRange("gainStaminaRecovery", 0.5, 0.0, 100.0);
        GAIN_OVERUSE_SURVIVAL = builder
                .comment("Base composition gained per overuse stage survived")
                .defineInRange("gainOveruseSurvival", 5.0, 0.0, 100.0);
        DIMINISHING_DIVISOR = builder
                .comment("Controls diminishing returns curve. At this composition value, gains are 50% of base.")
                .defineInRange("diminishingDivisor", 1000.0, 100.0, 100000.0);
        HEALTH_PER_COMPOSITION = builder
                .comment("Composition needed for +1 max health (0.5 hearts). 0 = disabled.")
                .defineInRange("healthPerComposition", 1000, 0, 100000);
        builder.pop();

        builder.comment("Overuse Timing").push("overuse");
        OVERUSE_WINDOW_TICKS = builder
                .comment("Duration of the overuse window in ticks (12000 = 10 minutes)")
                .defineInRange("windowTicks", 12000, 1200, 72000);
        GRACE_HALT_TICKS = builder
                .comment("Regen halt duration during grace period in ticks (100 = 5 seconds)")
                .defineInRange("graceHaltTicks", 100, 20, 6000);
        LARGE_DRAIN_THRESHOLD = builder
                .comment("Drain percentage of max stamina that skips grace (0.15 = 15%)")
                .defineInRange("largeDrainThreshold", 0.15, 0.01, 1.0);
        builder.pop();

        SPEC = builder.build();
    }
}