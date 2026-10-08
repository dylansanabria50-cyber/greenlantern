package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Pantalla de habilidades: personaje a la izquierda, rama de habilidades a la derecha. */
public class PoliciaTreeScreen extends Screen {
    static final int PW = 460, PH = 340, LEFT_W = 104;
    static final String[] NAMES = {"Escudo balistico", "REFUERZO", "Tanque", "Helicoptero", "Tanque movil", "Esposas", "Perro K9", "Sirena y torreta", "Dron de vigilancia"};
    static final String[] DESC = {
            "Despliega un escudo antidisturbios durante 12 s. Bloquea los golpes de frente.",
            "Policias de refuerzo que te protegen y atacan a tus enemigos.",
            "Invoca un tanque bajo tus pies. Apunta y dispara con clic derecho una sola vez; luego se retira. Enfriamiento: 30 s.",
            "Invoca un helicoptero policial pilotable: W/S avanzar, A/D desplazar, ESPACIO subir, CTRL bajar. Tecla de linterna: luz que sigue tu mirada. Dura 2 min y aterriza solo. Enfriamiento: 60 s.",
            "Mejora del Tanque: lo reemplaza. Conduce con WASD, 3 disparos con clic derecho; baja con Mayus y vuelve a subir con clic derecho. Enfriamiento: 30 s.",
            "Apunta a un objetivo (hasta 12 m) y lo esposas 15 s: no puede atacar ni interactuar y tu puedes arrastrarlo. Enfriamiento: 30 s.",
            "Invoca un perro policia que te protege, ataca amenazas y marca con brillo a los enemigos cercanos. Dura 45 s. Enfriamiento: 60 s.",
            "Despliega una patrulla con sirena: los mobs hostiles cercanos huyen de miedo y la torreta dispara a los que esten a tiro. Dura 20 s. Enfriamiento: 40 s.",
            "Un dron vigila sobre ti 45 s y marca con brillo, incluso tras las paredes, a las amenazas en 32 m. Enfriamiento: 60 s."};
    /** Posicion de cada habilidad en la cuadricula (columna, fila). */
    static final int[] CX = {174, 72, 174, 276, 174, 38, 106, 242, 310};
    static final int[] ROW = {0, 1, 1, 1, 2, 2, 2, 2, 2};
    int px, py;
    float zoom = 1.0f;

    public PoliciaTreeScreen() {
        super(Component.literal("Rama de habilidades"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(px + PW - 66, py + PH + 4, 60, 20).build());
    }

    static ItemStack icon(int i) {
        if (i == 0) return new ItemStack(PoliciaShield.SHIELD.get());
        if (i == 2) return new ItemStack(Items.ELYTRA);
        return new ItemStack(Items.TNT_MINECART);
    }

    static boolean has(PoliciaMod.Sync s, int i) {
        return i == 0 || (s != null && ((s.un >> i) & 1) == 1);
    }

    int nodeX(int i) { return px + LEFT_W + 8 + CX[i] - 12; }
    int nodeY(int i) { return py + 40 + ROW[i] * 70; }

    static String cost(int i) {
        return PoliciaMod.LEVEL_COST[i] > 0 ? PoliciaMod.LEVEL_COST[i] + " niveles de experiencia" : PoliciaMod.XP_PER_NODE + " XP";
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF555C66);
        g.fill(x0, y0, x1, y1, 0xFF0A0C12);
    }

    void tag(GuiGraphics g, String text, int x, int y) {
        int w = font.width(text) + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + 13, 0xFF555C66);
        g.fill(x, y, x + w, y + 12, 0xFF8A8F99);
        g.drawString(font, text, x + 4, y + 2, 0xFFFFFFFF, true);
    }

    /** Icono con forma de tanque (16x16). */
    static void tankIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 10, x + 15, y + 14, 0xFF26292B);   // orugas
        g.fill(x + 2, y + 11, x + 14, y + 13, 0xFF4A4F52);
        for (int k = 0; k < 4; k++) g.fill(x + 3 + k * 3, y + 11, x + 4 + k * 3, y + 13, 0xFF8A9096); // ruedas
        g.fill(x + 2, y + 7, x + 14, y + 10, 0xFF5E7040);    // casco
        g.fill(x + 2, y + 7, x + 14, y + 8, 0xFF7C9156);
        g.fill(x + 5, y + 4, x + 11, y + 7, 0xFF6B7F48);     // torreta
        g.fill(x + 5, y + 4, x + 11, y + 5, 0xFF8AA062);
        g.fill(x + 7, y + 2, x + 9, y + 4, 0xFF3D4A28);      // escotilla
        g.fill(x + 11, y + 5, x + 16, y + 6, 0xFF2E3820);    // canon
        g.fill(x + 15, y + 4, x + 16, y + 7, 0xFF1F2616);    // boca del canon
    }

    /** Icono del tanque pilotable: tanque con flechas de movimiento. */
    static void mobileIcon(GuiGraphics g, int x, int y) {
        tankIcon(g, x, y);
        g.fill(x, y + 15, x + 16, y + 16, 0xFF60E0FF);       // flecha de avance
        g.fill(x + 13, y + 14, x + 14, y + 17, 0xFF60E0FF);
        g.fill(x + 14, y + 15, x + 15, y + 16, 0xFF60E0FF);
    }

    /** Icono de REFUERZO: dos policias. */
    static void reinforcementIcon(GuiGraphics g, int x, int y) {
        for (int k = 0; k < 2; k++) {
            int ox = x + k * 9;
            g.fill(ox + 1, y + 1, ox + 6, y + 3, 0xFF1C2A5A);    // gorra
            g.fill(ox, y + 3, ox + 7, y + 4, 0xFF10183A);        // visera
            g.fill(ox + 1, y + 4, ox + 6, y + 7, 0xFFE8B890);    // cara
            g.fill(ox + 2, y + 5, ox + 3, y + 6, 0xFF202020);    // ojos
            g.fill(ox + 4, y + 5, ox + 5, y + 6, 0xFF202020);
            g.fill(ox, y + 7, ox + 7, y + 14, 0xFF2C4A9A);       // uniforme
            g.fill(ox + 4, y + 8, ox + 6, y + 10, 0xFFE8C040);   // placa
            g.fill(ox, y + 11, ox + 7, y + 12, 0xFF202020);      // cinturon
            g.fill(ox + 1, y + 14, ox + 3, y + 16, 0xFF1C2238);  // piernas
            g.fill(ox + 4, y + 14, ox + 6, y + 16, 0xFF1C2238);
        }
    }

    /** Icono con forma de helicoptero (16x16). */
    static void heliIcon(GuiGraphics g, int x, int y) {
        g.fill(x, y + 1, x + 16, y + 2, 0xFFB0B4B8);             // helice
        g.fill(x + 7, y + 2, x + 9, y + 4, 0xFF50555A);          // eje
        g.fill(x + 3, y + 4, x + 12, y + 10, 0xFF5E7040);        // cabina
        g.fill(x + 3, y + 4, x + 12, y + 5, 0xFF7C9156);
        g.fill(x + 3, y + 5, x + 6, y + 8, 0xFF7FD0F0);          // cristal
        g.fill(x + 12, y + 5, x + 16, y + 7, 0xFF4E5E34);        // cola
        g.fill(x + 14, y + 3, x + 16, y + 5, 0xFF3D4A28);        // aleta
        g.fill(x + 15, y + 7, x + 16, y + 10, 0xFFB0B4B8);       // rotor de cola
        g.fill(x + 5, y + 10, x + 6, y + 11, 0xFF26292B);        // soportes
        g.fill(x + 9, y + 10, x + 10, y + 11, 0xFF26292B);
        g.fill(x + 2, y + 11, x + 13, y + 12, 0xFF26292B);       // patines
    }

    /** Icono de esposas (16x16). */
    static void cuffIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 3, x + 6, y + 6, 0xFF9AA0A8);       // bisagra izquierda
        g.fill(x, y + 5, x + 7, y + 13, 0xFFD0D4DA);          // aro izquierdo
        g.fill(x + 2, y + 7, x + 5, y + 11, 0xFFC8921C);      // hueco
        g.fill(x + 10, y + 3, x + 15, y + 6, 0xFF9AA0A8);     // bisagra derecha
        g.fill(x + 9, y + 5, x + 16, y + 13, 0xFFD0D4DA);     // aro derecho
        g.fill(x + 11, y + 7, x + 14, y + 11, 0xFFC8921C);
        g.fill(x + 7, y + 8, x + 9, y + 10, 0xFF404448);      // cadena
        g.fill(x + 6, y + 9, x + 10, y + 10, 0xFF606468);
        g.fill(x + 1, y + 12, x + 6, y + 13, 0xFF808890);
        g.fill(x + 10, y + 12, x + 15, y + 13, 0xFF808890);
    }

    /** Icono de perro K9 (16x16). */
    static void dogIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y + 1, x + 5, y + 6, 0xFF3A2A1A);       // oreja
        g.fill(x + 4, y + 3, x + 12, y + 11, 0xFFB07A3A);     // cabeza
        g.fill(x + 11, y + 6, x + 16, y + 11, 0xFFD8A860);    // hocico
        g.fill(x + 15, y + 6, x + 16, y + 8, 0xFF101010);     // nariz
        g.fill(x + 8, y + 5, x + 10, y + 7, 0xFF101010);      // ojo
        g.fill(x + 4, y + 11, x + 13, y + 13, 0xFF2C4A9A);    // collar
        g.fill(x + 7, y + 13, x + 9, y + 15, 0xFFE8C040);     // chapa
        g.fill(x + 2, y + 11, x + 4, y + 16, 0xFF3A2A1A);     // pecho
    }

    /** Icono de patrulla con sirena (16x16). */
    static void sirenIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 3, y + 4, x + 8, y + 9, 0xFFE03030);       // luz roja
        g.fill(x + 8, y + 4, x + 13, y + 9, 0xFF3060F0);      // luz azul
        g.fill(x + 3, y + 4, x + 13, y + 5, 0xFFFFFFFF);      // brillo
        g.fill(x + 2, y + 9, x + 14, y + 12, 0xFF8A9096);     // base
        g.fill(x + 1, y + 12, x + 15, y + 14, 0xFF26292B);
        g.fill(x, y + 3, x + 1, y + 4, 0xFFFFE070);           // destellos
        g.fill(x + 15, y + 3, x + 16, y + 4, 0xFFFFE070);
        g.fill(x + 7, y + 1, x + 9, y + 3, 0xFFFFE070);
    }

    /** Icono de dron (16x16). */
    static void droneIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 4, x + 6, y + 5, 0xFFD0D6DE);       // helices
        g.fill(x + 10, y + 4, x + 15, y + 5, 0xFFD0D6DE);
        g.fill(x + 3, y + 5, x + 4, y + 8, 0xFF505660);       // soportes
        g.fill(x + 12, y + 5, x + 13, y + 8, 0xFF505660);
        g.fill(x + 2, y + 8, x + 14, y + 9, 0xFF505660);      // brazos
        g.fill(x + 5, y + 7, x + 11, y + 11, 0xFF2C3038);     // cuerpo
        g.fill(x + 5, y + 7, x + 11, y + 8, 0xFF50565E);
        g.fill(x + 7, y + 11, x + 9, y + 13, 0xFFE03030);     // camara
        g.fill(x + 6, y + 13, x + 10, y + 14, 0xFF50565E);
    }

    void padlock(GuiGraphics g, int x, int y) {
        g.fill(x + 8, y + 3, x + 10, y + 11, 0xFFD8D8D8);
        g.fill(x + 14, y + 3, x + 16, y + 11, 0xFFD8D8D8);
        g.fill(x + 8, y + 3, x + 16, y + 5, 0xFFD8D8D8);
        g.fill(x + 6, y + 10, x + 18, y + 20, 0xFFE8B830);
        g.fill(x + 11, y + 13, x + 13, y + 17, 0xFF2A2A2A);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int rawMx = mx, rawMy = my;
        mx = (int) ((mx - width / 2.0f) / zoom + width / 2.0f);
        my = (int) ((my - height / 2.0f) / zoom + height / 2.0f);
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, height / 2.0f, 0.0f);
        g.pose().scale(zoom, zoom, 1.0f);
        g.pose().translate(-width / 2.0f, -height / 2.0f, 0.0f);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int xp = s == null ? 0 : s.xp;
        int sel = s == null ? 0 : s.sel;
        int lvl = minecraft != null && minecraft.player != null ? minecraft.player.experienceLevel : 0;
        // panel izquierdo: personaje
        panel(g, px, py, px + LEFT_W, py + PH);
        tag(g, "Policia", px + 6, py - 6);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + LEFT_W / 2, py + PH - 30, 55,
                    (px + LEFT_W / 2) - mx, (py + 60) - my, minecraft.player);
        }
        // panel derecho: habilidades
        int rx = px + LEFT_W + 8;
        panel(g, rx, py, px + PW, py + PH);
        tag(g, "Habilidades", rx + 6, py - 6);
        g.drawString(font, "XP: " + xp + "   Niveles: " + lvl, rx + 8, py + 10, 0xFFE8C040, true);
        g.drawString(font, "Desbloquear: clic izquierdo en el icono", rx + 8, py + 20, 0xFF9AA4B0, false);
        int n = PoliciaMod.SKILLS.length;
        // conexiones
        for (int i = 1; i < n; i++) {
            int pre = PoliciaMod.PRE[i];
            int col = has(s, i) ? 0xFFE070D8 : 0xFF6A3A66;
            int x0 = nodeX(pre) + 11, x1 = nodeX(i) + 11;
            int y0 = nodeY(pre) + 44, y1 = nodeY(i);
            int ym = y1 - 10;
            g.fill(x0, y0, x0 + 2, ym + 2, col);
            g.fill(Math.min(x0, x1), ym, Math.max(x0, x1) + 2, ym + 2, col);
            g.fill(x1, ym, x1 + 2, y1, col);
        }
        int hov = -1;
        for (int i = 0; i < n; i++) {
            int nx = nodeX(i), y = nodeY(i);
            boolean open = has(s, i);
            boolean hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) hov = i;
            int border = (open && i == sel) ? 0xFFFFFFFF : (hover ? 0xFFFFE9A0 : 0xFF6B4A0C);
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? 0xFFC8921C : 0xFF8A6612);
            if (i == 1) reinforcementIcon(g, nx + 4, y + 4);
            else if (i == 2) tankIcon(g, nx + 4, y + 4);
            else if (i == 3) heliIcon(g, nx + 4, y + 4);
            else if (i == 4) mobileIcon(g, nx + 4, y + 4);
            else if (i == 5) cuffIcon(g, nx + 4, y + 4);
            else if (i == 6) dogIcon(g, nx + 4, y + 4);
            else if (i == 7) sirenIcon(g, nx + 4, y + 4);
            else if (i == 8) droneIcon(g, nx + 4, y + 4);
            else g.renderItem(icon(i), nx + 4, y + 4);
            if (!open) {
                g.fill(nx, y, nx + 24, y + 24, 0x99000000);
                padlock(g, nx, y);
            }
            List<FormattedCharSequence> nl = font.split(FormattedText.of(NAMES[i]), 64);
            for (int k = 0; k < nl.size() && k < 2; k++) {
                g.drawCenteredString(font, nl.get(k), nx + 12, y + 27 + k * 9, open ? 0xFFFFFFFF : 0xFFA0A0A0);
            }
            if (i == 1 && open) g.drawCenteredString(font, "Nv " + Math.max(1, s == null ? 1 : s.rl) + "/4", nx + 12, y - 10, 0xFFE8C040);
        }
        // informacion de la habilidad senalada (o la elegida)
        int show = hov >= 0 ? hov : (has(s, sel) ? sel : 0);
        int bx0 = rx + 6, by0 = py + 258, bx1 = px + PW - 6, by1 = py + PH - 6;
        panel(g, bx0, by0, bx1, by1);
        boolean open = has(s, show);
        g.drawString(font, NAMES[show], bx0 + 5, by0 + 4, 0xFFFFFFFF, true);
        List<FormattedCharSequence> lines = font.split(FormattedText.of(DESC[show]), bx1 - bx0 - 10);
        for (int k = 0; k < lines.size() && k < (show == 1 && open ? 2 : 5); k++) {
            g.drawString(font, lines.get(k), bx0 + 5, by0 + 15 + k * 9, open ? 0xFFC8D0DC : 0xFF808890, false);
        }
        if (show == 1 && open) {
            int lv = Math.max(1, s == null ? 1 : s.rl);
            g.drawString(font, "Nv " + lv + "/4 - " + PoliciaRefuerzo.COUNT[lv] + " NPC - espada " + PoliciaRefuerzo.SWORD[lv], bx0 + 5, by0 + 36, 0xFFE8C040, false);
            String upg = lv >= 4 ? "Nivel maximo" : "Clic derecho: mejorar (" + PoliciaMod.XP_PER_NODE * lv + " XP)";
            g.drawString(font, upg, bx0 + 5, by0 + 46, 0xFF9AA4B0, false);
        }
        if (!open) {
            g.drawString(font, "Bloqueada - cuesta " + cost(show), bx0 + 5, by1 - 11, 0xFFE8B830, false);
        } else if (show == sel) {
            g.drawString(font, "Elegida (tecla J)", bx0 + 5, by1 - 11, 0xFF60E060, false);
        } else {
            g.drawString(font, "Clic para elegir", bx0 + 5, by1 - 11, 0xFF9AA4B0, false);
        }
        g.pose().popPose();
        super.render(g, rawMx, rawMy, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        double rmx = mx, rmy = my;
        mx = (mx - width / 2.0) / zoom + width / 2.0;
        my = (my - height / 2.0) / zoom + height / 2.0;
        if (btn == 1) { // clic derecho sobre REFUERZO: mejorar de nivel
            PoliciaMod.Sync s1 = PoliciaClient.mine();
            int nx1 = nodeX(1), y1 = nodeY(1);
            if (has(s1, 1) && mx >= nx1 && mx <= nx1 + 24 && my >= y1 && my <= y1 + 24) {
                PoliciaClient.send(30);
                return true;
            }
        }
        if (btn == 0) {
            PoliciaMod.Sync s = PoliciaClient.mine();
            for (int i = 0; i < PoliciaMod.SKILLS.length; i++) {
                int nx = nodeX(i), y = nodeY(i);
                if (mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24) {
                    PoliciaClient.send(has(s, i) ? 10 + i : 20 + i);
                    return true;
                }
            }
        }
        return super.mouseClicked(rmx, rmy, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        zoom = Math.max(0.5f, Math.min(2.0f, zoom + (float) delta * 0.1f));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
