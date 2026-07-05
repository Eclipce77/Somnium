package net.eclipce.somnium.core.meter;

/**
 * Defines how an ability interacts with a meter (stamina or custom).
 * Supports drain, add, multiply, and divide operations, plus a minimum
 * required value to activate the ability.
 */
public class MeterCost {

    public enum Operation {
        /** Subtract a flat amount from the meter. */
        DRAIN,
        /** Add a flat amount to the meter. */
        ADD,
        /** Multiply the meter's current value. */
        MULTIPLY,
        /** Divide the meter's current value. */
        DIVIDE
    }

    private final Operation operation;
    private final float amount;
    private final float requiredMinimum;
    private final boolean overuseExempt;

    /**
     * Creates a simple drain cost.
     *
     * @param drainAmount  the amount to drain on activation
     * @param requiredMin  minimum meter value needed to activate (0 = no requirement)
     */
    public MeterCost(float drainAmount, float requiredMin) {
        this(Operation.DRAIN, drainAmount, requiredMin, false);
    }

    /**
     * Creates a meter cost with a specific operation.
     *
     * @param operation   the operation to perform
     * @param amount      the amount/factor for the operation
     * @param requiredMin minimum meter value needed to activate (0 = no requirement)
     */
    public MeterCost(Operation operation, float amount, float requiredMin) {
        this(operation, amount, requiredMin, false);
    }

    /**
     * Creates a meter cost with full control.
     *
     * @param operation     the operation to perform
     * @param amount        the amount/factor for the operation
     * @param requiredMin   minimum meter value needed to activate (0 = no requirement)
     * @param overuseExempt if true, stamina drain won't trigger overuse processing
     */
    public MeterCost(Operation operation, float amount, float requiredMin,
                     boolean overuseExempt) {
        this.operation = operation;
        this.amount = amount;
        this.requiredMinimum = requiredMin;
        this.overuseExempt = overuseExempt;
    }

    /** @return the operation type */
    public Operation getOperation() { return operation; }

    /** @return the amount/factor */
    public float getAmount() { return amount; }

    /** @return the minimum meter value required to activate */
    public float getRequiredMinimum() { return requiredMinimum; }

    /**
     * @return true if this cost should not trigger overuse when applied
     *         to stamina. Only relevant for stamina costs.
     */
    public boolean isOveruseExempt() { return overuseExempt; }

    /**
     * The drain amount after a per-player multiplier (see
     * {@link net.eclipce.somnium.event.AbilityMeterCostEvent}). Only DRAIN
     * costs scale — for all other operations the multiplier is ignored.
     */
    public float scaledAmount(float multiplier) {
        return operation == Operation.DRAIN ? amount * multiplier : amount;
    }

    /**
     * The required minimum after a per-player multiplier. Scales with the same
     * multiplier as the amount so the activation gate and the actual drain
     * always agree. Only DRAIN costs scale.
     */
    public float scaledRequiredMinimum(float multiplier) {
        return operation == Operation.DRAIN ? requiredMinimum * multiplier : requiredMinimum;
    }

    /**
     * Checks if the meter has enough value to activate.
     */
    public boolean canActivate(MeterInstance meter) {
        return canActivate(meter, 1.0f);
    }

    /**
     * Checks if the meter has enough value to activate, with a per-player cost
     * multiplier applied to the required minimum (DRAIN costs only).
     */
    public boolean canActivate(MeterInstance meter, float multiplier) {
        float required = scaledRequiredMinimum(multiplier);
        if (required > 0 && meter.getValue() < required) {
            return false;
        }
        return true;
    }

    /**
     * Applies this cost to the meter. Call after activation succeeds.
     */
    public void apply(MeterInstance meter) {
        apply(meter, 1.0f);
    }

    /**
     * Applies this cost to the meter with a per-player cost multiplier
     * (DRAIN costs only — other operations apply their raw amount).
     */
    public void apply(MeterInstance meter, float multiplier) {
        switch (operation) {
            case DRAIN -> meter.drain(scaledAmount(multiplier));
            case ADD -> meter.add(amount);
            case MULTIPLY -> meter.multiply(amount);
            case DIVIDE -> {
                if (amount != 0) meter.multiply(1.0f / amount);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Convenience factories
    // ═══════════════════════════════════════════════════════════════════

    /** Drains amount, no minimum required. */
    public static MeterCost drain(float amount) {
        return new MeterCost(Operation.DRAIN, amount, 0);
    }

    /** Drains amount, requires minimum to activate. */
    public static MeterCost drain(float amount, float required) {
        return new MeterCost(Operation.DRAIN, amount, required);
    }

    /** Adds amount to the meter. */
    public static MeterCost add(float amount) {
        return new MeterCost(Operation.ADD, amount, 0);
    }

    /** Multiplies the meter value. */
    public static MeterCost multiply(float factor) {
        return new MeterCost(Operation.MULTIPLY, factor, 0);
    }

    /** Divides the meter value. */
    public static MeterCost divide(float factor) {
        return new MeterCost(Operation.DIVIDE, factor, 0);
    }

    /**
     * Drains amount, but won't trigger overuse if applied to stamina.
     * Use for utility/defensive abilities.
     */
    public static MeterCost drainExempt(float amount) {
        return new MeterCost(Operation.DRAIN, amount, 0, true);
    }

    /**
     * Drains amount with minimum, won't trigger overuse.
     */
    public static MeterCost drainExempt(float amount, float required) {
        return new MeterCost(Operation.DRAIN, amount, required, true);
    }
}