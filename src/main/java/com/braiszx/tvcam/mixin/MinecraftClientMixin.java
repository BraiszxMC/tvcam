package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marca donde empieza y acaba cada frame, para saber a quien pertenece. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void tvcam$beginFrame(boolean tick, CallbackInfo ci) {
        CameraDirector.get().beginFrame();
    }

    /**
     * En los frames de monitor, el juego dibuja el mundo dentro del monitor en vez
     * de en su framebuffer de siempre.
     *
     * <p>Es la pieza clave del multiviewer: asi la imagen se genera ya en su
     * destino, sin copiarla. Copiarla era lo que fallaba, porque la copia del juego
     * respeta el canal alfa y el render del mundo lo deja a cero. De paso se
     * renderiza a 256x144 en lugar de a pantalla completa, que cuesta mucho menos.
     */
    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void tvcam$previewTarget(CallbackInfoReturnable<Framebuffer> cir) {
        CameraDirector director = CameraDirector.get();
        if (!director.isPreviewFrame()) {
            return;
        }
        Framebuffer target = director.previews().target(director.previewIndex());
        if (target != null) {
            cir.setReturnValue(target);
        }
    }

    @Inject(method = "render(Z)V", at = @At("RETURN"))
    private void tvcam$endFrame(boolean tick, CallbackInfo ci) {
        CameraDirector.get().endFrame();
    }

}
