package net.eclipce.somnium.core.prerequisite;

import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Prerequisite: player must have a specific tag assigned.
 *
 * <p>Tags are stored per-player in {@link SomniumPlayerData} and can be
 * granted/revoked through the tag system or commands.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Ability only visible if the player has the "fire_user" tag
 * Power.builder()
 *     .ability(() -> FIRE_MASTERY.get(),
 *             new HasTagPrerequisite(new ResourceLocation("mymod", "fire_user")))
 *     .build();
 * }</pre>
 */
public class HasTagPrerequisite implements Prerequisite {

    private final ResourceLocation tag;

    /**
     * @param tag the tag the player must have
     */
    public HasTagPrerequisite(ResourceLocation tag) {
        this.tag = tag;
    }

    @Override
    public boolean isMet(SomniumPlayerData data) {
        return data.hasTag(tag);
    }

    @Override
    public Component getDescription() {
        return Component.literal("Requires tag: " + tag);
    }

    /** @return the required tag */
    public ResourceLocation getTag() {
        return tag;
    }
}