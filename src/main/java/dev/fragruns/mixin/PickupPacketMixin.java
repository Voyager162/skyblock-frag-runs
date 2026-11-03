package dev.fragruns.mixin;

import dev.fragruns.FragRunsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PickupPacketMixin {
    // Observe the original server message once, before chat filters can suppress, compact or reformat it.
    @Inject(method="handleSystemChat",cancellable=true,at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",shift=At.Shift.AFTER))
    private void fragruns$serverMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!packet.overlay() && FragRunsClient.INSTANCE != null) {
            if (FragRunsClient.INSTANCE.tracker().stashView().reply(packet.content().getString())) {ci.cancel();return;}
            FragRunsClient.INSTANCE.tracker().serverMessage(Minecraft.getInstance(), packet.content());
        }
    }
    @Inject(method="sendCommand",at=@At("HEAD"))
    private void fragruns$stashCommand(String command,CallbackInfo ci) {
        if (FragRunsClient.INSTANCE!=null) FragRunsClient.INSTANCE.tracker().stashCommand(command);
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
            FragRunsClient.INSTANCE.tracker().entityChanged(packet.getEntity(client.level),true,false);
    }
    @Inject(method="handleSetEquipment",at=@At("TAIL"))
    private void fragruns$equipment(ClientboundSetEquipmentPacket packet,CallbackInfo ci) { fragruns$changed(packet.getEntity(),false); }
    @Inject(method="handleSetEntityData",at=@At("TAIL"))
    private void fragruns$metadata(ClientboundSetEntityDataPacket packet,CallbackInfo ci) { fragruns$changed(packet.id(),false); }
    @Inject(method="handleDamageEvent",at=@At("TAIL"))
    private void fragruns$damage(ClientboundDamageEventPacket packet,CallbackInfo ci) {
        var mc=Minecraft.getInstance();
        if (mc.level!=null && FragRunsClient.INSTANCE!=null && FragRunsClient.INSTANCE.session().inDungeon())
            FragRunsClient.INSTANCE.tracker().entityDamaged(mc.level.getEntity(packet.entityId()),
                    mc.level.getEntity(packet.sourceCauseId()>=0?packet.sourceCauseId():packet.sourceDirectId()));
    }
    @Inject(method="handleRemoveEntities",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",shift=At.Shift.AFTER))
    private void fragruns$removed(ClientboundRemoveEntitiesPacket packet,CallbackInfo ci) {
        packet.getEntityIds().forEach((int id)->fragruns$changed(id,true));
    }
    @org.spongepowered.asm.mixin.Unique
    private static void fragruns$changed(int id,boolean removed) {
        var mc=Minecraft.getInstance();
        if (mc.level!=null && FragRunsClient.INSTANCE!=null && FragRunsClient.INSTANCE.session().inDungeon())
            FragRunsClient.INSTANCE.tracker().entityChanged(mc.level.getEntity(id),false,removed);
    }
}
