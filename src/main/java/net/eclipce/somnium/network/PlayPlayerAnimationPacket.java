package net.eclipce.somnium.network;

import net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client packet that triggers a GeckoLib cast animation on a player, optionally
 * with per-cast {@link CastAnimationOptions}.
 *
 * <p>Sent via {@link net.eclipce.somnium.compat.geckolib.animation.SomniumAnimHelper#triggerCastAnimation},
 * which broadcasts to all players tracking the target (including themselves). On arrival,
 * the client-side {@link SomniumCastAnimatable} stores the animation, model, and options
 * by UUID. The next render frame the Mixin picks them up and applies bone transforms onto
 * the vanilla player model additively (modulated by the options).</p>
 *
 * <h3>Wire format</h3>
 * <p>UUID, animation name (UTF), model registry key (UTF), then the serialized
 * {@link CastAnimationOptions}. Always include options; pass {@link CastAnimationOptions#DEFAULT}
 * when no overrides are needed.</p>
 */
public class PlayPlayerAnimationPacket {

    private final UUID playerUuid;
    /** Animation name as declared in the animation.json — e.g. "animation.mymod.cast" */
    private final String animationName;
    /** Registry key of the SomniumCastModel to use — e.g. "mymod:fire_cast" */
    private final String modelId;
    /** Per-cast behaviour options. Never {@code null}. */
    private final CastAnimationOptions options;

    /**
     * Full constructor.
     *
     * @param options never {@code null}; pass {@link CastAnimationOptions#DEFAULT} for no overrides
     */
    public PlayPlayerAnimationPacket(UUID playerUuid, String animationName, String modelId, CastAnimationOptions options) {
        this.playerUuid = playerUuid;
        this.animationName = animationName;
        this.modelId = modelId;
        this.options = options != null ? options : CastAnimationOptions.DEFAULT;
    }

    /**
     * Back-compatible constructor that defaults options to {@link CastAnimationOptions#DEFAULT}.
     * Existing call sites continue to compile and behave identically to before the options API.
     */
    public PlayPlayerAnimationPacket(UUID playerUuid, String animationName, String modelId) {
        this(playerUuid, animationName, modelId, CastAnimationOptions.DEFAULT);
    }

    // ─── Codec ───────────────────────────────────────────────────────────────

    public static PlayPlayerAnimationPacket decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String anim = buf.readUtf();
        String model = buf.readUtf();
        CastAnimationOptions opts = CastAnimationOptions.read(buf);
        return new PlayPlayerAnimationPacket(uuid, anim, model, opts);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerUuid);
        buf.writeUtf(animationName);
        buf.writeUtf(modelId);
        options.write(buf);
    }

    // ─── Handler (runs on client main thread) ────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        System.out.println("[Somnium-DIAG] PlayPlayerAnimationPacket.handle: RECEIVED on client"
                + " uuid=" + playerUuid + " anim=" + animationName + " model=" + modelId
                + " direction=" + ctx.get().getDirection());
        ctx.get().enqueueWork(() -> {
            System.out.println("[Somnium-DIAG] PlayPlayerAnimationPacket.handle: enqueued work running on main thread");
            SomniumCastAnimatable.setAnimation(playerUuid, animationName, modelId, options);
        });
        ctx.get().setPacketHandled(true);
    }
}