package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Que se ve en TU ventana.
 *
 * <p>En los frames de camara o de monitor, el framebuffer del juego contiene la
 * vista de la camara, asi que en su lugar se presenta la copia de tu ultimo
 * frame. Se sustituye la presentacion del juego en vez de anadir otra: el juego
 * solo admite una por frame, y llamar dos veces hacia que tu ventana fuese
 * saltando entre tu vista y la de la camara.
 */
@Mixin(Framebuffer.class)
public class FramebufferMixin {
    @Inject(method = "blitToScreen", at = @At("HEAD"), cancellable = true)
    private void tvcam$present(CallbackInfo ci) {
        Framebuffer self = (Framebuffer) (Object) this;
        // Solo el framebuffer principal del juego; el nuestro se presenta solo.
        if (self != MinecraftClient.getInstance().getFramebuffer()) {
            return;
        }
        if (CameraDirector.get().presentToPlayerWindow(self)) {
            ci.cancel();
        }
    }
}
