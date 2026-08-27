package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La capa de graficos de la emision.
 *
 * <p>En los frames de camara se salta entero el HUD del juego (vida, hotbar,
 * chat, titulos...) para que el plano salga limpio, y en su lugar se dibujan los
 * rotulos de television. En tus frames no se toca nada.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tvcam$broadcastGraphics(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CameraDirector director = CameraDirector.get();
        if (!director.isCameraFrame()) {
            return;
        }
        director.broadcast().render(context);
        ci.cancel();
    }

    /** BlockBall canta el gol con un titulo que lleva el marcador... */
    @Inject(method = "setTitle", at = @At("HEAD"))
    private void tvcam$title(Text title, CallbackInfo ci) {
        CameraDirector.get().broadcast().onTitle(title);
    }

    /** ...y un subtitulo con el autor. */
    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void tvcam$subtitle(Text subtitle, CallbackInfo ci) {
        CameraDirector.get().broadcast().onSubtitle(subtitle);
    }
}
