package net.eclipce.somnium.network;

import net.eclipce.somnium.compat.geckolib.player.cast.CastBodyPart;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumProceduralStretch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client packet that sets or clears a <em>procedural</em> (code-driven) limb stretch
 * for a player, used when a limb must reach toward a world point not known at animation-
 * authoring time (e.g. the Gomu Rocket grab stretching the arm to an aimed block).
 *
 * <p>Sent with {@code SomniumNetwork.sendToTracking} so the stretch renders on the casting
 * player for everyone who can see them (first- and third-person), not just the caster. The
 * client handler stores it in {@link SomniumProceduralStretch}, which
 * {@code SomniumCastBoneApplicator} reads each frame and folds into the existing per-bone
 * scale channel.</p>
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

    private final UUID player;
    private final boolean active;       // false => clear the stretch for this player
    private final int     partOrdinal;  // CastBodyPart ordinal (only read when active)
    private final float   reachBlocks;
    private final double  targetX, targetY, targetZ;

    public ProceduralStretchPacket(UUID player, boolean active, int partOrdinal,
                                   float reachBlocks,
                                   double targetX, double targetY, double targetZ) {
        this.player = player;
        this.active = active;
        this.partOrdinal = partOrdinal;
        this.reachBlocks = reachBlocks;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
    }

    /** Convenience factory for a clear packet. */
    public static ProceduralStretchPacket clear(UUID player) {
        return new ProceduralStretchPacket(player, false, 0, 0f, 0, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeBoolean(active);
        buf.writeVarInt(partOrdinal);
        buf.writeFloat(reachBlocks);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
    }

    public static ProceduralStretchPacket decode(FriendlyByteBuf buf) {
        return new ProceduralStretchPacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // PLAY_TO_CLIENT: runs client-side on the main thread (consumerMainThread).
        if (active) {
            CastBodyPart[] parts = CastBodyPart.values();
            CastBodyPart part = (partOrdinal >= 0 && partOrdinal < parts.length)
                    ? parts[partOrdinal] : CastBodyPart.RIGHT_ARM;
            SomniumProceduralStretch.set(player,
                    new SomniumProceduralStretch.Stretch(part, reachBlocks,
                            targetX, targetY, targetZ));
        } else {
            SomniumProceduralStretch.clear(player);
        }
        context.setPacketHandled(true);
    }
}