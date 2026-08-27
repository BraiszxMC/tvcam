package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.MinecraftClient;
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
 * <p>El HUD se oculta por la via del propio juego (la misma opcion que F1), y aqui
 * solo se anaden encima los rotulos de television. En tus frames no se toca nada.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void tvcam$broadcastGraphics(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CameraDirector director = CameraDirector.get();
        // Los rotulos van encima del plano, y solo cuando el frame es de emision y
        // no hay ninguna pantalla abierta (con la mesa abierta la emision se recoge
        // antes, ya limpia, y no pasa por aqui).
        if (director.isCameraFrame() && MinecraftClient.getInstance().currentScreen == null) {
            director.broadcast().render(context);
        }
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
