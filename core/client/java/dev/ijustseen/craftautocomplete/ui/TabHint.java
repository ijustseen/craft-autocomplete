package dev.ijustseen.craftautocomplete.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

/**
 * The Tab key picture under the result slot. Hidden when it would cover another slot.
 * The picture is always the Tab key: the bind itself stays Tab.
 */
final class TabHint {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("craft-autocomplete", "textures/gui/tab.png");
    /** Pixel size of {@code textures/gui/tab.png}. Change both if the file changes size. */
    private static final int WIDTH = 20;
    private static final int HEIGHT = 11;

    private TabHint() {}

    static void draw(StationView view, GuiGraphicsExtractor graphics) {
        AbstractRecipeBookScreen<?> screen = view.screen();
        Slot result = view.result();
        int x = result.x + 8 - WIDTH / 2;
        int y = result.y + 16 + (view.bigResult() ? 6 : 3);
        if (!fits(view, x, y)) {
            return;
        }
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                screen.leftPos + x,
                screen.topPos + y,
                0f,
                0f,
                WIDTH,
                HEIGHT,
                WIDTH,
                HEIGHT
        );
    }

    private static boolean fits(StationView view, int x, int y) {
        AbstractRecipeBookScreen<?> screen = view.screen();
        if (x < 0 || y < 0 || x + WIDTH > screen.imageWidth || y + HEIGHT > screen.imageHeight) {
            return false;
        }
        for (Slot slot : view.menu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            boolean apart = x + WIDTH <= slot.x - 1
                    || slot.x + 17 <= x
                    || y + HEIGHT <= slot.y - 1
                    || slot.y + 17 <= y;
            if (!apart) {
                return false;
            }
        }
        return true;
    }
}
