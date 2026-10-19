package gt.edu.uvg.universityguide.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import gt.edu.uvg.universityguide.block.entity.TourStopBlockEntity;
import gt.edu.uvg.universityguide.network.TourPayloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dark-green stop form: outlined boxes, live character counters and a strong green save button. */
public final class StopEditorScreen extends Screen {
    private static final int NAME_LABEL_Y = 62;
    private static final int NAME_BOX_Y = 76;
    private static final int DESCRIPTION_CARD_Y = 106;
    private static final int DESCRIPTION_LABEL_Y = 114;
    private static final int DESCRIPTION_BOX_Y = 128;

    private final TourPayloads.OpenStopEditor payload;
    private EditBox nameBox;
    private MultiLineEditBox descriptionBox;
    private Button saveButton;

    // Inicializa la pantalla con sus datos de apertura.
    public StopEditorScreen(TourPayloads.OpenStopEditor payload) {
        super(Component.translatable("screen.universityguide.edit_stop"));
        this.payload = payload;
    }

    /**
     * Description box without the built-in counter below it. The counter lives
     * at the top right next to the label instead, so it never shows twice.
     */
    private static final class SingleCounterBox extends MultiLineEditBox {
        private static final ResourceLocation SCROLLER =
                ResourceLocation.withDefaultNamespace("widget/scroller");

        // Crea el campo multilínea con un único contador.
        SingleCounterBox(Font font, int x, int y, int width, int height,
                Component placeholder, Component message) {
            super(font, x, y, width, height, placeholder, message);
        }

        // Dibuja el contador del campo de texto.
        @Override
        protected void renderDecorations(GuiGraphics graphics) {
            // Same vanilla scrollbar, but no character counter under the box.
            if (this.scrollbarVisible()) {
                int contentHeight = this.getInnerHeight() + 4;
                int barHeight = Mth.clamp(
                        (int) ((float) (this.getHeight() * this.getHeight()) / (float) contentHeight),
                        32, this.getHeight());
                int maxScroll = this.getMaxScrollAmount();
                int barY = maxScroll == 0 ? this.getY()
                        : Math.max(this.getY(),
                                (int) (this.scrollAmount() * (this.getHeight() - barHeight)) / maxScroll
                                        + this.getY());
                RenderSystem.enableBlend();
                graphics.blitSprite(SCROLLER, this.getX() + this.getWidth(), barY, 8, barHeight);
                RenderSystem.disableBlend();
            }
        }
    }

    // Limita el ancho del contenido a la ventana.
    private int contentWidth() {
        return Math.min(440, width - 30);
    }

    // Centra horizontalmente el contenido.
    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    // Ajusta la altura del campo de descripción.
    private int descriptionHeight() {
        return Math.max(70, Math.min(180, height - 185));
    }

    // Calcula el borde inferior de la descripción.
    private int descriptionCardBottom() {
        return DESCRIPTION_BOX_Y + descriptionHeight() + 10;
    }

    /** Preserve unsaved field values when resizing causes Minecraft to recreate the widgets. */
    @Override
    protected void init() {
        String currentName = nameBox == null ? payload.name() : nameBox.getValue();
        String currentDescription = descriptionBox == null ? payload.description() : descriptionBox.getValue();
        int contentWidth = contentWidth();
        int left = contentLeft();

        nameBox = new EditBox(font, left + 12, NAME_BOX_Y, contentWidth - 24, 20,
                Component.translatable("screen.universityguide.stop_name"));
        nameBox.setMaxLength(TourStopBlockEntity.MAX_NAME_LENGTH);
        nameBox.setValue(currentName);
        nameBox.setTextColor(MaterialGuideTheme.ON_SURFACE);
        nameBox.setResponder(value -> updateSaveState());
        addRenderableWidget(nameBox);

        int boxHeight = descriptionHeight();
        descriptionBox = new SingleCounterBox(font, left + 12, DESCRIPTION_BOX_Y, contentWidth - 24, boxHeight,
                Component.translatable("screen.universityguide.description_placeholder"),
                Component.translatable("screen.universityguide.stop_description"));
        descriptionBox.setCharacterLimit(TourStopBlockEntity.MAX_DESCRIPTION_LENGTH);
        descriptionBox.setValue(currentDescription);
        addRenderableWidget(descriptionBox);

        int buttonsY = Math.min(height - 32, descriptionCardBottom() + 8);
        saveButton = addRenderableWidget(MaterialGuideTheme.M3Button.filled(
                width / 2 - 130, buttonsY, 125, 22,
                Component.translatable("gui.done"), button -> save()));
        addRenderableWidget(MaterialGuideTheme.M3Button.tonal(
                width / 2 + 5, buttonsY, 125, 22,
                Component.translatable("gui.cancel"), button -> onClose()));
        setInitialFocus(nameBox);
        updateSaveState();
    }

    /** Disable submission for blank names; server-side checks still protect the stored data. */
    private void updateSaveState() {
        if (saveButton != null && nameBox != null) {
            saveButton.active = !nameBox.getValue().trim().isBlank();
        }
    }

    /** Send edited text and block position, then close; the server decides whether the edit is permitted. */
    private void save() {
        PacketDistributor.sendToServer(new TourPayloads.UpdateStop(
                payload.pos(), nameBox.getValue(), descriptionBox.getValue()));
        onClose();
    }

    /** Dark background plus header and two soft cards. Labels sit inside their cards. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MaterialGuideTheme.BACKGROUND);
        int contentWidth = contentWidth();
        int left = contentLeft();
        MaterialGuideTheme.header(graphics, left, 10, contentWidth, 34);
        MaterialGuideTheme.card(graphics, left, 54, contentWidth, 58);
        MaterialGuideTheme.card(graphics, left, DESCRIPTION_CARD_Y, contentWidth,
                descriptionCardBottom() - DESCRIPTION_CARD_Y);
    }

    /** Labels and live counters stay aligned with the boxes when the window resizes. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int contentWidth = contentWidth();
        graphics.drawCenteredString(font,
                Component.literal(MaterialGuideTheme.fit(font, title.getString(), contentWidth - 24)),
                width / 2, 21, MaterialGuideTheme.PRIMARY);
        graphics.drawString(font, Component.translatable("screen.universityguide.stop_name"),
                nameBox.getX(), NAME_LABEL_Y, MaterialGuideTheme.PRIMARY, false);
        String nameCounter = nameBox.getValue().length() + "/" + TourStopBlockEntity.MAX_NAME_LENGTH;
        graphics.drawString(font, Component.literal(nameCounter),
                nameBox.getX() + nameBox.getWidth() - font.width(nameCounter),
                NAME_LABEL_Y, MaterialGuideTheme.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.universityguide.stop_description"),
                descriptionBox.getX(), DESCRIPTION_LABEL_Y, MaterialGuideTheme.PRIMARY, false);
        String descriptionCounter =
                descriptionBox.getValue().length() + "/" + TourStopBlockEntity.MAX_DESCRIPTION_LENGTH;
        graphics.drawString(font, Component.literal(descriptionCounter),
                descriptionBox.getX() + descriptionBox.getWidth() - font.width(descriptionCounter),
                DESCRIPTION_LABEL_Y, MaterialGuideTheme.MUTED, false);
    }

    /** Keep the world and multiplayer tour running while this screen is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
