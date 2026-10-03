package com.example.greenlantern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PowerRingItem extends Item {
    public PowerRingItem(Properties props) { super(props); }

    /** Shift + clic derecho: recarga el anillo con el juramento (cooldown de 30 s). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) RingPowers.recharge(player);
            player.getCooldowns().addCooldown(this, 600);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        // clic derecho: ponerse el anillo (va a la primera ranura de armadura libre)
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            if (player.getItemBySlot(slot).isEmpty()) {
                if (!level.isClientSide) {
                    ItemStack one = stack.copy();
                    one.setCount(1);
                    player.setItemSlot(slot, one);
                    stack.shrink(1);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    /** El anillo se puede colocar en cualquier ranura de armadura. */
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, Entity entity) {
        return slot.getType() == EquipmentSlot.Type.ARMOR;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.literal("Clic derecho: ponertelo (o ponelo en una ranura de armadura)").withStyle(ChatFormatting.GREEN));
        tips.add(Component.literal("G: volar | R: crear objetos | V: burbuja | B: muro").withStyle(ChatFormatting.GRAY));
        tips.add(Component.literal("Shift + clic derecho: recargar voluntad").withStyle(ChatFormatting.DARK_GREEN));
    }
}
