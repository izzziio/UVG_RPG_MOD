package gt.edu.uvg.universityguide.client.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Dark-green arrival card: green title header, soft reading card and a green progress bar. */
public final class ArrivalScreen extends Screen {
    private final String description;
    private List<FormattedCharSequence> lines = List.of();
    private int scrollLine;
    private int visibleLines;

    // Inicializa la pantalla con sus datos de apertura.
    public ArrivalScreen(String title, String description) {
        super(Component.literal(title));
        this.description = description;
    }

    // Limita el ancho del contenido a la ventana.
    private int contentWidth() {
        return Math.min(520, width - 30);
    }

    // Centra horizontalmente el contenido.
    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    /** Wrap the explanation to the current screen width and determine how many lines fit. */
    @Override
    protected void init() {
        lines = font.split(Component.literal(description), Math.min(480, width - 70));
        visibleLines = Math.max(3, (height - 170) / 11);
        addRenderableWidget(MaterialGuideTheme.M3Button.filled(
                width / 2 - 80, height - 34, 160, 22,
                Component.translatable("gui.done"), button -> onClose()));
    }

    /** Dark background plus title header and reading card behind the text. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MaterialGuideTheme.BACKGROUND);
        int contentWidth = contentWidth();
        int left = contentLeft();
        MaterialGuideTheme.header(graphics, left, 10, contentWidth, 40);
        MaterialGuideTheme.card(graphics, left, 58, contentWidth, height - 58 - 48);
    }

    /** Draw the title, the visible slice of text, the progress bar and the scroll hint. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int contentWidth = contentWidth();
        int left = contentLeft();
        graphics.drawCenteredString(font,
                Component.literal(MaterialGuideTheme.fit(font, title.getString(), contentWidth - 32)),
                width / 2, 24, MaterialGuideTheme.PRIMARY);
        int textLeft = left + 16;
        int end = Math.min(lines.size(), scrollLine + visibleLines);
        int y = 70;
        for (int index = scrollLine; index < end; index++) {
            graphics.drawString(font, lines.get(index), textLeft, y, MaterialGuideTheme.ON_SURFACE, false);
            y += 11;
        }
        if (lines.size() > visibleLines) {
            int barX = left + contentWidth - 18;
            int barTop = 70;
            int barHeight = Math.max(30, visibleLines * 11);
            graphics.fill(barX, barTop, barX + 4, barTop + barHeight, MaterialGuideTheme.SURFACE_HIGH);
            float progress = lines.size() <= 1 ? 0 : (float) scrollLine / (lines.size() - visibleLines);
            int knobHeight = Math.max(10, barHeight * visibleLines / Math.max(1, lines.size()));
            int knobY = barTop + Math.round((barHeight - knobHeight) * progress);
            graphics.fill(barX, knobY, barX + 4, knobY + knobHeight, MaterialGuideTheme.PRIMARY);
            graphics.drawCenteredString(font,
                    Component.translatable("screen.universityguide.scroll_hint", scrollLine + 1, lines.size()),
                    width / 2, height - 50, MaterialGuideTheme.MUTED);
        } else if (!lines.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.universityguide.scroll_hint", 1, lines.size()),
                    width / 2, height - 50, MaterialGuideTheme.MUTED);
        }
    }

    /** Clamp scrolling to the wrapped text so the user cannot move beyond the explanation. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maximum = Math.max(0, lines.size() - visibleLines);
        scrollLine = Mth.clamp(scrollLine - (int) Math.signum(scrollY), 0, maximum);
        return true;
    }

    /** Keep the world and multiplayer tour running while this screen is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
