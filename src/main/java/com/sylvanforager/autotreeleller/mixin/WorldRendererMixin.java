package com.sylvanforager.autotreeleller.mixin;

import com.sylvanforager.autotreeleller.render.OverlayRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.world.debug.gizmo.GizmoCollector;
import net.minecraft.world.debug.gizmo.GizmoCollectorImpl;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    /** Shadowed reference to WorldRenderer's gizmo collector. */
    @Shadow
    private GizmoCollectorImpl gizmoCollector;

    /**
     * Inject at the HEAD of collectGizmos() — before vanilla gizmos are
     * extracted — so our GizmoDrawing calls are included in the same pass.
     */
    @Inject(method = "collectGizmos", at = @At("HEAD"))
    private void onCollectGizmos(CallbackInfo ci) {
        try (GizmoDrawing.CollectorScope scope =
                GizmoDrawing.using((GizmoCollector) this.gizmoCollector)) {
            OverlayRenderer.drawGizmos();
        }
    }
}
