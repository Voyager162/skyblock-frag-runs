package dev.fragruns.mixin;

import dev.fragruns.tracking.RunMapRecorder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="co.stellarskys.stella.features.stellanav.render.Clear",remap=false)
abstract class StellaMapMixin {
    @Unique private boolean fragruns$drewRoom;
    @Inject(method="renderRoom",at=@At("HEAD"),require=0)
    private void fragruns$room(CallbackInfo ci) { fragruns$drewRoom=true; }
    @Inject(method="renderMap",at=@At("TAIL"),require=0)
    private void fragruns$map(GuiGraphicsExtractor graphics,CallbackInfo ci) {
        if (fragruns$drewRoom) RunMapRecorder.mapRendered(graphics,-5,133);
        fragruns$drewRoom=false;
    }
}
