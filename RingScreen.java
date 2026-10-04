package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Ventana del anillo: estilo del inventario creativo (pestanas, buscador, rejilla, barra) en verde Linterna. */
public class RingScreen extends Screen {
    private static final int COLS = 11, ROWS = 6, CELL = 20, TAB_H = 22;
    private static final int GREEN_BRIGHT = 0xFF55FF88, GREEN_EDGE = 0xFF2E6B45, GREEN_MID = 0xFF163325, GREEN_DARK = 0xFF0E1F15;

    private record Tab(Component name, ItemStack icon, List<ItemStack> items) { }

    private static List<ItemStack> ALL;
    private static List<Tab> TABS;

    private final List<ItemStack> shown = new ArrayList<>();
    private final int panelW = COLS * CELL + 26;
    private final int panelH = ROWS * CELL + 84;
    private EditBox search;
    private int scroll = 0;
    private int selTab = 0;
    private int targetSlot = 0;
    private int left, top;
    private boolean dragBar = false;

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

        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(Component.literal("Todos"), new ItemStack(GreenLanternMod.POWER_RING.get()), ALL));
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            }
            for (CreativeModeTab t : CreativeModeTabs.tabs()) {
                if (t.getType() != CreativeModeTab.Type.CATEGORY) continue;
                LinkedHashSet<Item> seen = new LinkedHashSet<>();
                List<ItemStack> items = new ArrayList<>();
                for (ItemStack s : t.getDisplayItems()) {
                    if (RingPowers.isAllowed(s.getItem()) && seen.add(s.getItem())) items.add(new ItemStack(s.getItem()));
                }
                if (!items.isEmpty()) tabs.add(new Tab(t.getDisplayName(), t.getIconItem(), items));
            }
        } catch (Exception ignored) { }
        TABS = tabs;
    }

    @Override
    protected void init() {
        buildAll();
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2 + TAB_H / 2;
        String prev = search != null ? search.getValue() : "";
        search = new EditBox(this.font, left + 8, top + 20, panelW - 24, 14, Component.literal("Buscar"));
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
        List<ItemStack> src = q.isEmpty() ? TABS.get(Math.min(selTab, TABS.size() - 1)).items() : ALL;
        for (ItemStack s : src) {
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

    private int tabW() {
        return Math.min(26, panelW / Math.max(1, TABS.size()));
    }

    private int tabAt(double mx, double my) {
        if (my < top - TAB_H || my >= top || mx < left) return -1;
        int i = (int) ((mx - left) / tabW());
        return i >= 0 && i < TABS.size() ? i : -1;
    }

    private int barX() { return left + 8 + COLS * CELL + 3; }

    private int barY() { return top + 40; }

    private int barH() { return ROWS * CELL; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        int tw = tabW();
        Component tabTip = null;
        for (int i = 0; i < TABS.size(); i++) {
            int x = left + i * tw;
            boolean sel = i == selTab;
            int y0 = top - TAB_H + (sel ? 0 : 3);
            g.fill(x, y0 - 1, x + tw - 1, top + (sel ? 2 : 0), sel ? GREEN_BRIGHT : GREEN_EDGE);
            g.fill(x + 1, y0, x + tw - 2, top + (sel ? 2 : 0), sel ? 0xFF1F5C38 : GREEN_DARK);
            Tab tb = TABS.get(i);
            g.renderItem(tb.icon(), x + (tw - 16) / 2, y0 + 3);
            if (mx >= x && mx < x + tw - 1 && my >= top - TAB_H && my < top) tabTip = tb.name();
        }

        g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, GREEN_EDGE);
        g.fill(left, top, left + panelW, top + panelH, GREEN_DARK);
        String q = search == null ? "" : search.getValue().trim();
        Component title = q.isEmpty() ? TABS.get(selTab).name() : Component.literal("Busqueda");
        g.drawString(this.font, title, left + 8, top + 6, 0x55FF77, false);

        ItemStack hovered = ItemStack.EMPTY;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                int x = left + 8 + c * CELL;
                int y = top + 40 + r * CELL;
                boolean over = mx >= x && mx < x + CELL && my >= y && my < y + CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, over ? 0xFF2F6B45 : GREEN_MID);
                if (idx < shown.size()) {
                    ItemStack st = shown.get(idx);
                    g.renderItem(st, x + 1, y + 1);
                    ClientEvents.tint(g, x + 1, y + 1, 16);
                    if (over) hovered = st;
                }
            }
        }

        int bx = barX(), by = barY(), bh = barH();
        g.fill(bx, by, bx + 6, by + bh, GREEN_MID);
        int totalRows = Math.max(ROWS, (shown.size() + COLS - 1) / COLS);
        int handleH = Math.max(12, bh * ROWS / totalRows);
        int ms = maxScroll();
        int hy = ms == 0 ? by : by + (bh - handleH) * scroll / ms;
        g.fill(bx, hy, bx + 6, hy + handleH, dragBar ? GREEN_BRIGHT : 0xFF3FA866);

        int fx = left - 30;
        g.fill(fx - 3, top - 2, fx + CELL + 3, top + 40 + Favorites.SLOTS * (CELL + 4) + 2, GREEN_EDGE);
        g.fill(fx - 1, top, fx + CELL + 1, top + 40 + Favorites.SLOTS * (CELL + 4), GREEN_DARK);
        g.drawString(this.font, "Fav", fx + 1, top + 24, 0x55FF77, false);
        for (int i = 0; i < Favorites.SLOTS; i++) {
            int y = top + 40 + i * (CELL + 4);
            boolean over = mx >= fx && mx < fx + CELL && my >= y && my < y + CELL;
            g.fill(fx - 1, y - 1, fx + CELL, y + CELL, i == targetSlot ? GREEN_BRIGHT : GREEN_MID);
            g.fill(fx, y, fx + CELL - 1, y + CELL - 1, over ? 0xFF2F6B45 : GREEN_MID);
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
        else if (tabTip != null) g.renderTooltip(this.font, tabTip, mx, my);
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

    private void dragTo(double my) {
        int ms = maxScroll();
        if (ms == 0) return;
        double f = (my - barY()) / (double) barH();
        scroll = Mth.clamp((int) Math.round(f * ms), 0, ms);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int tb = tabAt(mx, my);
        if (tb >= 0 && button == 0) {
            selTab = tb;
            if (search != null) search.setValue("");
            applyFilter();
            return true;
        }
        if (button == 0 && mx >= barX() && mx < barX() + 6 && my >= barY() && my < barY() + barH()) {
            dragBar = true;
            dragTo(my);
            return true;
        }
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
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragBar) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragBar = false;
        return super.mouseReleased(mx, my, button);
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
