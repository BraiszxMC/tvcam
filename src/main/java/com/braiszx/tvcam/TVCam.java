package com.braiszx.tvcam;

import com.braiszx.tvcam.camera.CameraDirector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TVCam implements ClientModInitializer {
    /**
     * Con shaders puestos, la emision se ve rara: los efectos que reaprovechan el
     * frame anterior (suavizado temporal, desenfoque de movimiento, oclusion) no
     * entienden que un frame de cada dos venga de otra camara. No es algo que se
     * pueda apagar desde aqui, asi que al menos se avisa y se explica que tocar.
     */
    private static void warnAboutShaders() {
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isModLoaded("iris") || loader.isModLoaded("oculus")) {
            LOGGER.warn("Iris detectado: si la emision se ve rara, apaga en el shader el "
                    + "suavizado temporal (TAA) y el desenfoque de movimiento, o usa "
                    + "/tvcam burst 4 para darle varios frames seguidos a cada vista.");
        }
    }

    public static final String MOD_ID = "tvcam";
    public static final Logger LOGGER = LoggerFactory.getLogger("TVCam");

    @Override
    public void onInitializeClient() {
        Keybinds.register();
        Commands.register();
        Hud.register();
        com.braiszx.tvcam.net.RemoteControl.register();
        if ("1".equals(System.getenv("TVCAM_SELFTEST"))) {
            SelfTest.register();
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CameraDirector.get().shutdown());
        warnAboutShaders();
        LOGGER.info("TVCam listo");
    }
}
