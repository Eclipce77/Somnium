package net.eclipce.somnium.compat.geckolib.player.cast;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code assets/<namespace>/bone_hierarchy/<path>.json} from every loaded mod's
 * resources — the on-disk source for {@link BoneHierarchy} data, hot-reloadable the same way
 * any other JSON asset is (F3+T in-game, or automatically on resource-pack changes).
 *
 * <h3>File format</h3>
 * <pre>{@code
 * {
 *   "parents": {
 *     "head": "body",
 *     "hat": "head",
 *     "right_arm": "body",
 *     "right_sleeve": "right_arm",
 *     "left_arm": "body",
 *     "left_sleeve": "left_arm",
 *     "right_leg": "body",
 *     "right_pants": "right_leg",
 *     "left_leg": "body",
 *     "left_pants": "left_leg"
 *   }
 * }
 * }</pre>
 * Each entry maps a child bone name to its parent's name. A bone absent from {@code parents}
 * is a root (has no parent) — an empty or absent {@code parents} object means every bone is a
 * root, i.e. fully flat, identical to not specifying a hierarchy at all.
 *
 * <p>A file at {@code assets/romancedawn/bone_hierarchy/gear_second.json} is loaded under the
 * key {@code romancedawn:gear_second} — reference that key from
 * {@link CastAnimationOptions.Builder#boneHierarchy(ResourceLocation)} to opt a triggered
 * animation into it. This registry knows nothing about which mod defines a file; any mod's
 * {@code assets} folder is picked up automatically, the same as vanilla's own
 * {@code SimpleJsonResourceReloadListener}-based registries (recipes, loot tables, etc.).</p>
 *
 * <h3>Registration</h3>
 * Registered once, client-side only, in {@code SomniumClientEvents.ModBusEvents} via
 * {@code RegisterClientReloadListenersEvent} — see that class. Addon/content mods don't need
 * to register anything themselves; dropping a JSON file in the right folder is sufficient.
 */
public final class BoneHierarchyRegistry extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String FOLDER = "bone_hierarchy";

    private static volatile Map<ResourceLocation, BoneHierarchy> loaded = Map.of();

    public BoneHierarchyRegistry() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, BoneHierarchy> result = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                Map<String, String> parentOf = new HashMap<>();
                JsonObject root = entry.getValue().getAsJsonObject();
                JsonObject parentsJson = root.has("parents") ? root.getAsJsonObject("parents") : null;
                if (parentsJson != null) {
                    for (Map.Entry<String, JsonElement> p : parentsJson.entrySet()) {
                        parentOf.put(p.getKey(), p.getValue().getAsString());
                    }
                }
                result.put(id, BoneHierarchy.of(parentOf));
            } catch (Exception e) {
                // One malformed file shouldn't take every hierarchy down with it — skip it
                // (animations referencing it fall back to FLAT, same as an unset/missing key)
                // and keep loading the rest.
                LOGGER.error("[Somnium] Failed to parse bone hierarchy '{}' — animations referencing it will render flat (uncompounded)", id, e);
            }
        }

        loaded = Map.copyOf(result);
        LOGGER.debug("[Somnium] Loaded {} bone hierarchy definition(s)", loaded.size());
    }

    /**
     * @param id a key like {@code romancedawn:gear_second}, or {@code null}
     * @return the resolved hierarchy, or {@code null} if {@code id} is null or no file with
     *         that key was loaded (callers should treat a null return as {@link BoneHierarchy#FLAT}).
     */
    @Nullable
    public static BoneHierarchy get(@Nullable ResourceLocation id) {
        if (id == null) return null;
        return loaded.get(id);
    }
}
