package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/** Escudo balistico: un ShieldItem de verdad (misma forma, pose y animacion de bloqueo que el vanilla) con textura policial. */
public class PoliciaShield extends ShieldItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PoliciaMod.ID);
    public static final RegistryObject<Item> SHIELD = ITEMS.register("escudo_policial",
            () -> new PoliciaShield(new Item.Properties().stacksTo(1)));

    public PoliciaShield(Item.Properties p) {
        super(p);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return Render.get();
            }
        });
    }

    static class Render extends BlockEntityWithoutLevelRenderer {
        static final ResourceLocation TEX = new ResourceLocation(PoliciaMod.ID, "textures/entity/escudo_policial.png");
        static Render INSTANCE;
        ShieldModel model;

        static Render get() {
            if (INSTANCE == null) {
                Minecraft mc = Minecraft.getInstance();
                INSTANCE = new Render(mc);
            }
            return INSTANCE;
        }

        Render(Minecraft mc) {
            super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }

        /** Dibuja el escudo (mismo modelo vanilla) ya transformado por quien llama. */
        static void drawWorld(PoseStack ps, MultiBufferSource buf, int light) {
            Render r = get();
            if (r.model == null) {
                r.model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
            }
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
            r.model.handle().render(ps, vc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            r.model.plate().render(ps, vc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            // en la mano no se dibuja: el escudo grande se pinta delante del cuerpo (tercera persona) o como pantalla (primera persona)
            if (model == null) {
                model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
            }
            ps.pushPose();
            ps.scale(1.0F, -1.0F, -1.0F);
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
            model.handle().render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            model.plate().render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            ps.popPose();
        }
    }
}
