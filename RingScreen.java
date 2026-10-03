package com.example.greenlantern.client;

import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Ventana del anillo: rejilla de objetos con buscador, parecida al inventario creativo. */
public class RingScreen extends Screen {
    private static final int COLS = 9, ROWS = 6, CELL = 20;
    private static List<ItemStack> ALL;

    private final List<ItemStack> shown = new ArrayList<>();
    private final int panelW = COLS * CELL + 16;
    private final int panelH = ROWS * CELL + 84;
    private EditBox search;
    private int scroll = 0;
    private int targetSlot = 0;
    private int left, top;

    public RingScreen() {
        super(Component.literal("Anillo de Poder"));
    }

    private static void buildAll() {
        if (ALL != null) return;
        List<ItemStack> list = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (RingPowers.isAllowed(item)) list.add(new ItemStack(item));
        }
        list.sort(Comparator.comparing(s -> ForgeRegistries.ITEMS.getKey(s.getItem()).toString()));
        ALL = list;
    }

    @Override
    protected void init() {
        buildAll();
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        String prev = search != null ? search.getValue() : "";
        search = new EditBox(this.font, left + 8, top + 20, panelW - 16, 14, Component.literal("Buscar"));
        search.setMaxLength(40);
        search.setValue(prev);
        search.setResponder(t -> applyFilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        applyFilter();
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (ItemStack s : ALL) {
            if (q.isEmpty()
                    || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || ForgeRegistries.ITEMS.getKey(s.getItem()).getPath().contains(q)) {
                shown.add(s);
            }
        }
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xFF2E6B45);
        g.fill(left, top, left + panelW, top + panelH, 0xFF0E1F15);
        g.drawString(this.font, this.title, left + 8, top + 6, 0x55FF77, false);

        ItemStack hovered = ItemStack.EMPTY;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                int x = left + 8 + c * CELL;
                int y = top + 40 + r * CELL;
                boolean over = mx >= x && mx < x + CELL && my >= y && my < y + CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, over ? 0xFF2F6B45 : 0xFF163325);
                if (idx < shown.size()) {
                    ItemStack st = shown.get(idx);
                    g.renderItem(st, x + 1, y + 1);
                    ClientEvents.tint(g, x + 1, y + 1, 16);
                    if (over) hovered = st;
                }
            }
        }
        // ranuras de favoritos (lado izquierdo)
        int fx = left - 30;
        g.fill(fx - 3, top - 2, fx + CELL + 3, top + 40 + Favorites.SLOTS * (CELL + 4) + 2, 0xFF2E6B45);
        g.fill(fx - 1, top, fx + CELL + 1, top + 40 + Favorites.SLOTS * (CELL + 4), 0xFF0E1F15);
        g.drawString(this.font, "Fav", fx + 1, top + 24, 0x55FF77, false);
        for (int i = 0; i < Favorites.SLOTS; i++) {
            int y = top + 40 + i * (CELL + 4);
            boolean over = mx >= fx && mx < fx + CELL && my >= y && my < y + CELL;
            g.fill(fx - 1, y - 1, fx + CELL, y + CELL, i == targetSlot ? 0xFF55FF88 : 0xFF163325);
            g.fill(fx, y, fx + CELL - 1, y + CELL - 1, over ? 0xFF2F6B45 : 0xFF163325);
            ItemStack f = Favorites.get(i);
            if (f.isEmpty()) {
                g.drawString(this.font, "" + (i + 1), fx + 7, y + 6, 0x446655, false);
            } else {
                g.renderItem(f, fx + 1, y + 1);
                ClientEvents.tint(g, fx + 1, y + 1, 16);
                if (over) hovered = f;
            }
        }
        int ty = top + panelH - 40;
        g.drawString(this.font, "Clic: 1 | Mayus+clic: pila", left + 8, ty, 0x88CC99, false);
        g.drawString(this.font, "Rueda: desplazar", left + 8, ty + 10, 0x88CC99, false);
        g.drawString(this.font, "Clic der. en objeto: favorito", left + 8, ty + 20, 0x88CC99, false);
        g.drawString(this.font, "Clic der. en ranura: elegirla", left + 8, ty + 30, 0x88CC99, false);

        super.render(g, mx, my, pt);
        if (!hovered.isEmpty()) g.renderTooltip(this.font, hovered, mx, my);
    }

    private ItemStack itemAt(double mx, double my) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = left + 8 + c * CELL;
                int y = top + 40 + r * CELL;
                if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) {
                    int idx = (scroll + r) * COLS + c;
                    return idx < shown.size() ? shown.get(idx) : ItemStack.EMPTY;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private int favAt(double mx, double my) {
        int fx = left - 30;
        for (int i = 0; i < Favorites.SLOTS; i++) {
            int y = top + 40 + i * (CELL + 4);
            if (mx >= fx && mx < fx + CELL && my >= y && my < y + CELL) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int fs = favAt(mx, my);
        if (fs >= 0) {
            if (button == 1) {
                if (hasShiftDown()) Favorites.clear(fs); else targetSlot = fs;
                return true;
            }
            if (button == 0) {
                ItemStack f = Favorites.get(fs);
                if (!f.isEmpty()) { Favorites.conjure(f, hasShiftDown()); this.onClose(); }
                return true;
            }
        }
        ItemStack st = itemAt(mx, my);
        if (!st.isEmpty() && button == 1) {
            Favorites.set(targetSlot, st.getItem());
            targetSlot = (targetSlot + 1) % Favorites.SLOTS;
            return true;
        }
        if (!st.isEmpty() && button == 0) {
            int amount = hasShiftDown() ? st.getMaxStackSize() : 1;
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ConjurePacket(ForgeRegistries.ITEMS.getKey(st.getItem()), amount));
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
