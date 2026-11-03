package dev.fragruns.mixin;

import dev.fragruns.FragRunsClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class MapFrameMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/render/GuiRenderer;render()V",shift=At.Shift.AFTER))
    private void fragruns$readMap(CallbackInfo ci) {
        var mod=FragRunsClient.INSTANCE;
        if (mod!=null && mod.maps()!=null) mod.maps().afterGui();
    }
}
