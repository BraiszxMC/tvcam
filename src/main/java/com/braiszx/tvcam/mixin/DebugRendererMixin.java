package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Las hitboxes y demas ayudas de F3 se quedan en tu pantalla y no salen en la
 * emision ni en los monitores.
 *
 * <p>Se corta aqui, en el dibujado, y no en la lista de ayudas visibles: esa
 * lista el juego la arma una sola vez, no en cada frame, asi que filtrarla no
 * servia de nada y ademas se arriesgaba a quitarte las hitboxes tambien a ti.
 */
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tvcam$cleanFeed(Frustum frustum, double cameraX, double cameraY, double cameraZ,
                                 float tickDelta, CallbackInfo ci) {
        CameraDirector director = CameraDirector.get();
        if (director.isBroadcastFrame() && director.settings().hideDebugInBroadcast) {
            ci.cancel();
        }
    }
}
