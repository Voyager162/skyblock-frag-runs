package dev.fragruns.mixin;

import dev.fragruns.FragRunsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PickupPacketMixin {
    // Observe the original server message once, before chat filters can suppress, compact or reformat it.
    @Inject(method="handleSystemChat",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",shift=At.Shift.AFTER))
    private void fragruns$serverMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!packet.overlay() && FragRunsClient.INSTANCE != null) {
            FragRunsClient.INSTANCE.tracker().serverMessage(Minecraft.getInstance(), packet.content().getString());
        }
    }
    // After the thread check, before vanilla removes/decrements the item entity.
    @Inject(method="handleTakeItemEntity",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",shift=At.Shift.AFTER))
    private void fragruns$pickup(ClientboundTakeItemEntityPacket packet,CallbackInfo ci) {
        Minecraft client=Minecraft.getInstance();
        if(FragRunsClient.INSTANCE==null || client.player==null || client.level==null || packet.getPlayerId()!=client.player.getId()) return;
        if(client.level.getEntity(packet.getItemId()) instanceof ItemEntity entity) FragRunsClient.INSTANCE.tracker().pickup(entity,packet.getAmount());
    }
    @Inject(method="handleEntityEvent",at=@At("TAIL"))
    private void fragruns$death(ClientboundEntityEventPacket packet,CallbackInfo ci) {
        Minecraft client=Minecraft.getInstance();
        if(packet.getEventId()==3 && client.level!=null && FragRunsClient.INSTANCE!=null)
            FragRunsClient.INSTANCE.tracker().entityDeath(packet.getEntity(client.level));
    }
}
