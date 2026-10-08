package com.example.policia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Libro con las habilidades y teclas del oficio, que se entrega al elegir el oficio. */
public class PoliciaBook {
    static final String[] ALBANIL = {
            "OFICIO ALBANIL\n\nB: activar o desactivar el modo albanil.\nJ: usar la habilidad elegida.\nK: cambiar de habilidad.\n\nLas habilidades se desbloquean en el arbol de habilidades (clic en el icono) y cuestan XP.",
            "1. PICO (J)\n\nTe da un pico de diamante durante 7 s con Fuerza I, Prisa minera I y Fortuna II.\nNo se puede soltar ni guardar en cofres; se puede encantar y conserva los encantamientos.\nEnfriamiento: 8 s.",
            "2. MURO (J)\n\nPared de 3 x 3 frente a ti. Hasta 3 muros seguidos (J de nuevo). Duran 15 s. Enfriamiento: 5 s.\n\nMejora a REFUGIO: casa con puerta, cama y cofre durante 35 s. Enfriamiento: 40 s.",
            "3. AYUDANTE (J)\n\nNivel I: 1 ayudante con pico de piedra. Nivel II: 3 con hierro. Nivel III: 4 con diamante.\nDuran 60 s. Enfriamiento: 30 s.\nMientras estan activos ves menas cercanas (radio 4).",
            "Teclas de los ayudantes:\n\nH: cambiar la orden (seguir, picar delante de mi, buscar minerales).\nX (mantener): elegir el area; rueda = largo, Mayus + rueda = ancho.\nY / N: aceptar o rechazar su aviso de mineral.\nU: ver lo que llevan.",
            "4. LINTERNA (J o G)\n\nUna luz que sigue tu mirada hasta 48 bloques durante 60 s.\nEnfriamiento: 20 s.\n\nCosto de desbloqueo: 10 XP (AYUDANTE II 15 XP, AYUDANTE III 20 XP).",
            "5. TRACTOR (J)\n\nPala cargadora amarilla con cabina. Dura 45 s. Enfriamiento: 35 s. Desbloqueo: 15 XP.\nJ: invocar / bajar / volver a subir.\nMayus + J: retirarlo.\nClic derecho: montar.",
            "Conduciendo el tractor:\n\nW / S: avanzar y retroceder.\nA / D: girar.\nESPACIO: sube la pala. CTRL: la baja.\nG: luces.\nU: su inventario.\nMayus: bajarte.\n\nRompe lo natural que tiene delante, pero nunca construcciones de jugadores."};

    static final String[] POLICIA = {
            "OFICIO POLICIA\n\nB: activar o desactivar el modo policia.\nJ: usar la habilidad elegida.\nK: cambiar de habilidad.\n\nLas habilidades se desbloquean en el arbol de habilidades (clic en el icono) y cuestan XP.",
            "1. ESCUDO BALISTICO (J)\n\nTe protege durante unos segundos y luego entra en enfriamiento.\n\n2. REFUERZO (J)\n\nLlama a policias que luchan a tu lado. Se mejora hasta el nivel 4 con XP.",
            "3. TANQUE (J)\n\nApunta y haz clic derecho para disparar un proyectil explosivo.\n\n4. TANQUE MOVIL\n\nPilotable: W/A/S/D para moverte, clic derecho para disparar (3 disparos). Mayus: bajarte. J: volver a subir.",
            "5. HELICOPTERO (J)\n\nW / S: avanzar. A / D: desplazarte.\nESPACIO: subir. CTRL: bajar.\nG: modo linterna.\nMayus: salir.",
            "6. ESPOSAS\n7. PERRO K9\n8. SIRENA Y TORRETA\n9. DRON DE VIGILANCIA\n\nElige la habilidad con K y usala con J. Cada una se desbloquea en el arbol de habilidades.",
            "COMISARIO Y MISIONES\n\nEn cada aldea hay un comisario junto a la campana. Clic derecho: tablero con 3 misiones (se renuevan cada dia). Solo atiende a policias.\n\nMisiones: atrapar fugitivo, patrulla de 3 puntos, rescate, limpiar campamento, evidencia, escolta y bombas. Cada 3 cumplidas se desbloquea cazar al jefe de banda.",
            "ENEMIGOS\n\nLadron, evadido y contrabandista huyen: esposalos. Pandilleros con garrote y arco, lideres, jefe de banda con barra de vida y francotiradores de torre (usa el escudo balistico). Saboteadores dejan bombas: rompe el bloque de TNT para desactivarlo. Recompensas: XP, esmeraldas y objetos especiales."};

    static void give(ServerPlayer p, String job) {
        String[] pg = "albanil".equals(job) ? ALBANIL : ("policia".equals(job) ? POLICIA : null);
        if (pg == null) return;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.hasTag() && job.equals(s.getTag().getString("pol_book"))) return;
        }
        ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag t = b.getOrCreateTag();
        t.putString("title", "albanil".equals(job) ? "Guia del Albanil" : "Guia del Policia");
        t.putString("author", "Oficios");
        t.putString("pol_book", job);
        ListTag pages = new ListTag();
        for (String s : pg) pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(s))));
        t.put("pages", pages);
        if (!p.getInventory().add(b)) p.drop(b, false);
        PoliciaMod.msg(p, "Recibiste un libro con las habilidades y teclas de tu oficio");
    }
}
