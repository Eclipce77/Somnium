package net.eclipce.somnium.network;

import net.eclipce.somnium.compat.geckolib.player.cast.SomniumProceduralStretch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client packet that sets or clears a code-driven <em>body lean</em> for a player,
 * used to pitch a set of body parts toward a direction known only at runtime (e.g. the Gomu
 * Rocket leaning the whole body — except the grabbing arm — toward the block it's pulling
 * toward).
 *
 * <p>Sent with {@code SomniumNetwork.sendToTracking} so the lean renders on the player for all
 * viewers. The client handler stores it in {@link SomniumProceduralStretch}, which
 * {@code SomniumCastBoneApplicator} reads each frame.</p>
 *
 * <h3>Registration</h3>
 * <pre>{@code
 * CHANNEL.messageBuilder(ProceduralStretchPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
 *         .encoder(ProceduralStretchPacket::encode)
 *         .decoder(ProceduralStretchPacket::decode)
 *         .consumerMainThread(ProceduralStretchPacket::handle)
 *         .add();
 * }</pre>
 */
public class ProceduralStretchPacket {

    private final UUID    player;
    private final boolean active;     // false => clear the lean for this player
    private final float   pitchRad;   // lean pitch applied to each part in the mask
    private final int     partsMask;  // bitmask of CastBodyPart ordinals to lean
    private final int     armOrdinal; // CastBodyPart ordinal of the held arm, or -1 for none
    private final float   armScaleY;  // arm length scale (Y)
    private final float   armPitchRad;// arm aim pitch
    private final boolean clearClip;  // if true, also end any active cast clip for this player

    public ProceduralStretchPacket(UUID player, boolean active, float pitchRad, int partsMask,
                                   int armOrdinal, float armScaleY, float armPitchRad,
                                   boolean clearClip) {
        this.player = player;
        this.active = active;
        this.pitchRad = pitchRad;
        this.partsMask = partsMask;
        this.armOrdinal = armOrdinal;
        this.armScaleY = armScaleY;
        this.armPitchRad = armPitchRad;
        this.clearClip = clearClip;
    }

    /** Convenience factory for a clear packet. */
    public static ProceduralStretchPacket clear(UUID player) {
        return new ProceduralStretchPacket(player, false, 0f, 0, -1, 1f, 0f, false);
    }

    /** Packet that ends the active cast clip for a player (without touching the lean). */
    public static ProceduralStretchPacket clearClip(UUID player) {
        return new ProceduralStretchPacket(player, false, 0f, 0, -1, 1f, 0f, true);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeBoolean(active);
        buf.writeFloat(pitchRad);
        buf.writeVarInt(partsMask);
        buf.writeVarInt(armOrdinal);
        buf.writeFloat(armScaleY);
        buf.writeFloat(armPitchRad);
        buf.writeBoolean(clearClip);
    }

    public static ProceduralStretchPacket decode(FriendlyByteBuf buf) {
        return new ProceduralStretchPacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // PLAY_TO_CLIENT: runs client-side on the main thread (consumerMainThread).
        if (clearClip) {
            net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable
                    .clearPlayer(player);
        }
        if (active) {
            net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart armPart = null;
            if (armOrdinal >= 0) {
                net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart[] all =
                        net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart.values();
                if (armOrdinal < all.length) armPart = all[armOrdinal];
            }
            SomniumProceduralStretch.set(player,
                    new SomniumProceduralStretch.Lean(pitchRad,
                            SomniumProceduralStretch.partsFromMask(partsMask),
                            armPart, armScaleY, armPitchRad));
        } else {
            SomniumProceduralStretch.clear(player);
        }
        context.setPacketHandled(true);
    }
}