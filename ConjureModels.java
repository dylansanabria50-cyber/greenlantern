package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.RingPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Los objetos creados con el anillo se ven teñidos de verde en TODOS lados
 * (inventario, mano, suelo, marcos): se envuelve el modelo de cada objeto y, si el objeto
 * es un constructo, sus caras usan un indice de tinte especial que pinta de verde.
 */
public class ConjureModels {
    public static final int TINT_INDEX = 99;
    public static final int GREEN = 0x4DFF80;

    /** Modelo teñido: mismas caras que el original pero con indice de tinte 99. */
    public static class TintedModel extends BakedModelWrapper<BakedModel> {
        public TintedModel(BakedModel original) { super(original); }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            List<BakedQuad> in = originalModel.getQuads(state, side, rand);
            List<BakedQuad> out = new ArrayList<>(in.size());
            for (BakedQuad q : in) {
                out.add(new BakedQuad(q.getVertices(), TINT_INDEX, q.getDirection(), q.getSprite(), q.isShade()));
            }
            return out;
        }

        public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack ps, boolean leftHand) {
            originalModel.applyTransform(ctx, ps, leftHand);
            return this;
        }

        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }

        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return List.of(GreenRender.ITEM_GLASS);
        }
    }

    /** Envuelve cada modelo de objeto: si el objeto es un constructo devuelve la version teñida. */
    public static class ConjureModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                BakedModel resolved = originalModel.getOverrides().resolve(originalModel, stack, level, entity, seed);
                if (resolved == null) return null;
                return RingPowers.isConjured(stack) ? new TintedModel(resolved) : resolved;
            }
        };

        public ConjureModel(BakedModel original) { super(original); }

        @Override
        public ItemOverrides getOverrides() { return overrides; }
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onModify(ModelEvent.ModifyBakingResult e) {
            Map<ResourceLocation, BakedModel> models = e.getModels();
            for (ResourceLocation key : new ArrayList<>(models.keySet())) {
                if (key instanceof ModelResourceLocation mrl && "inventory".equals(mrl.getVariant())) {
                    BakedModel m = models.get(key);
                    if (m != null) models.put(key, new ConjureModel(m));
                }
            }
        }

        @SubscribeEvent
        public static void onItemColors(RegisterColorHandlersEvent.Item e) {
            for (Item item : ForgeRegistries.ITEMS) {
                try {
                    if (e.getItemColors().getColor(new ItemStack(item), 0) != -1) continue; // ya tiene su propio color
                } catch (Exception ignored) { }
                e.register((stack, idx) -> idx == TINT_INDEX && RingPowers.isConjured(stack) ? GREEN : -1, item);
            }
        }
    }
}
