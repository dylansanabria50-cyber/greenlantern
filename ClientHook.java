package com.example.glsolo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ClientHook {
  public static void openRing() {
    try {
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
        try {
          if (mc.screen != null || mc.player == null) return;
          Class<?> c = Class.forName("com.example.greenlantern.client.RingScreen");
          mc.setScreen((Screen) c.getConstructor().newInstance());
        } catch (Throwable t) {
        }
      });
    } catch (Throwable t) {
    }
  }
}
