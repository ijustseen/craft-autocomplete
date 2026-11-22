package dev.ijustseen.craftautocomplete.ui;

import net.minecraft.client.renderer.item.TrackingItemStackRenderState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Marks the item renders submitted for the current suggestion.
 * The GUI draws every item opaquely, so the renderer looks the mark up and fades only these.
 */
public final class GhostFade {
    /**
     * 40% opacity on a premultiplied pipeline. RGB is scaled by the same amount as alpha,
     * so the item fades without being mixed toward white.
     */
    public static final int TINT = 0x66666666;

    private static boolean captureNext;
    private static final Set<TrackingItemStackRenderState> GHOSTS = Collections.newSetFromMap(new IdentityHashMap<>());

    private GhostFade() {}

    public static void beginFrame() {
        captureNext = false;
        GHOSTS.clear();
    }

    public static void captureNext() {
        captureNext = true;
    }

    public static void cancelCapture() {
        captureNext = false;
    }

    public static void remember(TrackingItemStackRenderState state) {
        if (!captureNext) {
            return;
        }
        captureNext = false;
        GHOSTS.add(state);
    }

    public static boolean isGhost(TrackingItemStackRenderState state) {
        return GHOSTS.contains(state);
    }
}
