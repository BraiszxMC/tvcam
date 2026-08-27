package com.braiszx.tvcam.gui;

import com.braiszx.tvcam.camera.CameraDirector;
import com.braiszx.tvcam.camera.CameraMode;
import com.braiszx.tvcam.camera.CameraPoint;
import com.braiszx.tvcam.camera.TargetSpec;
import com.braiszx.tvcam.camera.TargetTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * La mesa de realizacion: la lista de planos a la izquierda y todos los ajustes
 * de la camara seleccionada a la derecha.
 *
 * <p>La emision sigue saliendo mientras la mesa esta abierta (los frames de
 * camara no dibujan ninguna pantalla), asi que puedes tocar ajustes viendo el
 * resultado en la ventana de emision en tiempo real.
 */
public final class DeskScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 150;
    private static final int LIVE_WIDTH = 46;
    private static final int PANEL_GAP = 16;
    /** Alto reservado abajo para la barra de controles generales. */
    private static final int BOTTOM_BAR_HEIGHT = 56;

    private static final int LIVE_COLOR = 0xFFFF5555;
    private static final int MUTED = 0xFFAAAAAA;

    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private TextFieldWidget nameField;
    private int selected;
    private int scroll;
    private int visibleRows;

    /** true si la abrio el item de la mano, para cerrarla al guardarlo. */
    private final boolean openedByWand;

    public DeskScreen(boolean openedByWand) {
        super(Text.literal("Mesa de realizacion"));
        this.openedByWand = openedByWand;
        this.selected = Math.max(0, CameraDirector.get().activeIndex());
    }

    public boolean openedByWand() {
        return openedByWand;
    }

    /** La mesa no debe pausar la partida: se retransmite mientras esta abierta. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        CameraDirector director = CameraDirector.get();
        List<CameraPoint> cameras = director.cameras();
        if (selected >= cameras.size()) {
            selected = Math.max(0, cameras.size() - 1);
        }

        int listX = 20;
        int listTop = 46;
        int listBottom = height - BOTTOM_BAR_HEIGHT - 14;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = Math.clamp(scroll, 0, Math.max(0, cameras.size() - visibleRows));
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + visibleRows) {
            scroll = selected - visibleRows + 1;
        }

        rowButtons.clear();
        for (int row = 0; row < visibleRows; row++) {
            int index = scroll + row;
            if (index >= cameras.size()) {
                break;
            }
            CameraPoint camera = cameras.get(index);
            int y = listTop + row * ROW_HEIGHT;
            boolean isSelected = index == selected;
            boolean isLive = index == director.activeIndex();

            String label = (index + 1) + ". " + trim(camera.name(), 14);
            ButtonWidget row0 = ButtonWidget.builder(
                            Text.literal(label).formatted(isSelected ? Formatting.YELLOW : Formatting.WHITE),
                            button -> select(index))
                    .dimensions(listX, y, LIST_WIDTH, 20).build();
            addDrawableChild(row0);
            rowButtons.add(row0);

            addDrawableChild(ButtonWidget.builder(
                            Text.literal(isLive ? "AIRE" : "cortar")
                                    .formatted(isLive ? Formatting.RED : Formatting.GRAY),
                            button -> {
                                director.cut(index);
                                rebuild();
                            })
                    .dimensions(listX + LIST_WIDTH + 4, y, LIVE_WIDTH, 20).build());
        }

        if (cameras.size() > visibleRows) {
            addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> {
                scroll = Math.max(0, scroll - visibleRows);
                rebuild();
            }).dimensions(listX + LIST_WIDTH + LIVE_WIDTH + 8, listTop, 20, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> {
                scroll = Math.min(Math.max(0, cameras.size() - visibleRows), scroll + visibleRows);
                rebuild();
            }).dimensions(listX + LIST_WIDTH + LIVE_WIDTH + 8, listTop + 24, 20, 20).build());
        }

        buildEditor(cameras);
        buildBottomBar();
    }

    private void buildEditor(List<CameraPoint> cameras) {
        CameraDirector director = CameraDirector.get();
        CameraPoint camera = director.at(selected);
        if (camera == null) {
            return;
        }
        int panelX = 20 + LIST_WIDTH + LIVE_WIDTH + PANEL_GAP + 20;
        int panelWidth = Math.min(220, width - panelX - 20);
        int half = (panelWidth - 4) / 2;

        // Nueve filas de ajustes que tienen que caber por encima de la barra de
        // abajo sea cual sea el tamano de la ventana y la escala de la interfaz.
        int top = 46;
        int bottom = height - BOTTOM_BAR_HEIGHT;
        int rows = 9;
        int step = Math.clamp((bottom - top) / rows, 16, 24);
        int buttonHeight = Math.max(12, step - 4);
        int y = top;

        nameField = new TextFieldWidget(textRenderer, panelX, y, panelWidth, buttonHeight,
                Text.literal("Nombre"));
        nameField.setText(camera.name());
        nameField.setChangedListener(value -> {
            if (!value.isBlank()) {
                camera.name = value;
                director.touch();
            }
        });
        addDrawableChild(nameField);
        y += step;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Modo: " + camera.mode().name().toLowerCase()),
                        b -> cycleMode(camera))
                .dimensions(panelX, y, panelWidth, buttonHeight).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Sigue a: " + camera.target().shortLabel()),
                        b -> cycleTarget(camera))
                .dimensions(panelX, y, panelWidth, buttonHeight).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Zoom -"), b -> {
            camera.zoom = Math.clamp(camera.zoom - 0.25f, 1.0f, 10.0f);
            director.touch();
            rebuild();
        }).dimensions(panelX, y, half, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.literal(String.format("x%.2f +", camera.zoom())), b -> {
                            camera.zoom = Math.clamp(camera.zoom + 0.25f, 1.0f, 10.0f);
                            director.touch();
                            rebuild();
                        })
                .dimensions(panelX + half + 4, y, half, buttonHeight).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Zoom auto: " + (camera.autoZoom == null ? "general"
                                : camera.autoZoom ? "si" : "no")),
                        b -> cycleAutoZoom(camera))
                .dimensions(panelX, y, panelWidth, buttonHeight).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Pulso: " + (camera.smoothing == null ? "general"
                                : String.valueOf(camera.smoothing))),
                        b -> cycleSmoothing(camera))
                .dimensions(panelX, y, panelWidth, buttonHeight).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Traer aqui"), b -> {
            director.moveHere(selected);
            rebuild();
        }).dimensions(panelX, y, half, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reapuntar"), b -> {
            director.aimHere(selected);
            rebuild();
        }).dimensions(panelX + half + 4, y, half, buttonHeight).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Subir"), b -> {
            if (director.move(selected, -1)) {
                selected--;
            }
            rebuild();
        }).dimensions(panelX, y, half, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Bajar"), b -> {
            if (director.move(selected, 1)) {
                selected++;
            }
            rebuild();
        }).dimensions(panelX + half + 4, y, half, buttonHeight).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Duplicar"), b -> {
            director.duplicate(selected);
            rebuild();
        }).dimensions(panelX, y, half, buttonHeight).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Borrar").formatted(Formatting.RED), b -> {
                            director.remove(selected);
                            selected = Math.max(0, selected - 1);
                            rebuild();
                        })
                .dimensions(panelX + half + 4, y, half, buttonHeight).build());
    }

    private void buildBottomBar() {
        CameraDirector director = CameraDirector.get();
        int y = height - BOTTOM_BAR_HEIGHT + 8;
        int gap = 6;
        int buttonWidth = (width - 40 - gap * 3) / 4;
        int x = 20;

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Camara aqui"), b -> {
            director.addHere(null);
            selected = director.cameras().size() - 1;
            rebuild();
        }).dimensions(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Ventana: " + (director.window().isOpen() ? "ON" : "OFF")), b -> {
                            director.toggleWindow();
                            rebuild();
                        })
                .dimensions(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Auto: " + (director.settings().autoDirector ? "ON" : "OFF")),
                        b -> {
                            director.settings().autoDirector = !director.settings().autoDirector;
                            director.saveSettings();
                            rebuild();
                        })
                .dimensions(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("General: " + director.target().shortLabel()),
                        b -> {
                            cycleGlobalTarget();
                            rebuild();
                        })
                .dimensions(x, y, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cerrar"), b -> close())
                .dimensions(width / 2 - 50, height - 26, 100, 20).build());
    }

    // ------------------------------------------------------------- acciones

    /** Recorre el objetivo general: pelota -> cada jugador -> nadie. */
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

    private void select(int index) {
        selected = index;
        rebuild();
    }

    private void cycleMode(CameraPoint camera) {
        CameraMode[] modes = CameraMode.values();
        CameraMode next = modes[(camera.mode().ordinal() + 1) % modes.length];
        camera.mode = next;
        if (next == CameraMode.ACOMPANAR) {
            // Mantiene la distancia que hay ahora mismo con su objetivo.
            Entity entity = CameraDirector.get().target().resolve(camera.target());
            camera.setOffset(entity == null ? camera.offset()
                    : camera.pos().subtract(entity.getBoundingBox().getCenter()));
        }
        CameraDirector.get().touch();
        rebuild();
    }

    /**
     * Recorre: general -> pelota -> cada jugador conectado -> nadie. Asi se elige
     * a quien sigue cada camara sin tener que escribir nombres.
     */
    private void cycleTarget(CameraPoint camera) {
        TargetSpec spec = camera.target();
        List<String> players = onlinePlayers();
        switch (spec.kind) {
            case GLOBAL -> camera.target = TargetSpec.ball();
            case PELOTA -> camera.target = players.isEmpty()
                    ? TargetSpec.none() : TargetSpec.player(players.get(0));
            case JUGADOR -> {
                int index = players.indexOf(spec.player);
                if (index >= 0 && index + 1 < players.size()) {
                    camera.target = TargetSpec.player(players.get(index + 1));
                } else {
                    camera.target = TargetSpec.none();
                }
            }
            case NINGUNO -> camera.target = TargetSpec.global();
        }
        CameraDirector.get().touch();
        rebuild();
    }

    private void cycleAutoZoom(CameraPoint camera) {
        if (camera.autoZoom == null) {
            camera.autoZoom = Boolean.TRUE;
        } else if (camera.autoZoom) {
            camera.autoZoom = Boolean.FALSE;
        } else {
            camera.autoZoom = null;
        }
        CameraDirector.get().touch();
        rebuild();
    }

    private void cycleSmoothing(CameraPoint camera) {
        if (camera.smoothing == null) {
            camera.smoothing = 0;
        } else if (camera.smoothing >= 90) {
            camera.smoothing = null;
        } else {
            camera.smoothing = camera.smoothing + 15;
        }
        CameraDirector.get().touch();
        rebuild();
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

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    // -------------------------------------------------------------- dibujo

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("MESA DE REALIZACION").formatted(Formatting.BOLD), width / 2, 16, 0xFFFFFFFF);

        CameraDirector director = CameraDirector.get();
        CameraPoint live = director.activeCamera();
        String status = live == null
                ? "Nada al aire"
                : "AL AIRE: " + (director.activeIndex() + 1) + " · " + live.name();
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 30,
                live == null ? MUTED : LIVE_COLOR);

        CameraPoint camera = director.at(selected);
        if (camera != null) {
            String detail = String.format("%s · sigue a %s · zoom x%.2f",
                    camera.mode().name().toLowerCase(), camera.target().shortLabel(), camera.zoom());
            context.drawTextWithShadow(textRenderer, detail, 20, height - BOTTOM_BAR_HEIGHT - 10, MUTED);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fondo semitransparente: la mesa se usa mirando el juego por detras.
        context.fill(0, 0, width, height, 0xB0101010);
    }
}
