package gt.edu.uvg.universityguide.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Tema Material verde oscuro compartido por las ventanas del guía. */
public final class MaterialGuideTheme {
    // Evita crear instancias de esta clase de utilidades.
    private MaterialGuideTheme() {
    }

    /** Full-window night green. */
    public static final int BACKGROUND = 0xFF101412;
    /** Card surface. */
    public static final int SURFACE = 0xFF1B211E;
    /** Superficie clara para botones y campos. */
    public static final int SURFACE_HIGH = 0xFF262E2A;
    /** Thin card border. */
    public static final int OUTLINE = 0xFF3A4640;
    /** Verde principal para títulos y botón de guardar. */
    public static final int PRIMARY = 0xFF7BE3A8;
    /** Texto sobre el botón verde. */
    public static final int ON_PRIMARY = 0xFF003823;
    /** Grey text used on green buttons: almost black so it stands out on light green. */
    public static final int ON_GREEN_BUTTON = 0xFF1B211E;
    /** Texto blanco suave con tono verde. */
    public static final int ON_SURFACE = 0xFFE9F2EC;
    /** Texto secundario gris verdoso. */
    public static final int MUTED = 0xFF8FA89A;
    /** Switched-off button fill. */
    public static final int DISABLED_BG = 0xFF2A322E;
    /** Switched-off button text. */
    public static final int DISABLED_FG = 0xFF6B7771;

    /** Dibuja una tarjeta plana con borde fino y esquinas cuadradas. */
    public static void card(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SURFACE);
        graphics.fill(x, y, x + width, y + 1, OUTLINE);
        graphics.fill(x, y + height - 1, x + width, y + height, OUTLINE);
        graphics.fill(x, y, x + 1, y + height, OUTLINE);
        graphics.fill(x + width - 1, y, x + width, y + height, OUTLINE);
    }

    /** Cabecera con una línea verde inferior. */
    public static void header(GuiGraphics graphics, int x, int y, int width, int height) {
        card(graphics, x, y, width, height);
        graphics.fill(x + 12, y + height - 3, x + width - 12, y + height - 1, PRIMARY);
    }

    /** Small green pill behind a short label such as a distance. */
    public static void chip(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF1E3A2A);
        graphics.fill(x, y + height - 1, x + width, y + height, PRIMARY);
    }

    /** Acorta el texto con puntos suspensivos para que quepa. */
    public static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "...";
        int dotsWidth = font.width(dots);
        int length = text.length();
        while (length > 0 && font.width(text.substring(0, length)) + dotsWidth > maxWidth) {
            length--;
        }
        return text.substring(0, Math.max(0, length)) + dots;
    }

    /** Botón de estilo Material, verde al destacar y oscuro en otro caso. */
    public static final class M3Button extends Button {
        private final boolean filled;

        // Configura la acción y el estilo del botón.
        public M3Button(int x, int y, int width, int height, Component message, OnPress onPress, boolean filled) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.filled = filled;
        }

        // Crea un botón con el color principal.
        public static M3Button filled(int x, int y, int width, int height, Component message, OnPress onPress) {
            return new M3Button(x, y, width, height, message, onPress, true);
        }

        // Crea un botón con fondo discreto.
        public static M3Button tonal(int x, int y, int width, int height, Component message, OnPress onPress) {
            return new M3Button(x, y, width, height, message, onPress, false);
        }

        // Dibuja el botón según foco y disponibilidad.
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            int back;
            int frame = OUTLINE;
            if (!active) {
                back = DISABLED_BG;
                frame = DISABLED_BG;
            } else if (filled) {
                back = hovered ? 0xFF8FF0B6 : PRIMARY;
            } else {
                back = hovered ? 0xFF313B36 : SURFACE_HIGH;
                frame = hovered ? PRIMARY : OUTLINE;
            }
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), back);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, frame);
            graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), frame);
            graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), frame);
            graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), frame);
            int textColor;
            if (!active) {
                textColor = DISABLED_FG;
            } else if (filled) {
                textColor = ON_GREEN_BUTTON;
            } else if (hovered) {
                textColor = PRIMARY;
            } else {
                textColor = ON_SURFACE;
            }
            // Usa sombra normal para mantener el texto legible sobre verde.
            // Usa letras sin sombra en botones verdes para mejorar la legibilidad.
            Font font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            graphics.drawString(font, label,
                    getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - 8) / 2, textColor, !filled);
        }
    }
}
