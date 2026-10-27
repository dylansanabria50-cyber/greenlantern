package com.example.greenlantern.client;

import com.example.greenlantern.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Shift + H: elegir el traje, el emblema, el verde y el antifaz, con el jugador de vista previa. */
public class DesignScreen extends Screen {
    private int d;
    private Button bTraje, bEmblema, bVerde, bAntifaz;

    public DesignScreen(int design) {
        super(Component.literal("Diseño del traje"));
        this.d = design & 127;
    }

    @Override
    protected void init() {
        int x = this.width / 2 + 10, y = this.height / 2 - 56;
        bTraje = addRenderableWidget(Button.builder(Component.empty(), b -> cambiar(0)).bounds(x, y, 160, 20).build());
        bEmblema = addRenderableWidget(Button.builder(Component.empty(), b -> cambiar(1)).bounds(x, y + 24, 160, 20).build());
        bVerde = addRenderableWidget(Button.builder(Component.empty(), b -> cambiar(2)).bounds(x, y + 48, 160, 20).build());
        bAntifaz = addRenderableWidget(Button.builder(Component.empty(), b -> cambiar(3)).bounds(x, y + 72, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Listo"), b -> this.onClose()).bounds(x, y + 104, 160, 20).build());
        actualizar();
    }

    private void cambiar(int cual) {
        int t = SuitTexture.traje(d), e = SuitTexture.emblema(d), v = SuitTexture.verde(d);
        boolean a = SuitTexture.antifaz(d);
        if (cual == 0) t = (t + 1) % 3;
        if (cual == 1) e = (e + 1) % 3;
        if (cual == 2) v = (v + 1) % 3;
        if (cual == 3) a = !a;
        d = SuitTexture.pack(t, e, v, a);
        actualizar();
    }

    private void actualizar() {
        bTraje.setMessage(Component.literal("Traje: " + SuitTexture.TRAJES[SuitTexture.traje(d)]));
        bEmblema.setMessage(Component.literal("Emblema: " + SuitTexture.EMBLEMAS[SuitTexture.emblema(d)]));
        bVerde.setMessage(Component.literal("Verde: " + SuitTexture.VERDES[SuitTexture.verde(d)]));
        bAntifaz.setMessage(Component.literal(SuitTexture.antifaz(d) ? "Con antifaz" : "Sin antifaz"));
        ClientSuit.previewDesign = d;
    }

    @Override
    public void onClose() {
        ClientSuit.previewDesign = -1;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.DesignPacket(d));
        super.onClose();
    }

    @Override
    public void removed() {
        ClientSuit.previewDesign = -1;
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int px = this.width / 2 - 70, py = this.height / 2 + 60;
        if (this.minecraft != null && this.minecraft.player != null)
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, 55, (float) (px - mx), (float) (py - 90 - my), this.minecraft.player);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 84, 0x55FF77);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
