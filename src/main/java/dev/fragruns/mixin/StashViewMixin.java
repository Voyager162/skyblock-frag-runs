package dev.fragruns.mixin;

import dev.fragruns.FragRunsClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class StashViewMixin {
    @Inject(method="handleContainerContent",at=@At("TAIL"))
    private void fragruns$contents(ClientboundContainerSetContentPacket p,CallbackInfo ci) {
        var mod=FragRunsClient.INSTANCE;if(mod!=null) {
            mod.tracker().stashContents(p.containerId(),p.items().size());
        }
    }
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"))
    private void fragruns$slot(ClientboundContainerSetSlotPacket p,CallbackInfo ci) {
        var mod=FragRunsClient.INSTANCE;if(mod!=null) {
            mod.tracker().stashSlot(p.getContainerId(),p.getSlot());
        }
    }
}
