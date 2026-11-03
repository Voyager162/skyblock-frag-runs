package dev.fragruns.mixin;

import dev.fragruns.FragRunsClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class StashViewMixin {
    @Inject(method="handleOpenScreen",cancellable=true,at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"))
    private void fragruns$ownView(ClientboundOpenScreenPacket p,CallbackInfo ci) {
        var mod=FragRunsClient.INSTANCE;
        if (mod!=null && mod.tracker().stashView().open(p)) ci.cancel();
    }
    @Inject(method="handleContainerContent",at=@At("TAIL"))
    private void fragruns$contents(ClientboundContainerSetContentPacket p,CallbackInfo ci) {
        var mod=FragRunsClient.INSTANCE;if(mod!=null) {
            mod.tracker().stashView().contents(p);
            mod.tracker().stashContents(p.containerId(),p.items().size());
        }
    }
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"))
    private void fragruns$slot(ClientboundContainerSetSlotPacket p,CallbackInfo ci) {
        var mod=FragRunsClient.INSTANCE;if(mod!=null) {
            mod.tracker().stashView().slot(p);
            mod.tracker().stashSlot(p.getContainerId(),p.getSlot());
        }
    }
    @Inject(method="handleContainerClose",cancellable=true,at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"))
    private void fragruns$closed(ClientboundContainerClosePacket p,CallbackInfo ci) {
        var mod=FragRunsClient.INSTANCE;
        if(mod!=null && mod.tracker().stashView().closed(p.getContainerId())) ci.cancel();
    }
}
