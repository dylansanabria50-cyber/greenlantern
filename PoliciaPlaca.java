package com.example.policia;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

/** Oficios: cada oficio se consigue con su propia placa. Al usarla se consume y el oficio queda fijado para siempre. */
public class PoliciaPlaca {
    /** Placa de policia: se craftea como un escudo, con bloques de oro y una esmeralda en el centro. */
    public static final RegistryObject<Item> PLACA = PoliciaShield.ITEMS.register("placa_policia",
            () -> new JobBadge(new Item.Properties().stacksTo(1), "policia", "policia", "Pulsa B para activar el modo policia"));

    /** Se llama desde el constructor del mod para que el objeto quede registrado a tiempo. */
    public static void init() { }

    public static class JobBadge extends Item {
        final String job, label, hint;

        public JobBadge(Item.Properties p, String job, String label, String hint) {
            super(p);
            this.job = job;
            this.label = label;
            this.hint = hint;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                String cur = PoliciaMod.job(sp);
                if (!cur.isEmpty()) {
                    PoliciaMod.msg(sp, cur.equals(job) ? "Ya eres " + label + ": la placa no sirve de nada" : "Ya tienes un oficio (" + cur + "): solo se puede tener uno");
                    return InteractionResultHolder.fail(st);
                }
                sp.getPersistentData().putString("pol_job", job);
                st.shrink(1);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.6f);
                sp.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.0, sp.getZ(), 24, 0.6, 0.8, 0.6, 0.05);
                PoliciaMod.msg(sp, "Ahora eres " + label + ". " + hint);
                PoliciaMod.sync(sp);
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }
}
