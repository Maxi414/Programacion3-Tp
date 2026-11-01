package uade.prog3.tpo.seed;

import uade.prog3.tpo.juego.Mapa;
import uade.prog3.tpo.juego.Terreno;
import uade.prog3.tpo.model.Item;

import java.util.List;

/**
 * Los datos del dominio: el mapa del juego y el catalogo de unidades.
 *
 * Es la UNICA fuente de estos datos. CargaInicial los guarda en Neo4j y los
 * tests arman el grafo en memoria con los mismos valores, asi lo que se
 * verifica con asserts es exactamente lo que hay en la base.
 *
 * El mapa es simetrico respecto del centro (cada territorio del norte tiene
 * un gemelo en el sur), salvo el centro: un paso de montana (CON) y unas
 * ruinas en el bosque (EBA). Detalle y diagrama en el README.
 */
public final class MapaInicial {

    private MapaInicial() {
    }

    /**
     * 12 territorios, en orden ALFABETICO de id (el mismo orden canonico con
     * el que GrafoService los lee de Neo4j). produccion = oro por turno.
     * x, y = posicion en el dibujo.
     */
    public static final List<Mapa.Territorio> TERRITORIOS = List.of(
            new Mapa.Territorio("AZU", "Aguazul",         Terreno.CIUDAD,  3, 110, 110),
            new Mapa.Territorio("BRA", "Brasaroja",       Terreno.CIUDAD,  3, 890, 590),
            new Mapa.Territorio("CON", "Paso del Condor", Terreno.MONTANA, 1, 420, 320),
            new Mapa.Territorio("COR", "Puerto Coral",    Terreno.CIUDAD,  3, 380, 600),
            new Mapa.Territorio("DOR", "Llano Dorado",    Terreno.LLANURA, 2, 810, 350),
            new Mapa.Territorio("EBA", "Ruinas de Ebano", Terreno.BOSQUE,  2, 590, 380),
            new Mapa.Territorio("HEL", "Picos Helados",   Terreno.MONTANA, 1, 880, 130),
            new Mapa.Territorio("IGN", "Cumbres Igneas",  Terreno.MONTANA, 1, 120, 570),
            new Mapa.Territorio("NIE", "Puerto Niebla",   Terreno.CIUDAD,  3, 620, 100),
            new Mapa.Territorio("PIN", "Pinar Hondo",     Terreno.BOSQUE,  2, 360,  90),
            new Mapa.Territorio("SEL", "Selva Ambar",     Terreno.BOSQUE,  2, 640, 610),
            new Mapa.Territorio("VAD", "Vado Real",       Terreno.LLANURA, 2, 190, 350));

    /**
     * 17 rutas no dirigidas. costo = dias de marcha. Cada una se escribe con
     * origen < destino y la lista va ordenada por (origen, destino): es el
     * orden en que GrafoService las lee de Neo4j, asi BFS y DFS recorren los
     * vecinos en el mismo orden en los tests y en la API.
     *
     * Elegidas a proposito para que el camino directo NO siempre sea el mas
     * barato: cruzar el paso de montana PIN-CON cuesta 10, pero bordear por el
     * valle PIN-AZU-VAD-CON cuesta 3+2+4 = 9 (verificacion de Dijkstra).
     */
    public static final List<Mapa.Ruta> RUTAS = List.of(
            new Mapa.Ruta("AZU", "PIN", 3),
            new Mapa.Ruta("AZU", "VAD", 2),
            new Mapa.Ruta("BRA", "DOR", 2),
            new Mapa.Ruta("BRA", "SEL", 3),
            new Mapa.Ruta("CON", "COR", 4),
            new Mapa.Ruta("CON", "EBA", 3),
            new Mapa.Ruta("CON", "PIN", 10),
            new Mapa.Ruta("CON", "VAD", 4),
            new Mapa.Ruta("COR", "IGN", 5),
            new Mapa.Ruta("COR", "SEL", 3),
            new Mapa.Ruta("DOR", "EBA", 4),
            new Mapa.Ruta("DOR", "HEL", 6),
            new Mapa.Ruta("EBA", "NIE", 4),
            new Mapa.Ruta("EBA", "SEL", 10),
            new Mapa.Ruta("HEL", "NIE", 5),
            new Mapa.Ruta("IGN", "VAD", 6),
            new Mapa.Ruta("NIE", "PIN", 3));

    /**
     * Catalogo del mercado: peso = costo en oro, valor = poder.
     *
     * Calibrado para que greedy por ratio poder/costo NO sea optimo en varios
     * presupuestos. Con 6 de oro (el primer turno del escenario clasico)
     * greedy compra Arqueros + Milicia = 6 de poder y la mochila compra
     * Milicia + Caballeria = 9. Con 10 de oro: greedy 14, mochila 15. Con 14:
     * 19 contra 22.
     */
    public static List<Item> catalogo() {
        return List.of(
                new Item("U1", "Milicia",    1,  1, null),
                new Item("U2", "Arqueros",   3,  5, null),
                new Item("U3", "Lanceros",   4,  6, null),
                new Item("U4", "Caballeria", 5,  8, null),
                new Item("U5", "Catapulta",  6,  9, null),
                new Item("U6", "Guardia",    8, 13, null));
    }
}
