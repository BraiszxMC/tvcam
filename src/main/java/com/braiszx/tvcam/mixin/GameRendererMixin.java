package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
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
