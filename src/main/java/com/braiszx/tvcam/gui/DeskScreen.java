package com.braiszx.tvcam.gui;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraMode;
import com.braiszx.tvcam.camera.CameraPoint;
import com.braiszx.tvcam.camera.TargetSpec;
import com.braiszx.tvcam.camera.TargetTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * La mesa de realizacion: multiviewer arriba con la imagen de cada camara, bus de
 * programa abajo y el panel de la camara seleccionada a la derecha.
 *
 * <p>Los monitores no son una captura: el realizador dedica de vez en cuando un
 * frame a dibujar el mundo desde cada camara y guarda esa imagen reducida. Por eso
 * se refrescan un par de veces por segundo y no a la velocidad del juego: son
 * monitores de control, no la emision.
 */
public final class DeskScreen extends Screen {
    private static int selected;

    private final boolean openedByWand;
    private TextFieldWidget nameField;
    private TextFieldWidget goalField;
    private int page;

    public DeskScreen(boolean openedByWand) {
        super(Text.literal("Mesa de realizacion"));
        this.openedByWand = openedByWand;
        int active = CameraDirector.get().activeIndex();
        if (active >= 0) {
            selected = active;
        }
    }

    public static int selectedIndex() {
        return selected;
    }

    public boolean openedByWand() {
        return openedByWand;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        CameraDirector director = CameraDirector.get();
        director.setPreviewsWanted(true);

        List<CameraPoint> cameras = director.cameras();
        if (selected >= cameras.size()) {
            selected = Math.max(0, cameras.size() - 1);
        }

        int margin = 10;
        int panelWidth = Math.clamp(width / 4, 96, 132);
        int panelX = width - margin - panelWidth;
        int gridLeft = margin;
        int gridTop = 40;
        int gridRight = panelX - 12;
        int busTop = height - 78;

        buildMultiviewer(cameras, gridLeft, gridTop, gridRight - gridLeft, busTop - gridTop - 84);
        buildGoalBar(gridLeft, busTop - 68, gridRight - gridLeft);
        buildGeneralBar(gridLeft, busTop - 40, gridRight - gridLeft);
        buildProgramBus(cameras, gridLeft, busTop, gridRight - gridLeft);
        // Se deja libre la franja de la barra de ayuda de abajo.
        buildPanel(panelX, gridTop, panelWidth, height - gridTop - margin - 18);
    }

    // ------------------------------------------------------------ multiviewer

    private void buildMultiviewer(List<CameraPoint> cameras, int x, int y, int width, int height) {
        if (cameras.isEmpty()) {
            return;
        }
        int gap = 5;
        // Se busca la rejilla que consiga meter todas las camaras a la vez: se
        // prueban distribuciones de menos a mas columnas y se coge la primera que
        // las muestre todas, que es la de monitores mas grandes. Si no cabe
        // ninguna, la que mas quepan y el resto por paginas.
        int wanted = cameras.size();
        int columns = 1;
        int tileWidth = 0;
        int tileHeight = 0;
        int rows = 1;
        int bestSlots = -1;
        for (int candidate = 1; candidate <= 5; candidate++) {
            int candidateWidth = Math.min(190, (width - gap * (candidate - 1)) / candidate);
            if (candidateWidth < 56) {
                break;
            }
            int candidateHeight = candidateWidth * 9 / 16 + 12;
            int candidateRows = Math.max(1, (height + gap) / (candidateHeight + gap));
            int slots = candidate * candidateRows;
            if (slots > bestSlots) {
                bestSlots = slots;
                columns = candidate;
                tileWidth = candidateWidth;
                tileHeight = candidateHeight;
                rows = candidateRows;
            }
            if (slots >= wanted) {
                columns = candidate;
                tileWidth = candidateWidth;
                tileHeight = candidateHeight;
                rows = candidateRows;
                break;
            }
        }
        int perPage = columns * rows;
        int pages = Math.max(1, (cameras.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);

        if ("1".equals(System.getenv("TVCAM_SELFTEST"))) {
            com.braiszx.tvcam.TVCam.LOGGER.info(
                    "[selftest] rejilla: area {}x{} camaras={} -> {} columnas x {} filas (tile {}x{})",
                    width, height, cameras.size(), columns, rows, tileWidth, tileHeight);
        }
        int first = page * perPage;
        for (int slot = 0; slot < perPage && first + slot < cameras.size(); slot++) {
            int index = first + slot;
            int column = slot % columns;
            int row = slot / columns;
            int tileX = x + column * (tileWidth + gap);
            int tileY = y + row * (tileHeight + gap);
            addDrawableChild(new MonitorTile(tileX, tileY, tileWidth, tileHeight, index,
                    () -> select(index),
                    () -> {
                        CameraDirector.get().cut(index);
                        select(index);
                    }));
        }

        if (pages > 1) {
            addDrawableChild(new ConsoleButton(x, y + rows * (tileHeight + gap), 60, 16,
                    "< pagina", () -> {
                page = (page - 1 + pages) % pages;
                rebuild();
            }).compact());
            addDrawableChild(new ConsoleButton(x + 66, y + rows * (tileHeight + gap), 60, 16,
                    "pagina >", () -> {
                page = (page + 1) % pages;
                rebuild();
            }).compact());
        }
    }

    /** El bus de programa: una tecla por camara, como la fila de un mezclador. */
    private void buildProgramBus(List<CameraPoint> cameras, int x, int y, int width) {
        CameraDirector director = CameraDirector.get();
        int count = Math.max(1, Math.min(cameras.size(), 9));
        int gap = 4;
        int keyWidth = Math.min(52, (width - gap * count) / Math.max(count + 1, 4));
        int keyX = x;
        for (int i = 0; i < count; i++) {
            int index = i;
            addDrawableChild(new ConsoleButton(keyX, y, keyWidth, 26, String.valueOf(i + 1), () -> {
                director.cut(index);
                select(index);
            }).compact().accent(Console.TALLY).lit(() -> director.activeIndex() == index)
                    .help("Pone la camara " + (i + 1) + " al aire. Tambien con el "
                            + (i + 1) + " del teclado numerico."));
            keyX += keyWidth + gap;
        }
        addDrawableChild(new ConsoleButton(keyX + 8, y, 46, 26, "NEGRO", () -> {
            director.cut(-1);
            rebuild();
        }).compact().lit(() -> director.activeIndex() < 0)
                .help("Corta la emision: la ventana TVCam deja de seguir a ninguna camara."));
    }

    // ------------------------------------------------------------------ panel

    private void buildPanel(int x, int y, int width, int height) {
        CameraDirector director = CameraDirector.get();
        CameraPoint camera = director.at(selected);

        // Trece filas tienen que caber en el panel sea cual sea la escala de la
        // interfaz: se calcula el alto de fila a partir del sitio que hay, en vez
        // de anclar unos controles arriba y otros abajo y que se pisen.
        int gap = 3;
        int totalRows = camera == null ? 1 : 9;
        int available = height - 12;
        int row = Math.clamp(available / Math.max(1, totalRows) - gap, 10, 18);
        int half = (width - gap) / 2;
        int cursor = y + 8;

        if (camera != null) {
            nameField = new TextFieldWidget(textRenderer, x + 1, cursor, width - 2, row,
                    Text.literal("Nombre"));
            nameField.setText(camera.name());
            nameField.setDrawsBackground(false);
            nameField.setChangedListener(value -> {
                if (!value.isBlank()) {
                    camera.name = value;
                    director.touch();
                }
            });
            addDrawableChild(nameField);
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> cycleMode(camera))
                    .label(() -> "Movimiento: " + modeLabel(camera.mode()))
                    .help("Como se mueve esta camara. QUIETA: no se mueve nunca. "
                            + "GIRA: se queda en su sitio y gira siguiendo a quien enfoca. "
                            + "PERSIGUE: se mueve con el, manteniendo la distancia actual."));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> cycleTarget(camera))
                    .label(() -> "Enfoca a: " + camera.target().shortLabel())
                    .help("A quien enfoca ESTA camara, sin afectar a las demas. Pulsa para "
                            + "pasar por: el objetivo comun, la pelota, cada jugador conectado y nadie. "
                            + "Solo tiene efecto si el movimiento es GIRA o PERSIGUE."));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "ZOOM -", () -> {
                camera.zoom = Math.clamp(camera.zoom - 0.25f, 1.0f, 10.0f);
                director.touch();
            }).compact().help("Abre el plano de esta camara."));
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "", () -> {
                camera.zoom = Math.clamp(camera.zoom + 0.25f, 1.0f, 10.0f);
                director.touch();
            }).compact().label(() -> String.format("x%.2f +", camera.zoom()))
                    .help("Aprieta el plano de esta camara, como un teleobjetivo."));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> cycleAutoZoom(camera))
                    .label(() -> "Zoom auto: " + heredado(camera.autoZoom == null,
                            camera.autoZoom != null && camera.autoZoom ? "si" : "no"))
                    .lit(() -> Boolean.TRUE.equals(camera.autoZoom))
                    .help("Que la camara apriete sola cuando la jugada se aleja. "
                            + "'como el comun' usa lo que tengas puesto para toda la retransmision."));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> cycleSmoothing(camera))
                    .label(() -> "Suavidad: " + heredado(camera.smoothing == null,
                            String.valueOf(camera.smoothing)))
                    .help("Cuanto tarda la camara en alcanzar a quien sigue. 0 = clavada al "
                            + "objetivo, 90 = muy perezosa, como el pulso de un camara de verdad."));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "TRAER",
                    () -> director.moveHere(selected)).compact()
                    .help("Mueve esta camara a donde estas tu ahora, mirando a donde miras."));
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "APUNTAR",
                    () -> director.aimHere(selected)).compact()
                    .help("Deja la camara donde esta pero le cambia el encuadre al tuyo."));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "SUBIR", () -> {
                if (director.move(selected, -1)) {
                    selected--;
                }
                rebuild();
            }).compact().help("Sube esta camara en la lista: cambia su numero y su tecla del numpad."));
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "BAJAR", () -> {
                if (director.move(selected, 1)) {
                    selected++;
                }
                rebuild();
            }).compact().help("Baja esta camara en la lista: cambia su numero y su tecla del numpad."));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "COPIAR", () -> {
                director.duplicate(selected);
                rebuild();
            }).compact().help("Crea otra camara igual que esta, para retocarla sin perder la original."));
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "BORRAR", () -> {
                director.remove(selected);
                selected = Math.max(0, selected - 1);
                rebuild();
            }).compact().accent(Console.TALLY));
            cursor += row + gap + 8;
        }

    }

    /** El rotulo que sale al marcar: se escribe aqui y se prueba al momento. */
    private void buildGoalBar(int x, int y, int width) {
        CameraDirector director = CameraDirector.get();
        int gap = 5;
        int buttonWidth = (width - gap * 3) / 4;
        int fieldWidth = buttonWidth * 2 + gap;

        goalField = new TextFieldWidget(textRenderer, x + 74, y, fieldWidth - 74, 20,
                Text.literal("Texto del gol"));
        goalField.setMaxLength(24);
        goalField.setText(director.settings().goalText);
        goalField.setChangedListener(value -> {
            director.settings().goalText = value;
            director.saveSettings();
        });
        addDrawableChild(goalField);

        addDrawableChild(new ConsoleButton(x + fieldWidth + gap, y, buttonWidth, 20, "PROBAR GOL",
                () -> director.broadcast().testGoal()).compact()
                .help("Lanza el rotulo de gol para ver como queda. Sale en la ventana de emision."));

        addDrawableChild(new ConsoleButton(x + fieldWidth + buttonWidth + gap * 2, y, buttonWidth, 20, "",
                () -> {
                    director.settings().hideDebugInBroadcast = !director.settings().hideDebugInBroadcast;
                    director.saveSettings();
                }).compact()
                .label(() -> "Hitboxes: " + (director.settings().hideDebugInBroadcast ? "no" : "si"))
                .lit(() -> !director.settings().hideDebugInBroadcast)
                .help("Si las hitboxes y demas ayudas de F3 salen o no en la emision. "
                        + "En 'no' las sigues viendo tu, pero el espectador no."));
    }

    /**
     * Los mandos que valen para toda la retransmision, en su propia fila: en el
     * panel de la camara no cabian y ademas se mezclaban con los ajustes de una
     * sola camara, que es lo que mas confundia.
     */
    private void buildGeneralBar(int x, int y, int width) {
        CameraDirector director = CameraDirector.get();
        int gap = 5;
        int buttonWidth = (width - gap * 3) / 4;

        addDrawableChild(new ConsoleButton(x, y, buttonWidth, 20, "+ CAMARA", () -> {
            director.addHere(null);
            selected = director.cameras().size() - 1;
            rebuild();
        }).compact().accent(Console.OK)
                .help("Crea una camara nueva justo donde estas tu, mirando a donde miras."));

        addDrawableChild(new ConsoleButton(x + buttonWidth + gap, y, buttonWidth, 20, "",
                director::toggleWindow)
                .compact()
                .label(() -> "Emision: " + (director.window().isOpen() ? "ON" : "OFF"))
                .lit(() -> director.window().isOpen())
                .accent(Console.TALLY)
                .help("Abre o cierra la ventana TVCam, que es la que capturas en OBS."));

        addDrawableChild(new ConsoleButton(x + (buttonWidth + gap) * 2, y, buttonWidth, 20, "", () -> {
            director.settings().autoDirector = !director.settings().autoDirector;
            director.saveSettings();
        }).compact()
                .label(() -> "Corta: " + (director.settings().autoDirector ? "el mod" : "tu"))
                .lit(() -> director.settings().autoDirector)
                .help("En 'el mod', cambia solo al plano que mejor ve la jugada. "
                        + "En 'tu', mandas tu con el numpad o pulsando dos veces un monitor."));

        addDrawableChild(new ConsoleButton(x + (buttonWidth + gap) * 3, y, buttonWidth, 20, "",
                this::cycleGlobalTarget)
                .compact()
                .label(() -> "Comun: " + director.target().shortLabel())
                .help("A quien enfocan las camaras que tengan puesto 'el comun'. "
                        + "No toca a las que enfocan a alguien concreto."));
    }

    // --------------------------------------------------------------- acciones

    /** Nombres en castellano llano para los modos, que "acompanar" no dice nada. */
    private static String modeLabel(CameraMode mode) {
        return switch (mode) {
            case FIJA -> "QUIETA";
            case SEGUIR -> "GIRA";
            case ACOMPANAR -> "PERSIGUE";
        };
    }

    private static String heredado(boolean inherited, String value) {
        return inherited ? "comun" : value;
    }

    private void select(int index) {
        selected = index;
        rebuild();
    }

    private void cycleMode(CameraPoint camera) {
        CameraMode[] modes = CameraMode.values();
        CameraMode next = modes[(camera.mode().ordinal() + 1) % modes.length];
        camera.mode = next;
        if (next == CameraMode.ACOMPANAR) {
            Entity entity = CameraDirector.get().target().resolve(camera.target());
            camera.setOffset(entity == null ? camera.offset()
                    : camera.pos().subtract(entity.getBoundingBox().getCenter()));
        }
        CameraDirector.get().touch();
    }

    private void cycleTarget(CameraPoint camera) {
        TargetSpec spec = camera.target();
        List<String> players = onlinePlayers();
        switch (spec.kind) {
            case GLOBAL -> camera.target = TargetSpec.ball();
            case PELOTA -> camera.target = players.isEmpty()
                    ? TargetSpec.none() : TargetSpec.player(players.get(0));
            case JUGADOR -> {
                int index = players.indexOf(spec.player);
                camera.target = (index >= 0 && index + 1 < players.size())
                        ? TargetSpec.player(players.get(index + 1)) : TargetSpec.none();
            }
            case NINGUNO -> camera.target = TargetSpec.global();
        }
        CameraDirector.get().touch();
    }

    private void cycleAutoZoom(CameraPoint camera) {
        camera.autoZoom = camera.autoZoom == null ? Boolean.TRUE
                : camera.autoZoom ? Boolean.FALSE : null;
        CameraDirector.get().touch();
    }

    private void cycleSmoothing(CameraPoint camera) {
        camera.smoothing = camera.smoothing == null ? 0
                : camera.smoothing >= 90 ? null : camera.smoothing + 15;
        CameraDirector.get().touch();
    }

    private void cycleGlobalTarget() {
        TargetTracker tracker = CameraDirector.get().target();
        List<String> players = onlinePlayers();
        switch (tracker.kind()) {
            case NINGUNO -> tracker.followBall();
            case PELOTA -> {
                if (players.isEmpty()) {
                    tracker.clear();
                } else {
                    tracker.followPlayer(players.get(0));
                }
            }
            case JUGADOR -> {
                int index = players.indexOf(tracker.playerName());
                if (index >= 0 && index + 1 < players.size()) {
                    tracker.followPlayer(players.get(index + 1));
                } else {
                    tracker.clear();
                }
            }
            case ENTIDAD -> tracker.followBall();
        }
    }

    private static List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            client.world.getPlayers().forEach(player -> names.add(player.getGameProfile().name()));
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    // ----------------------------------------------------------------- dibujo

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderChrome(context);
        super.render(context, mouseX, mouseY, delta);
        renderLabels(context);
        renderHelp(context);
        // Asi PROBAR GOL enseña el rotulo aqui mismo: en la emision lo veran los
        // espectadores, pero tu con la mesa abierta no la estas mirando.
        if (CameraDirector.get().broadcast().goalFlash().isShowing()) {
            CameraDirector.get().broadcast().render(context);
        }
    }

    /** La explicacion del boton que tienes debajo del raton, abajo del todo. */
    private void renderHelp(DrawContext context) {
        String help = null;
        for (var child : children()) {
            if (child instanceof ConsoleButton button && button.isHovered()) {
                help = button.helpText();
                break;
            }
        }
        if (help == null) {
            help = "Un clic selecciona; dos clics ponen al aire. Pon el raton en un boton.";
        }
        int y = height - 12;
        context.fill(0, y - 4, width, height, Console.PANEL);
        // Una sola linea: si no cabe entera, se recorta.
        context.drawText(Console.font(), Console.font().trimToWidth(help, width - 24), 12, y,
                Console.TEXT_DIM, false);
    }

    /**
     * Con la mesa abierta el teclado va a la pantalla y no al juego, asi que las
     * teclas del numpad dejaban de funcionar justo cuando mas falta hacen.
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        boolean writing = (nameField != null && nameField.isFocused())
                || (goalField != null && goalField.isFocused());
        if (!writing) {
            int key = input.key();
            if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_KP_9) {
                int index = key - org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1;
                CameraDirector.get().cut(index);
                select(index);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_0) {
                CameraDirector.get().cut(-1);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) {
                CameraPoint camera = CameraDirector.get().at(selected);
                if (camera != null) {
                    float step = key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD ? 0.25f : -0.25f;
                    camera.zoom = Math.clamp(camera.zoom + step, 1.0f, 10.0f);
                    CameraDirector.get().touch();
                }
                return true;
            }
        }
        return super.keyPressed(input);
    }

    /** El mueble: cabecera, paneles y serigrafia. */
    private void renderChrome(DrawContext context) {
        CameraDirector director = CameraDirector.get();
        int margin = 10;
        int panelWidth = Math.clamp(width / 4, 96, 132);
        int panelX = width - margin - panelWidth;

        context.fill(0, 0, width, 28, Console.PANEL);
        context.fill(0, 28, width, 29, Console.EDGE);

        context.drawText(Console.font(), "TVCam", margin, 10, Console.TEXT, false);
        context.drawText(Console.font(), "MESA DE REALIZACION",
                margin + Console.font().getWidth("TVCam") + 8, 10, Console.TEXT_FAINT, false);

        CameraPoint live = director.activeCamera();
        String status = live == null ? "NEGRO" : "AL AIRE  " + (director.activeIndex() + 1)
                + "  " + live.name();
        int statusWidth = Console.font().getWidth(status);
        Console.tallyLamp(context, width - margin - statusWidth - 12, 10, live != null);
        context.drawText(Console.font(), status, width - margin - statusWidth, 10,
                live == null ? Console.TEXT_FAINT : Console.TALLY, false);

        // Panel lateral y su serigrafia.
        Console.panel(context, panelX - 6, 34, panelWidth + 12, height - 48, Console.PANEL);
        Console.outline(context, panelX - 6, 34, panelWidth + 12, height - 48, Console.EDGE_SOFT);

        Console.sectionTitle(context, "CAMARA", panelX, 34, panelWidth);
        int busTop = height - 78;
        Console.sectionTitle(context, "ROTULOS", margin, busTop - 80, 120);
        context.drawText(Console.font(), "Al marcar:", margin, busTop - 62, Console.TEXT_DIM, false);
        Console.sectionTitle(context, "GENERAL", margin, busTop - 52, 120);
        Console.sectionTitle(context, "MULTIVIEWER", margin, 32, 120);
        Console.sectionTitle(context, "PROGRAMA", margin, busTop - 12, 120);
    }

    private void renderLabels(DrawContext context) {
        CameraDirector director = CameraDirector.get();
        CameraPoint camera = director.at(selected);
        if (camera == null) {
            context.drawCenteredTextWithShadow(Console.font(),
                    "No hay camaras: pulsa + CAMARA AQUI", width / 2, height / 2 - 4,
                    Console.TEXT_DIM);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, Console.BACKDROP);
    }

    @Override
    public void close() {
        CameraDirector.get().setPreviewsWanted(false);
        super.close();
    }

    @Override
    public void removed() {
        CameraDirector.get().setPreviewsWanted(false);
        super.removed();
    }
}
