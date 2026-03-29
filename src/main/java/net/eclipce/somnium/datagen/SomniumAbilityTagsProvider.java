package net.eclipce.somnium.datagen;

import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Data generation provider for ability type tags. Addon developers extend
 * this class to generate tag JSON files via the {@code runData} gradle task.
 *
 * <h3>What this does</h3>
 * <p>When {@code runData} executes, this provider generates JSON files at:
 * {@code data/<namespace>/tags/somnium/ability_types/<tagpath>.json}.
 * These files define which abilities belong to which tags (conflict categories,
 * groupings, etc.).</p>
 *
 * <h3>Auto-generation from intrinsic tags</h3>
 * <p>Abilities that declare conflict tags via
 * {@link AbilityType.Properties#conflictTag(ResourceLocation)} have those
 * tags automatically generated. You don't need to manually add them —
 * just call {@code super.addTags(provider)} in your implementation and
 * they'll be included.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * public class ModAbilityTagsProvider extends SomniumAbilityTagsProvider {
 *
 *     public ModAbilityTagsProvider(PackOutput output,
 *                                    CompletableFuture<HolderLookup.Provider> lookupProvider,
 *                                    ExistingFileHelper fileHelper) {
 *         super(output, lookupProvider, "your_mod_id", fileHelper);
 *     }
 *
 *     @Override
 *     protected void addTags(HolderLookup.Provider provider) {
 *         // IMPORTANT: call super to auto-generate intrinsic conflict tags
 *         super.addTags(provider);
 *
 *         // Add your own tag assignments
 *         tag(SomniumAbilityTags.MOVEMENT)
 *             .add(ModAbilities.FLIGHT.get())
 *             .add(ModAbilities.DASH.get());
 *
 *         tag(SomniumAbilityTags.OFFENSIVE)
 *             .add(ModAbilities.FIRE_BLAST.get());
 *
 *         // Add to your own custom tags
 *         tag(ModAbilityTags.FIRE)
 *             .add(ModAbilities.FIRE_BLAST.get())
 *             .add(ModAbilities.FIRE_SHIELD.get());
 *     }
 * }
 * }</pre>
 *
 * <p>Then register it in your {@code GatherDataEvent} handler:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public static void gatherData(GatherDataEvent event) {
 *     DataGenerator gen = event.getGenerator();
 *     gen.addProvider(event.includeServer(),
 *         (DataProvider.Factory<ModAbilityTagsProvider>) output ->
 *             new ModAbilityTagsProvider(output,
 *                 event.getLookupProvider(),
 *                 event.getExistingFileHelper())
 *     );
 * }
 * }</pre>
 *
 * @see net.eclipce.somnium.core.tag.SomniumAbilityTags
 * @see SomniumRegistries#isAbilityInTag(AbilityType, TagKey)
 */
public abstract class SomniumAbilityTagsProvider extends IntrinsicHolderTagsProvider<AbilityType> {

    /**
     * Creates a new ability tags provider.
     *
     * @param output         the pack output from the data generator
     * @param lookupProvider the registry lookup provider
     * @param modId          your mod's ID (tags are generated under your namespace)
     * @param fileHelper     the existing file helper for validation
     */
    public SomniumAbilityTagsProvider(PackOutput output,
                                      CompletableFuture<HolderLookup.Provider> lookupProvider,
                                      String modId,
                                      @Nullable ExistingFileHelper fileHelper) {
        super(
                output,
                SomniumRegistries.ABILITY_TYPES.getRegistryKey(),
                lookupProvider,
                abilityType -> SomniumRegistries.getAbilityTypeRegistry()
                        .getResourceKey(abilityType)
                        .orElseThrow(() -> new IllegalStateException(
                                "Unregistered AbilityType passed to tag provider")),
                modId,
                fileHelper
        );
    }

    /**
     * Called during data generation to define tags. The base implementation
     * auto-generates tag entries from abilities' intrinsic conflict tags
     * (those declared via {@link AbilityType.Properties#conflictTag}).
     *
     * <p><strong>Subclasses must call {@code super.addTags(provider)}</strong>
     * to include the auto-generated intrinsic tags.</p>
     *
     * @param provider the holder lookup provider
     */
    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Auto-generate tags from abilities' intrinsic conflict tag declarations.
        // For each registered ability, check what conflict tags it declared in
        // its Properties, and add it to those tags automatically.
        var registry = SomniumRegistries.getAbilityTypeRegistry();
        if (registry == null) return;

        for (AbilityType abilityType : registry) {
            for (ResourceLocation conflictTagId : abilityType.getConflictTags()) {
                TagKey<AbilityType> tagKey = SomniumRegistries.ABILITY_TYPES
                        .createTagKey(conflictTagId);
                tag(tagKey).add(abilityType);
            }
        }
    }
}