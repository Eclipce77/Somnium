package net.eclipce.somnium.network;

import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client packet that triggers a GeckoLib cast animation on a player.
 *
 * <p>Sent via {@link net.eclipce.somnium.compat.geckolib.animation.SomniumAnimHelper#triggerCastAnimation},
 * which broadcasts to all players tracking the target (including themselves).
 * On arrival, the client-side {@link SomniumCastAnimatable} stores the animation
 * by UUID. The next render frame the Mixin picks it up and applies bone transforms
 * onto the vanilla player model additively.</p>
 *
 * <p>Includes a model registry key so the correct {@link net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastModel}
 * is resolved client-side.</p>
 */
public class PlayPlayerAnimationPacket {

    private final UUID playerUuid;
    /** Animation name as declared in the animation.json — e.g. "animation.mymod.cast" */
    private final String animationName;
    /** Registry key of the SomniumCastModel to use — e.g. "mymod:fire_cast" */
    private final String modelId;

    public PlayPlayerAnimationPacket(UUID playerUuid, String animationName, String modelId) {
        this.playerUuid = playerUuid;
        this.animationName = animationName;
        this.modelId = modelId;
    }

    // ─── Codec ───────────────────────────────────────────────────────────────

    public static PlayPlayerAnimationPacket decode(FriendlyByteBuf buf) {
        return new PlayPlayerAnimationPacket(buf.readUUID(), buf.readUtf(), buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerUuid);
        buf.writeUtf(animationName);
        buf.writeUtf(modelId);
    }

    // ─── Handler (runs on client main thread) ────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                SomniumCastAnimatable.setAnimation(playerUuid, animationName, modelId));
        ctx.get().setPacketHandled(true);
    }
}