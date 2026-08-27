package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ninguna pantalla del juego sale en la emision: ni la mesa de realizacion, ni el
 * inventario, ni el menu de pausa. Asi puedes trastear mientras emites y el
 * espectador solo ve el plano.
 */
@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void tvcam$hideFromBroadcast(DrawContext context, int mouseX, int mouseY, float delta,
                                         CallbackInfo ci) {
        if (CameraDirector.get().isCameraFrame()) {
            ci.cancel();
        }
    }
}
