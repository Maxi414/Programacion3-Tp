package uade.prog3.tpo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uade.prog3.tpo.algorithm.*;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Un test por cada algoritmo implementado (requisito del hito 9).
 *
 * NO tocan Neo4j: son unitarios sobre el grafo en memoria y corren en
 * milisegundos. Los valores esperados son los que la guia de la materia da
 * calculados a mano sobre el grafo de ejemplo.
 */
class AlgoritmosTest {

    /** El mismo grafo de 8 vertices y 12 aristas que carga CargaInicial. */
    private Grafo semilla(boolean dirigido) {
        Grafo g = new Grafo(dirigido);
        for (String id : List.of("A", "B", "C", "D", "E", "F", "G", "H")) {
            g.agregarVertice(id);
        }
        g.agregarArista("A", "B", 4);   g.agregarArista("A", "C", 9);
        g.agregarArista("B", "C", 3);   g.agregarArista("B", "D", 7);
        g.agregarArista("C", "D", 2);   g.agregarArista("C", "E", 11);
        g.agregarArista("D", "E", 5);   g.agregarArista("D", "F", 8);
        g.agregarArista("E", "F", 6);   g.agregarArista("E", "G", 10);
        g.agregarArista("F", "H", 3);   g.agregarArista("G", "H", 4);
        return g;
    }

    /** Los mismos items que carga CargaInicial. */
    private List<Item> items() {
        return List.of(
                new Item("I1", "Item 1", 5, 10, "A"),
                new Item("I2", "Item 2", 4, 40, "B"),
                new Item("I3", "Item 3", 6, 30, "C"),
                new Item("I4", "Item 4", 3, 50, "D"),
                new Item("I5", "Item 5", 7, 55, "E"));
    }

    private List<String> ids(List<Item> lista) {
        List<String> r = new ArrayList<>();
        for (Item i : lista) {
            r.add(i.getId());
        }
        return r;
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Recorridos - 2 puntos")
    class RecorridosTest {

        private final Recorridos recorridos = new Recorridos();

        @Test
        void dfsVisitaTodosLosAlcanzablesEnProfundidad() {
            assertEquals(List.of("A", "B", "C", "D", "E", "F", "H", "G"),
                    recorridos.dfs(semilla(true), "A"));
        }

        @Test
        void dfsIterativoDaElMismoOrdenQueElRecursivo() {
            Grafo g = semilla(true);
            assertEquals(recorridos.dfs(g, "A"), recorridos.dfsIterativo(g, "A"));
        }

        @Test
        void bfsVisitaPorNiveles() {
            assertEquals(List.of("A", "B", "C", "D", "E", "F", "G", "H"),
                    recorridos.bfs(semilla(true), "A"));
        }

        @Test
        void bfsNoRepiteVerticesAunqueHayaCiclos() {
            List<String> orden = recorridos.bfs(semilla(false), "A");
            assertEquals(new HashSet<>(orden).size(), orden.size(),
                    "marcar al encolar evita que un vertice entre dos veces");
        }

        @Test
        void origenInexistenteFallaConMensajeClaro() {
            assertThrows(IllegalArgumentException.class,
                    () -> recorridos.bfs(semilla(true), "Z"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Caminos minimos - Dijkstra y Floyd")
    class CaminosMinimosTest {

        private final CaminosMinimos caminos = new CaminosMinimos();

        @Test
        void dijkstraEligeElCaminoBaratoYNoElDirecto() {
            var c = caminos.dijkstra(semilla(true), "A", "C");
            assertEquals(7.0, c.costoTotal(), 1e-9, "A->B->C cuesta 7; el directo A->C cuesta 9");
            assertEquals(List.of("A", "B", "C"), c.vertices());
        }

        @Test
        void dijkstraReconstruyeElCaminoCompleto() {
            var c = caminos.dijkstra(semilla(true), "A", "H");
            assertEquals(20.0, c.costoTotal(), 1e-9);
            assertEquals(List.of("A", "B", "C", "D", "F", "H"), c.vertices());
        }

        @Test
        void dijkstraRechazaPesosNegativos() {
            Grafo g = new Grafo(true);
            g.agregarArista("X", "Y", -5);
            assertThrows(IllegalArgumentException.class, () -> caminos.dijkstra(g, "X", "Y"));
        }

        @Test
        void dijkstraAvisaCuandoNoHayCamino() {
            assertThrows(IllegalStateException.class,
                    () -> caminos.dijkstra(semilla(true), "H", "A"));
        }

        @Test
        void floydCoincideConDijkstraEnTodosLosPares() {
            Grafo g = semilla(true);
            Map<String, Double> todos = caminos.floydWarshall(g);
            for (String origen : g.ids()) {
                for (String destino : g.ids()) {
                    Double porFloyd = todos.get(origen + "->" + destino);
                    if (porFloyd == null || origen.equals(destino)) {
                        continue;
                    }
                    assertEquals(caminos.dijkstra(g, origen, destino).costoTotal(), porFloyd, 1e-9,
                            "discrepancia en " + origen + "->" + destino);
                }
            }
        }

        @Test
        void floydDetectaCicloNegativo() {
            Grafo g = new Grafo(true);
            g.agregarArista("X", "Y", 1);
            g.agregarArista("Y", "X", -5);
            assertThrows(IllegalStateException.class, () -> caminos.floydWarshall(g));
        }

        @Test
        void ucsExpandeMenosVerticesQueDijkstraCompleto() {
            Grafo g = semilla(true);
            assertTrue(caminos.expandidosUcs(g, "A", "C") < caminos.expandidosDijkstraCompleto(g, "A"),
                    "la parada temprana tiene que ahorrar expansiones");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Arbol de recubrimiento minimo - Prim y Kruskal")
    class ArbolRecubrimientoTest {

        private final ArbolRecubrimiento arbol = new ArbolRecubrimiento();

        @Test
        void primDaCostoTotal27ConSieteAristas() {
            var mst = arbol.prim(semilla(false), "A");
            assertEquals(27.0, mst.costoTotal(), 1e-9);
            assertEquals(7, mst.aristas().size(), "V-1 aristas con V=8");
        }

        @Test
        void kruskalDaElMismoCostoQuePrim() {
            Grafo g = semilla(false);
            assertEquals(arbol.prim(g, "A").costoTotal(), arbol.kruskal(g).costoTotal(), 1e-9);
        }

        @Test
        void kruskalNoGeneraCiclos() {
            var mst = arbol.kruskal(semilla(false));
            assertEquals(7, mst.aristas().size());
            HashSet<String> vistos = new HashSet<>();
            for (var a : mst.aristas()) {
                vistos.add(a.origen());
                vistos.add(a.destino());
            }
            assertEquals(8, vistos.size(), "el MST tiene que tocar los 8 vertices");
        }

        @Test
        void elMstExigeGrafoNoDirigido() {
            assertThrows(IllegalArgumentException.class, () -> arbol.prim(semilla(true), "A"));
            assertThrows(IllegalArgumentException.class, () -> arbol.kruskal(semilla(true)));
        }

        @Test
        void grafoNoConexoFallaConMensajeClaro() {
            Grafo g = new Grafo(false);
            g.agregarArista("P", "Q", 1);
            g.agregarVertice("Z"); // aislado
            assertThrows(IllegalStateException.class, () -> arbol.kruskal(g));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Ordenamiento - 1 punto")
    class OrdenamientoTest {

        private final Ordenamiento ordenamiento = new Ordenamiento();
        private final Comparator<Item> porPeso = Comparator.comparingDouble(Item::getPeso);

        @Test
        void quickSortOrdenaPorPeso() {
            assertEquals(List.of("I4", "I2", "I1", "I3", "I5"),
                    ids(ordenamiento.quickSort(items(), porPeso)));
        }

        @Test
        void mergeSortOrdenaIgualQueQuickSort() {
            assertEquals(ids(ordenamiento.quickSort(items(), porPeso)),
                    ids(ordenamiento.mergeSort(items(), porPeso)));
        }

        @Test
        void ningunoModificaLaListaDeEntrada() {
            List<Item> original = items();
            ordenamiento.quickSort(original, porPeso);
            ordenamiento.mergeSort(original, porPeso);
            assertEquals(List.of("I1", "I2", "I3", "I4", "I5"), ids(original));
        }

        @Test
        void mergeSortEsEstable() {
            // tres items con el mismo peso: tienen que salir en el orden de entrada
            List<Item> empatados = List.of(
                    new Item("X1", "x1", 5, 1, null),
                    new Item("X2", "x2", 5, 2, null),
                    new Item("X3", "x3", 5, 3, null));
            assertEquals(List.of("X1", "X2", "X3"), ids(ordenamiento.mergeSort(empatados, porPeso)));
        }

        @Test
        void listasVaciasYDeUnElementoNoRompen() {
            assertTrue(ordenamiento.quickSort(List.of(), porPeso).isEmpty());
            assertEquals(1, ordenamiento.mergeSort(items().subList(0, 1), porPeso).size());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Seleccion - greedy contra programacion dinamica")
    class SeleccionTest {

        private final Seleccion seleccion = new Seleccion(new Ordenamiento());

        @Test
        void greedyTomaPorRatioYRespetaLaCapacidad() {
            var r = seleccion.greedy(items(), 10);
            assertEquals(List.of("I4", "I2"), ids(r.elegidos()));
            assertEquals(90.0, r.valorTotal(), 1e-9);
            assertTrue(r.pesoTotal() <= 10);
        }

        @Test
        void programacionDinamicaEncuentraElOptimo() {
            var r = seleccion.programacionDinamica(items(), 10);
            assertEquals(105.0, r.valorTotal(), 1e-9);
            assertEquals(List.of("I4", "I5"), ids(r.elegidos()));
            assertEquals(10.0, r.pesoTotal(), 1e-9);
        }

        @Test
        void greedyNoAlcanzaElOptimoEnMochila01() {
            // ESTE es el contraejemplo que pide el hito 6
            double porGreedy = seleccion.greedy(items(), 10).valorTotal();
            double porDp = seleccion.programacionDinamica(items(), 10).valorTotal();
            assertTrue(porGreedy < porDp,
                    "greedy da " + porGreedy + " y el optimo es " + porDp);
            assertEquals(15.0, porDp - porGreedy, 1e-9);
        }

        @Test
        void capacidadCeroNoEligeNada() {
            assertTrue(seleccion.programacionDinamica(items(), 0).elegidos().isEmpty());
            assertTrue(seleccion.greedy(items(), 0).elegidos().isEmpty());
        }

        @Test
        void pesosNoEnterosAvisanEnLugarDeRedondearEnSilencio() {
            List<Item> fraccionario = List.of(new Item("F1", "f1", 2.5, 10, null));
            assertThrows(IllegalArgumentException.class,
                    () -> seleccion.programacionDinamica(fraccionario, 10));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Backtracking - 1 punto")
    class BacktrackingTest {

        private final Backtracking backtracking = new Backtracking();

        @Test
        void encuentraLasTreceRutasSimplesDeAaH() {
            assertEquals(13, backtracking.rutasSimples(semilla(true), "A", "H", 1000, 10).size());
        }

        @Test
        void todasLasRutasSonSimplesYVanDeOrigenADestino() {
            for (var r : backtracking.rutasSimples(semilla(true), "A", "H", 1000, 10)) {
                assertEquals(new HashSet<>(r.vertices()).size(), r.vertices().size(),
                        "no puede repetir vertices: " + r.vertices());
                assertEquals("A", r.vertices().get(0));
                assertEquals("H", r.vertices().get(r.vertices().size() - 1));
            }
        }

        @Test
        void laRestriccionDeCostoDescartaRutas() {
            var todas = backtracking.rutasSimples(semilla(true), "A", "H", 1000, 10);
            var baratas = backtracking.rutasSimples(semilla(true), "A", "H", 25, 10);
            assertTrue(baratas.size() < todas.size());
            for (var r : baratas) {
                assertTrue(r.costoTotal() <= 25);
            }
        }

        @Test
        void laRestriccionDeSaltosDescartaRutas() {
            var cortas = backtracking.rutasSimples(semilla(true), "A", "H", 1000, 3);
            for (var r : cortas) {
                assertTrue(r.vertices().size() - 1 <= 3);
            }
        }

        @Test
        void laPodaExploraMenosNodosSinPerderSoluciones() {
            var c = backtracking.compararPoda(semilla(true), "A", "H", 25, 10);
            assertTrue(c.exploradosConPoda() < c.exploradosSinPoda(),
                    "con poda " + c.exploradosConPoda() + ", sin poda " + c.exploradosSinPoda());
            assertEquals(backtracking.rutasSimples(semilla(true), "A", "H", 25, 10).size(),
                    c.rutasEncontradas(), "la poda no puede perder rutas validas");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Branch and Bound - 1 punto")
    class RamificacionYPodaTest {

        private final RamificacionYPoda byp = new RamificacionYPoda(new Ordenamiento());

        @Test
        void repartirEnTresContenedoresDaMakespan9() {
            // pesos 5,4,6,3,7 suman 25. Con 3 contenedores el minimo posible es 9:
            // con max 8 la capacidad total seria 24 < 25.
            assertEquals(9.0, byp.repartir(items(), 3).cargaMaxima(), 1e-9);
        }

        @Test
        void noSePierdeNiSeDuplicaNingunItem() {
            var a = byp.repartir(items(), 3);
            int total = a.porContenedor().values().stream().mapToInt(List::size).sum();
            assertEquals(5, total);
        }

        @Test
        void laPodaActuaYSeReporta() {
            var a = byp.repartir(items(), 3);
            assertTrue(a.nodosPodados() > 0, "si no poda nada, la cota no sirve");
            assertTrue(a.cotaInicial() >= a.cargaMaxima(), "LPT es una cota superior");
        }

        @Test
        void unSoloContenedorRecibeTodo() {
            assertEquals(25.0, byp.repartir(items(), 1).cargaMaxima(), 1e-9);
        }

        @Test
        void cantidadDeContenedoresInvalidaFalla() {
            assertThrows(IllegalArgumentException.class, () -> byp.repartir(items(), 0));
        }

        @Test
        void elMakespanNuncaBajaDeLaMedia() {
            var a = byp.repartir(items(), 3);
            double suma = items().stream().mapToDouble(Item::getPeso).sum();
            assertTrue(a.cargaMaxima() >= suma / 3, "seria una solucion imposible");
        }
    }
}
