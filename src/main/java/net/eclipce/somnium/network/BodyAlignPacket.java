package net.eclipce.somnium.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → casting-client packet that re-faces the local player's body to a yaw, the way a
 * left-click attack snaps the body toward the look direction. Sent from
 * {@code SomniumAnimHelper.triggerCastAnimation} when {@code onExecuteBodyAlign} is set.
 *
 * <h3>Why a client-bound rotation packet (and not a server-side write or teleport)</h3>
 * <p>The casting client is authoritative over its own rotation: a server-side
 * {@code setYRot}/{@code yBodyRot} write gets overwritten by the client's next movement
 * packet, and {@code connection.teleport} corrects position too, which stutters movement.
 * The non-stuttering, actually-sticking path is to tell the owning client to set its OWN
 * rotation — exactly what vanilla left-click face-align does locally. This packet is that
 * instruction.</p>
 *
 * <h3>What it sets</h3>
 * <p>Only body and head yaw, to the supplied value (the player's look yaw at execution).
 * The camera (look yaw/pitch) is NOT touched, so the view does not move — the body simply
 * catches up to where the player is already looking.</p>
 *
 * <h3>Registration</h3>
 * <p>Register in {@code SomniumNetwork.init()} alongside the other packets, following the
 * same messageBuilder pattern (this is Server → Client):</p>
 * <pre>{@code
 * CHANNEL.messageBuilder(BodyAlignPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
 *         .encoder(BodyAlignPacket::encode)
 *         .decoder(BodyAlignPacket::decode)
 *         .consumerMainThread(BodyAlignPacket::handle)
 *         .add();
 * }</pre>
 * <p>Send to the casting player with the existing
 * {@code SomniumNetwork.sendToClient(packet, serverPlayer)}.</p>
 */
public class BodyAlignPacket {

    private final float yaw;

    public BodyAlignPacket(float yaw) {
        this.yaw = yaw;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(yaw);
    }

    /** Static decoder to match SomniumNetwork's messageBuilder(...).decoder(...) registration. */
    public static BodyAlignPacket decode(FriendlyByteBuf buf) {
        return new BodyAlignPacket(buf.readFloat());
    }

    /**
     * Handles the packet on the client main thread. Registered via
     * {@code .consumerMainThread(BodyAlignPacket::handle)}, which already dispatches on the
     * main thread, so we apply directly — no enqueueWork needed.
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // PLAY_TO_CLIENT only, so this runs client-side; Minecraft.getInstance() is safe.
        apply(yaw);
        context.setPacketHandled(true);
    }

    /** Sets the local player's body + head yaw, leaving the camera (look) untouched. */
    private static void apply(float yaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Body yaw is what the model faces; head yaw drives the body-yaw clamp. Setting both
        // to the look yaw collapses the head/body twist to zero so the body holds this facing
        // instead of springing back. yRot (camera) is intentionally left as-is.
        mc.player.setYBodyRot(yaw);
        mc.player.yBodyRotO = yaw;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
    }
}