package com.example.greenlantern;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class GlTweaks {
  static final double FACTOR = 0.3;
  static final Pattern GL = Pattern.compile("willpower|lantern|halcorps", Pattern.CASE_INSENSITIVE);
  static Class<?> ASSIGN;
  static Method GET_CD, SET_CD, EV_ABILITY, EV_PLAYER;
  static final Map<UUID, Map<Object, Integer>> LAST = new HashMap<>();

  @SuppressWarnings("unchecked")
  public static void init() {
    try {
      ClassLoader cl = GlTweaks.class.getClassLoader();
      ASSIGN = Class.forName("com.tihyo.legends.abilities.assignable.LegendsAssignableAbility", false, cl);
      GET_CD = ASSIGN.getMethod("getCooldown", LivingEntity.class);
      SET_CD = ASSIGN.getMethod("setCooldown", LivingEntity.class, int.class, boolean.class);
      Class<?> pre = Class.forName("com.tihyo.legends.abilities.assignable.ActivateLegendsAbilityEvent$Pre", false, cl);
      EV_ABILITY = pre.getMethod("getAbility");
      EV_PLAYER = pre.getMethod("getPlayer");
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class<Event>) (Class<?>) pre, GlTweaks::onPre);
      MinecraftForge.EVENT_BUS.register(GlTweaks.class);
    } catch (Throwable t) {
      ASSIGN = null;
    }
  }

  static void onPre(Event e) {
    try {
      Object ab = EV_ABILITY.invoke(e);
      if (!ab.getClass().getSimpleName().equals("WillPowerBeamAbility")) return;
      if (e.isCancelable()) e.setCanceled(true);
      Object p = EV_PLAYER.invoke(e);
      if (p instanceof Player pl && pl.level().isClientSide && FMLEnvironment.dist == Dist.CLIENT) ClientHook.openRing();
    } catch (Throwable t) {
    }
  }

  static boolean isGl(Object eff) {
    String n = eff.getClass().getName();
    return n.contains(".superheroes.abilities.") && GL.matcher(eff.getClass().getSimpleName()).find();
  }

  @SubscribeEvent
  public static void onTick(TickEvent.PlayerTickEvent e) {
    if (ASSIGN == null || e.phase != TickEvent.Phase.END || e.side.isClient()) return;
    Player p = e.player;
    Map<Object, Integer> m = LAST.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
    try {
      for (MobEffect eff : p.getActiveEffectsMap().keySet()) {
        if (!ASSIGN.isInstance(eff) || !isGl(eff)) continue;
        int cd = (Integer) GET_CD.invoke(eff, p);
        Integer last = m.get(eff);
        if (last != null && cd > last && cd >= 6) {
          int n = Math.max(1, (int) Math.round(cd * FACTOR));
          SET_CD.invoke(eff, p, n, true);
          cd = n;
        }
        m.put(eff, cd);
      }
    } catch (Throwable t) {
    }
  }

  @SubscribeEvent
  public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
    LAST.remove(e.getEntity().getUUID());
  }
}
