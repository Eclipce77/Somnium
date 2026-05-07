package net.eclipce.somnium.core.composition;

/**
 * Sources of composition growth. Passed to events so addon devs
 * can filter or react to specific growth types.
 */
public enum CompositionSource {

    /** Growth from activating any Somnium ability. */
    ABILITY_USE,

    /** Growth from completing a full stamina recovery cycle (0→max). */
    STAMINA_RECOVERY,

    /** Growth from surviving an overuse cycle. */
    OVERUSE_SURVIVAL,

    /** Growth from addon-defined milestones. */
    MILESTONE,

    /** Growth from commands or direct API calls. */
    COMMAND,

    /** Growth from a custom addon-defined source. */
    CUSTOM
}