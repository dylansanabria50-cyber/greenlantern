package com.example.greenlantern.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Tipo de dibujado translucido (como cristal verde) para los objetos creados con el anillo. */
public class GreenRender extends RenderType {
    private GreenRender(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean a, boolean s, Runnable su, Runnable cl) {
        super(n, f, m, b, a, s, su, cl);
    }

    private static final RenderStateShard.TexturingStateShard GLASS = new RenderStateShard.TexturingStateShard(
            "greenlantern_glass",
            () -> RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.6F),
            () -> RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F));

    public static final RenderType ITEM_GLASS = RenderType.create("greenlantern_item_glass",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setCullState(NO_CULL)
                    .setTexturingState(GLASS)
                    .createCompositeState(true));
}
