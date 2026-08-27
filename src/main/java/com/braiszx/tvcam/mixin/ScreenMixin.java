package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ninguna pantalla del juego sale en la emision: ni la mesa, ni el inventario, ni
 * el menu de pausa. Tu las sigues viendo, porque en los frames de camara tu
 * ventana muestra la copia de tu ultimo frame, que si las lleva.
 */
@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
    private void tvcam$hideFromBroadcast(DrawContext context, int mouseX, int mouseY, float delta,
                                         CallbackInfo ci) {
        CameraDirector director = CameraDirector.get();
        if (!director.isBroadcastFrame()) {
            return;
        }
        // En la autoprueba no hay mundo que dibujar, asi que se deja pasar la
        // pantalla en los frames de monitor: es la unica forma de comprobar aqui
        // que el juego dibuja de verdad dentro del monitor.
        if (director.isPreviewFrame() && "1".equals(System.getenv("TVCAM_SELFTEST"))) {
            return;
        }
        ci.cancel();
    }
}
