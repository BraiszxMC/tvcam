package com.braiszx.tvcam.mixin;

import com.braiszx.tvcam.camera.CameraDirector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mientras estas emitiendo, el juego no baja los FPS.
 *
 * <p>Minecraft los recorta cuando abres un menu, sales de la ventana o te
 * quedas quieto, para no gastar de mas. Eso esta muy bien jugando, pero
 * retransmitiendo es justo lo contrario de lo que quieres: en cuanto ibas a
 * Discord o a OBS, la emision se arrastraba.
 */
@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void tvcam$keepRendering(CallbackInfoReturnable<Integer> cir) {
        CameraDirector director = CameraDirector.get();
        if (!director.window().isOpen() || !director.settings().keepFpsWhileBroadcasting) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int configured = client.options.getMaxFps().getValue();
        if (cir.getReturnValue() < configured) {
            cir.setReturnValue(configured);
        }
    }
}
