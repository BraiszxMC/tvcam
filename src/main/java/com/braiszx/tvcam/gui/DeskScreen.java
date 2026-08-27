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
    private int page;
    /** Donde empieza el bloque de controles generales, para la serigrafia. */
    private int generalTop;

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
        int busTop = height - 62;

        buildMultiviewer(cameras, gridLeft, gridTop, gridRight - gridLeft, busTop - gridTop - 10);
        buildProgramBus(cameras, gridLeft, busTop, gridRight - gridLeft);
        buildPanel(panelX, gridTop, panelWidth, height - gridTop - margin);
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
            int candidateWidth = (width - gap * (candidate - 1)) / candidate;
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
            }).compact().accent(Console.TALLY).lit(() -> director.activeIndex() == index));
            keyX += keyWidth + gap;
        }
        addDrawableChild(new ConsoleButton(keyX + 8, y, 46, 26, "NEGRO", () -> {
            director.cut(-1);
            rebuild();
        }).compact().lit(() -> director.activeIndex() < 0));
    }

    // ------------------------------------------------------------------ panel

    private void buildPanel(int x, int y, int width, int height) {
        CameraDirector director = CameraDirector.get();
        CameraPoint camera = director.at(selected);

        // Trece filas tienen que caber en el panel sea cual sea la escala de la
        // interfaz: se calcula el alto de fila a partir del sitio que hay, en vez
        // de anclar unos controles arriba y otros abajo y que se pisen.
        int cameraRows = camera == null ? 0 : 9;
        int generalRows = 4;
        int separators = camera == null ? 1 : 2;
        int gap = 3;
        int totalRows = cameraRows + generalRows;
        int available = height - 12 - separators * 8;
        int row = Math.clamp(available / Math.max(1, totalRows) - gap, 11, 18);
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
                    .label(() -> "Modo  " + camera.mode().name().toLowerCase()));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> cycleTarget(camera))
                    .label(() -> "Sigue  " + camera.target().shortLabel()));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "ZOOM -", () -> {
                camera.zoom = Math.clamp(camera.zoom - 0.25f, 1.0f, 10.0f);
                director.touch();
            }).compact());
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "", () -> {
                camera.zoom = Math.clamp(camera.zoom + 0.25f, 1.0f, 10.0f);
                director.touch();
            }).compact().label(() -> String.format("x%.2f +", camera.zoom())));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> cycleAutoZoom(camera))
                    .label(() -> "Zoom auto  " + (camera.autoZoom == null ? "general"
                            : camera.autoZoom ? "si" : "no"))
                    .lit(() -> Boolean.TRUE.equals(camera.autoZoom)));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> cycleSmoothing(camera))
                    .label(() -> "Pulso  " + (camera.smoothing == null ? "general"
                            : String.valueOf(camera.smoothing))));
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "TRAER",
                    () -> director.moveHere(selected)).compact());
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "APUNTAR",
                    () -> director.aimHere(selected)).compact());
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "SUBIR", () -> {
                if (director.move(selected, -1)) {
                    selected--;
                }
                rebuild();
            }).compact());
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "BAJAR", () -> {
                if (director.move(selected, 1)) {
                    selected++;
                }
                rebuild();
            }).compact());
            cursor += row + gap;

            addDrawableChild(new ConsoleButton(x, cursor, half, row, "COPIAR", () -> {
                director.duplicate(selected);
                rebuild();
            }).compact());
            addDrawableChild(new ConsoleButton(x + half + gap, cursor, half, row, "BORRAR", () -> {
                director.remove(selected);
                selected = Math.max(0, selected - 1);
                rebuild();
            }).compact().accent(Console.TALLY));
            cursor += row + gap + 8;
        }

        generalTop = cursor;

        addDrawableChild(new ConsoleButton(x, cursor, width, row, "+ CAMARA AQUI", () -> {
            director.addHere(null);
            selected = director.cameras().size() - 1;
            rebuild();
        }).compact().accent(Console.OK));
        cursor += row + gap;

        addDrawableChild(new ConsoleButton(x, cursor, width, row, "", director::toggleWindow)
                .label(() -> "Emision  " + (director.window().isOpen() ? "ON" : "OFF"))
                .lit(() -> director.window().isOpen())
                .accent(Console.TALLY));
        cursor += row + gap;

        addDrawableChild(new ConsoleButton(x, cursor, width, row, "", () -> {
            director.settings().autoDirector = !director.settings().autoDirector;
            director.saveSettings();
        }).label(() -> "Realizador  " + (director.settings().autoDirector ? "auto" : "manual"))
                .lit(() -> director.settings().autoDirector));
        cursor += row + gap;

        addDrawableChild(new ConsoleButton(x, cursor, width, row, "", this::cycleGlobalTarget)
                .label(() -> "General  " + director.target().shortLabel()));
    }

    // --------------------------------------------------------------- acciones

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
        if (generalTop > 0) {
            Console.sectionTitle(context, "GENERAL", panelX, generalTop - 9, panelWidth);
        }
        Console.sectionTitle(context, "MULTIVIEWER", margin, 32, 120);
        Console.sectionTitle(context, "PROGRAMA", margin, height - 74, 120);
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
