package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lo que la emision no debe ver, y el zoom del teleobjetivo. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /** La mano y el objeto que llevas no salen en la emision. */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void tvcam$noHand(float tickDelta, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
        if (CameraDirector.get().isBroadcastFrame()) {
            ci.cancel();
        }
    }

    /**
     * En cuanto el mundo esta dibujado y antes de que el juego pinte encima el HUD,
     * el chat o cualquier pantalla, se recoge la imagen limpia.
     *
     * <p>Antes se recogia al final del frame, y por eso la emision salia con
     * trozos de la interfaz y de los atlas de texturas encima: a esas alturas el
     * framebuffer ya no contiene solo el mundo.
     */
    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void tvcam$cleanFeed(RenderTickCounter tickCounter, CallbackInfo ci) {
        CameraDirector.get().afterWorldRender();
    }

    /** El zoom se hace estrechando el campo de vision, como un teleobjetivo real. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void tvcam$zoom(Camera camera, float tickDelta, boolean changingFov,
                            CallbackInfoReturnable<Float> cir) {
        CameraDirector director = CameraDirector.get();
        if (!director.isBroadcastFrame()) {
            return;
        }
        float zoom = director.isPreviewFrame()
                ? director.previewZoom(director.previewIndex())
                : director.currentZoom();
        if (zoom <= 0.0f || Math.abs(zoom - 1.0f) < 0.001f) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue() / zoom);
    }
}
