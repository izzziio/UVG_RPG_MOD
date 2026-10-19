package gt.edu.uvg.universityguide.client.screen;

import gt.edu.uvg.universityguide.network.TourPayloads;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dark-green destination picker: header card, one soft card per place and round-feel buttons. */
public final class DestinationScreen extends Screen {
    private static final int MAX_PAGE_SIZE = 7;
    private int pageSize = MAX_PAGE_SIZE;
    private static final int ROW_HEIGHT = 26;
    private final int guideEntityId;
    private final String guideName;
    private final List<TourPayloads.StopSummary> stops;
    private int page;

    // Inicializa la pantalla con sus datos de apertura.
    public DestinationScreen(TourPayloads.OpenDestinations payload) {
        super(Component.translatable("screen.universityguide.destinations"));
        this.guideEntityId = payload.guideEntityId();
        this.guideName = payload.guideName();
        this.stops = payload.stops();
    }

    // Limita el ancho del contenido a la ventana.
    private int contentWidth() {
        return Math.min(340, width - 30);
    }

    // Centra horizontalmente el contenido.
    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    // Sitúa la primera fila debajo del encabezado.
    private int firstRowY() {
        return 92;
    }

    /** Build only the current page's controls, with footer positions bounded by the available height. */
    @Override
    protected void init() {
        int contentWidth = contentWidth();
        int left = contentLeft();
        int firstVisible = page * pageSize;
        pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, (height - 32 - firstRowY() - 12) / ROW_HEIGHT));
        page = Math.min(firstVisible / pageSize, Math.max(0, (stops.size() - 1) / pageSize));
        int start = page * pageSize;
        int end = Math.min(start + pageSize, stops.size());

        for (int index = start; index < end; index++) {
            TourPayloads.StopSummary stop = stops.get(index);
            String label = MaterialGuideTheme.fit(font,
                    stop.name() + "  (" + stop.distance() + " m)", contentWidth - 32);
            addRenderableWidget(MaterialGuideTheme.M3Button.tonal(
                    left + 8, firstRowY() + (index - start) * ROW_HEIGHT + 3, contentWidth - 16, 20,
                    Component.literal(label), button -> select(stop)));
        }

        int footerY = Math.min(height - 32, firstRowY() + pageSize * ROW_HEIGHT + 12);
        if (page > 0) {
            addRenderableWidget(MaterialGuideTheme.M3Button.tonal(
                    left, footerY, 48, 22, Component.literal("<"), button -> changePage(-1)));
        }
        if ((page + 1) * pageSize < stops.size()) {
            addRenderableWidget(MaterialGuideTheme.M3Button.tonal(
                    left + contentWidth - 48, footerY, 48, 22, Component.literal(">"), button -> changePage(1)));
        }
        addRenderableWidget(MaterialGuideTheme.M3Button.filled(
                width / 2 - 70, footerY, 140, 22,
                Component.translatable("gui.cancel"), button -> onClose()));
    }

    /** Send intent only; the server rechecks guide distance, availability and destination validity. */
    private void select(TourPayloads.StopSummary stop) {
        PacketDistributor.sendToServer(new TourPayloads.SelectDestination(guideEntityId, stop.id()));
        onClose();
    }

    /** Rebuild buttons from the existing snapshot instead of requesting another network response. */
    private void changePage(int difference) {
        page += difference;
        rebuildWidgets();
    }

    /** Dark background plus header and list cards behind the buttons. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MaterialGuideTheme.BACKGROUND);
        int contentWidth = contentWidth();
        int left = contentLeft();
        MaterialGuideTheme.header(graphics, left, 12, contentWidth, 62);
        if (!stops.isEmpty()) {
            int start = page * pageSize;
            int end = Math.min(start + pageSize, stops.size());
            int rows = end - start;
            if (rows > 0) {
                MaterialGuideTheme.card(graphics, left, firstRowY() - 6, contentWidth, rows * ROW_HEIGHT + 12);
            }
        }
    }

    /** Draw the header texts, hint, empty-state message and page count around the buttons. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int contentWidth = contentWidth();
        int left = contentLeft();
        graphics.drawCenteredString(font,
                Component.literal(MaterialGuideTheme.fit(font, title.getString(), contentWidth - 32)),
                width / 2, 22, MaterialGuideTheme.PRIMARY);
        graphics.drawCenteredString(font,
                Component.literal(MaterialGuideTheme.fit(font, guideName, contentWidth - 32)),
                width / 2, 36, MaterialGuideTheme.ON_SURFACE);
        String hint = Component.translatable("screen.universityguide.choose_hint").getString();
        graphics.drawCenteredString(font,
                Component.literal(MaterialGuideTheme.fit(font, hint, contentWidth - 16)),
                width / 2, 52, MaterialGuideTheme.MUTED);
        if (stops.isEmpty()) {
            MaterialGuideTheme.card(graphics, left, height / 2 - 24, contentWidth, 48);
            graphics.drawCenteredString(font, Component.translatable("screen.universityguide.no_destinations"),
                    width / 2, height / 2 - 4, MaterialGuideTheme.MUTED);
        } else {
            int pages = (stops.size() + pageSize - 1) / pageSize;
            graphics.drawString(font, Component.translatable("screen.universityguide.page", page + 1, pages),
                    8, height - 12, MaterialGuideTheme.MUTED, false);
        }
    }

    /** Keep the world and multiplayer tour running while this screen is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
