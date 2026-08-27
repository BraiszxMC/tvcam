package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.gui.hud.debug.DebugHudProfile;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Las ayudas de depuracion (hitboxes, bordes de chunk y demas) se quedan en tu
 * pantalla y no salen en la emision, para que puedas jugar con ellas puestas sin
 * que las vea el espectador.
 */
@Mixin(DebugHudProfile.class)
public class DebugHudProfileMixin {
    @Inject(method = "isEntryVisible", at = @At("HEAD"), cancellable = true)
    private void tvcam$cleanFeed(Identifier entry, CallbackInfoReturnable<Boolean> cir) {
        CameraDirector director = CameraDirector.get();
        if (director.isBroadcastFrame() && director.settings().hideDebugInBroadcast) {
            cir.setReturnValue(false);
        }
    }
}
