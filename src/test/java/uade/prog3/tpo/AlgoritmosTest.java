package uade.prog3.tpo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uade.prog3.tpo.algorithm.*;
import uade.prog3.tpo.juego.Mapa;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.seed.MapaInicial;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Un test (o varios) por cada algoritmo implementado (requisito del hito 9).
 *
 * NO tocan Neo4j: son unitarios sobre el grafo en memoria y corren en
 * milisegundos. El mapa se arma con los MISMOS datos que CargaInicial guarda
 * en la base (MapaInicial), asi que lo que se verifica es el dominio real.
 * Los valores esperados estan calculados a mano en el README.
 */
class AlgoritmosTest {

    /** El mapa del juego: 12 territorios y 17 rutas, no dirigido. */
    static Grafo mapa() {
        Grafo g = new Grafo(false);
        for (Mapa.Territorio t : MapaInicial.TERRITORIOS) {
            g.agregarVertice(t.id());
        }
        for (Mapa.Ruta r : MapaInicial.RUTAS) {
            g.agregarArista(r.origen(), r.destino(), r.costo());
        }
        return g;
    }

    /** El catalogo del mercado: peso = costo en oro, valor = poder. */
    static List<Item> catalogo() {
        return MapaInicial.catalogo();
    }

    /** La semilla A..H del scaffold, con los valores de verificacion de GUIA-TPO.md. */
    static Grafo semilla(boolean dirigido) {
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

    static List<String> ids(List<Item> lista) {
        List<String> r = new ArrayList<>();
        for (Item i : lista) {
            r.add(i.getId());
        }
        return r;
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Hito 4 - BFS y DFS sobre el mapa")
    class RecorridosTest {

        private final Recorridos recorridos = new Recorridos();

        @Test
        void bfsDesdeAguazulVisitaPorNiveles() {
            // nivel 0: AZU | 1: PIN VAD | 2: CON NIE IGN | 3: COR EBA HEL | 4: SEL DOR | 5: BRA
            assertEquals(List.of("AZU", "PIN", "VAD", "CON", "NIE", "IGN", "COR", "EBA", "HEL", "SEL", "DOR", "BRA"),
                    recorridos.bfs(mapa(), "AZU"));
        }

        @Test
        void dfsDesdeAguazulVaEnProfundidad() {
            assertEquals(List.of("AZU", "PIN", "CON", "COR", "IGN", "VAD", "SEL", "BRA", "DOR", "EBA", "NIE", "HEL"),
                    recorridos.dfs(mapa(), "AZU"));
        }

        @Test
        void dfsIterativoDaElMismoOrdenQueElRecursivo() {
            assertEquals(recorridos.dfs(mapa(), "AZU"), recorridos.dfsIterativo(mapa(), "AZU"));
        }

        @Test
        void bfsNoRepiteVerticesAunqueHayaCiclos() {
            List<String> orden = recorridos.bfs(mapa(), "EBA");
            assertEquals(12, orden.size());
            assertEquals(new HashSet<>(orden).size(), orden.size(), "marcar al encolar evita duplicados");
        }

        @Test
        void bfsPorNivelesDaLosTurnosParaLlegarDesdeLaRegionAzul() {
            Map<String, Integer> niveles = recorridos.bfsPorNiveles(mapa(), List.of("AZU", "PIN", "VAD"), 2);
            assertEquals(Map.of("AZU", 0, "PIN", 0, "VAD", 0, "CON", 1, "NIE", 1, "IGN", 1,
                    "COR", 2, "EBA", 2, "HEL", 2), niveles);
            assertFalse(niveles.containsKey("BRA"), "BRA esta a mas de 2 conquistas");
        }

        @Test
        void componentesConexasDetectaUnImperioPartido() {
            assertEquals(1, recorridos.componentesConexas(mapa()).size(), "el mapa completo es conexo");
            // imperio con AZU-VAD de un lado y NIE-HEL del otro: dos componentes
            Grafo imperio = new Grafo(false);
            imperio.agregarArista("AZU", "VAD", 2);
            imperio.agregarArista("NIE", "HEL", 5);
            List<List<String>> comps = recorridos.componentesConexas(imperio);
            assertEquals(List.of(List.of("AZU", "VAD"), List.of("NIE", "HEL")), comps);
        }

        @Test
        void origenInexistenteFallaConMensajeClaro() {
            assertThrows(IllegalArgumentException.class, () -> recorridos.bfs(mapa(), "XXX"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Hito 5 - Dijkstra, Prim y Kruskal verificados a mano sobre el mapa")
    class Hito5Test {

        private final CaminosMinimos caminos = new CaminosMinimos();
        private final ArbolRecubrimiento arbol = new ArbolRecubrimiento();

        @Test
        void dijkstraBordeaElPasoDeMontanaEnLugarDeCruzarlo() {
            // PIN-CON directo cuesta 10; PIN-AZU-VAD-CON cuesta 3+2+4 = 9
            var c = caminos.dijkstra(mapa(), "PIN", "CON");
            assertEquals(9.0, c.costoTotal(), 1e-9);
            assertEquals(List.of("PIN", "AZU", "VAD", "CON"), c.vertices());
        }

        @Test
        void dijkstraDeCapitalACapitalReconstruyeElCamino() {
            var c = caminos.dijkstra(mapa(), "AZU", "BRA");
            assertEquals(15.0, c.costoTotal(), 1e-9);
            assertEquals(List.of("AZU", "VAD", "CON", "EBA", "DOR", "BRA"), c.vertices());
        }

        @Test
        void dijkstraDesdeAguazulCoincideConElCalculoAMano() {
            Map<String, Double> esperado = Map.ofEntries(
                    Map.entry("AZU", 0.0), Map.entry("VAD", 2.0), Map.entry("PIN", 3.0), Map.entry("CON", 6.0),
                    Map.entry("NIE", 6.0), Map.entry("IGN", 8.0), Map.entry("EBA", 9.0), Map.entry("COR", 10.0),
                    Map.entry("HEL", 11.0), Map.entry("DOR", 13.0), Map.entry("SEL", 13.0), Map.entry("BRA", 15.0));
            Map<String, Double> obtenido = caminos.distanciasDesde(mapa(), "AZU");
            assertEquals(esperado, obtenido);
            for (var e : esperado.entrySet()) {
                assertEquals(e.getValue(), caminos.dijkstra(mapa(), "AZU", e.getKey()).costoTotal(), 1e-9);
            }
        }

        @Test
        void dijkstraRechazaPesosNegativos() {
            Grafo g = new Grafo(true);
            g.agregarArista("X", "Y", -5);
            assertThrows(IllegalArgumentException.class, () -> caminos.dijkstra(g, "X", "Y"));
        }

        @Test
        void dijkstraAvisaCuandoNoHayCamino() {
            Grafo g = new Grafo(false);
            g.agregarArista("AZU", "VAD", 2);
            g.agregarVertice("NIE");
            assertThrows(IllegalStateException.class, () -> caminos.dijkstra(g, "AZU", "NIE"));
        }

        @Test
        void primDaCosto37ConOnceRutas() {
            var mst = arbol.prim(mapa(), "AZU");
            assertEquals(37.0, mst.costoTotal(), 1e-9);
            assertEquals(11, mst.aristas().size(), "V-1 aristas con V=12");
        }

        @Test
        void kruskalDaElMismoCostoQuePrimYTocaLosDoceTerritorios() {
            var k = arbol.kruskal(mapa());
            assertEquals(37.0, k.costoTotal(), 1e-9);
            assertEquals(arbol.prim(mapa(), "AZU").costoTotal(), k.costoTotal(), 1e-9);
            Set<String> tocados = new HashSet<>();
            for (var a : k.aristas()) {
                tocados.add(a.origen());
                tocados.add(a.destino());
            }
            assertEquals(12, tocados.size());
            // las rutas caras (PIN-CON 10, EBA-SEL 10, DOR-HEL 6, IGN-VAD 6, EBA-NIE 4...) no entran
            for (var a : k.aristas()) {
                assertTrue(a.costo() <= 5, "ninguna ruta de 6 o mas entra al MST: " + a);
            }
        }

        @Test
        void elMstExigeGrafoNoDirigidoYConexo() {
            assertThrows(IllegalArgumentException.class, () -> arbol.prim(semilla(true), "A"));
            Grafo g = new Grafo(false);
            g.agregarArista("P", "Q", 1);
            g.agregarVertice("Z");
            assertThrows(IllegalStateException.class, () -> arbol.kruskal(g));
        }

        @Test
        void casosDeVerificacionDeLaGuiaSobreLaSemilla() {
            assertEquals(7.0, caminos.dijkstra(semilla(true), "A", "C").costoTotal(), 1e-9);
            assertEquals(20.0, caminos.dijkstra(semilla(true), "A", "H").costoTotal(), 1e-9);
            assertEquals(27.0, arbol.prim(semilla(false), "A").costoTotal(), 1e-9);
            assertEquals(27.0, arbol.kruskal(semilla(false)).costoTotal(), 1e-9);
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Hito 7 - Floyd-Warshall contra Dijkstra V veces")
    class Hito7Test {

        private final CaminosMinimos caminos = new CaminosMinimos();

        @Test
        void floydCoincideConDijkstraEnTodosLosPares() {
            Grafo g = mapa();
            Map<String, Double> todos = caminos.floydWarshall(g);
            assertEquals(144, todos.size(), "12 x 12 pares, todos alcanzables");
            for (String o : g.ids()) {
                for (String d : g.ids()) {
                    assertEquals(caminos.dijkstra(g, o, d).costoTotal(), todos.get(o + "->" + d), 1e-9,
                            "discrepancia en " + o + "->" + d);
                }
            }
        }

        @Test
        void comparacionDeTrabajoContraDijkstraVVeces() {
            var c = caminos.compararFloydContraDijkstra(mapa());
            assertEquals(12, c.vertices());
            assertEquals(17, c.aristas());
            assertEquals(1728, c.floydCombinacionesEvaluadas(), "V^3 = 12^3");
            assertEquals(158, c.floydMejoras());
            assertEquals(144, c.dijkstraVerticesExpandidos(), "V corridas x V vertices");
            assertEquals(408, c.dijkstraAristasRelajadas(), "V corridas x 2E entradas de adyacencia");
            // las inserciones dependen de como desempata el heap distancias iguales (con
            // PriorityQueue dan 169): solo se puede asegurar expandidos <= inserciones <= expandidos + relajaciones
            assertTrue(c.dijkstraInsercionesEnCola() >= 144 && c.dijkstraInsercionesEnCola() <= 144 + 408);
            assertTrue(c.mismosResultados());
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
            assertTrue(caminos.expandidosUcs(mapa(), "AZU", "VAD") < caminos.expandidosDijkstraCompleto(mapa(), "AZU"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Hito 2 - QuickSort y MergeSort (genericos)")
    class OrdenamientoTest {

        private final Ordenamiento ordenamiento = new Ordenamiento();
        private final Comparator<Item> porCosto = Comparator.comparingDouble(Item::getPeso);

        @Test
        void quickSortOrdenaElCatalogoPorPoderDescendente() {
            assertEquals(List.of("U6", "U5", "U4", "U3", "U2", "U1"),
                    ids(ordenamiento.quickSort(catalogo(), (a, b) -> Double.compare(b.getValor(), a.getValor()))));
        }

        @Test
        void mergeSortOrdenaPorRatioYEsEstable() {
            // U3 y U5 tienen el mismo ratio (1.5): salen en el orden de entrada
            assertEquals(List.of("U1", "U3", "U5", "U4", "U6", "U2"),
                    ids(ordenamiento.mergeSort(catalogo(), Comparator.comparingDouble(Item::ratioValorPeso))));
        }

        @Test
        void quickSortYMergeSortCoincidenSobreDatosAleatorios() {
            Random rnd = new Random(7);
            List<Integer> datos = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                datos.add(rnd.nextInt(1000));
            }
            List<Integer> q = ordenamiento.quickSort(datos, Integer::compare);
            assertEquals(q, ordenamiento.mergeSort(datos, Integer::compare));
            for (int i = 1; i < q.size(); i++) {
                assertTrue(q.get(i - 1) <= q.get(i));
            }
        }

        @Test
        void ordenaTerritoriosPorProduccion() {
            List<Mapa.Territorio> porProduccion = ordenamiento.mergeSort(MapaInicial.TERRITORIOS,
                    (a, b) -> Integer.compare(b.produccion(), a.produccion()));
            assertEquals(List.of("AZU", "BRA", "COR", "NIE"),
                    porProduccion.subList(0, 4).stream().map(Mapa.Territorio::id).toList(),
                    "las cuatro ciudades (produccion 3) primero, en orden alfabetico por estabilidad");
        }

        @Test
        void ningunoModificaLaListaDeEntradaYNoRompenConCasosBorde() {
            List<Item> original = new ArrayList<>(catalogo());
            ordenamiento.quickSort(original, porCosto.reversed());
            ordenamiento.mergeSort(original, porCosto.reversed());
            assertEquals(List.of("U1", "U2", "U3", "U4", "U5", "U6"), ids(original));
            assertTrue(ordenamiento.quickSort(List.<Item>of(), porCosto).isEmpty());
            assertEquals(1, ordenamiento.mergeSort(catalogo().subList(0, 1), porCosto).size());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Hito 3 y 6 - Greedy contra mochila 0/1 en la compra de unidades")
    class SeleccionTest {

        private final Seleccion seleccion = new Seleccion(new Ordenamiento());

        @Test
        void greedyConSeisDeOroCompraArquerosYMilicia() {
            var r = seleccion.greedy(catalogo(), 6);
            assertEquals(List.of("U2", "U1"), ids(r.elegidos()));
            assertEquals(6.0, r.valorTotal(), 1e-9);
            assertEquals(4.0, r.pesoTotal(), 1e-9, "le sobran 2 de oro");
        }

        @Test
        void mochilaConSeisDeOroEncuentraPoderNueve() {
            var r = seleccion.programacionDinamica(catalogo(), 6);
            assertEquals(9.0, r.valorTotal(), 1e-9);
            assertEquals(List.of("U1", "U4"), ids(r.elegidos()), "Milicia + Caballeria (empata con la Catapulta)");
            assertEquals(6.0, r.pesoTotal(), 1e-9, "usa todo el oro");
        }

        @Test
        void contraejemploGreedyNoEsOptimoEnVariosPresupuestos() {
            // presupuesto -> {greedy, optimo}
            int[][] casos = {{6, 6, 9}, {10, 14, 15}, {14, 19, 22}, {19, 27, 30}};
            for (int[] c : casos) {
                assertEquals(c[1], seleccion.greedy(catalogo(), c[0]).valorTotal(), 1e-9, "greedy con " + c[0]);
                assertEquals(c[2], seleccion.programacionDinamica(catalogo(), c[0]).valorTotal(), 1e-9, "PD con " + c[0]);
            }
        }

        @Test
        void conAlgunosPresupuestosGreedyAcierta() {
            // greedy no siempre falla: con 8 de oro Arqueros + Caballeria = 13 es optimo
            assertEquals(seleccion.programacionDinamica(catalogo(), 8).valorTotal(),
                    seleccion.greedy(catalogo(), 8).valorTotal(), 1e-9);
        }

        @Test
        void capacidadCeroYPesosNoEnteros() {
            assertTrue(seleccion.programacionDinamica(catalogo(), 0).elegidos().isEmpty());
            assertTrue(seleccion.greedy(catalogo(), 0).elegidos().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> seleccion.programacionDinamica(
                    List.of(new Item("F", "f", 2.5, 10, null)), 10));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Busqueda binaria sobre el catalogo ordenado por costo")
    class BusquedaBinariaTest {

        private final BusquedaBinaria bb = new BusquedaBinaria();
        private final List<Item> porCosto = new Ordenamiento().mergeSort(catalogo(),
                Comparator.comparingDouble(Item::getPeso));

        @Test
        void ultimaUnidadQueSePuedePagar() {
            assertEquals(4, bb.ultimoMenorOIgual(porCosto, 6, Item::getPeso), "U5 cuesta 6");
            assertEquals(3, bb.ultimoMenorOIgual(porCosto, 5.5, Item::getPeso), "U4 cuesta 5");
            assertEquals(-1, bb.ultimoMenorOIgual(porCosto, 0, Item::getPeso), "con 0 no se paga nada");
            assertEquals(5, bb.ultimoMenorOIgual(porCosto, 100, Item::getPeso));
        }

        @Test
        void asequiblesEsElPrefijoDelCatalogo() {
            assertEquals(List.of("U1", "U2", "U3", "U4", "U5"), ids(bb.asequibles(porCosto, 6)));
            assertTrue(bb.asequibles(porCosto, 0).isEmpty());
        }

        @Test
        void busquedaExacta() {
            assertEquals("U4", porCosto.get(bb.buscar(porCosto, 5, Item::getPeso)).getId());
            assertEquals(-1, bb.buscar(porCosto, 7, Item::getPeso), "no hay unidad de costo 7");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("AVL con consulta por rango")
    class ArbolAvlTest {

        @Test
        void insertarOrdenadoNoDegeneraEnLista() {
            ArbolAVL<Integer> avl = new ArbolAVL<>();
            for (int i = 1; i <= 1000; i++) {
                avl.insertar(i, i); // el peor caso de un ABB comun
            }
            assertTrue(avl.estaBalanceado());
            assertTrue(avl.altura() <= 1.45 * Math.log(1002) / Math.log(2), "altura " + avl.altura());
            assertTrue(avl.rotaciones() > 0);
            assertEquals(1000, avl.tamanio());
        }

        @Test
        void enOrdenDevuelveTodoOrdenado() {
            ArbolAVL<Integer> avl = new ArbolAVL<>();
            Random rnd = new Random(3);
            List<Integer> valores = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                int v = rnd.nextInt(100); // con repetidos
                valores.add(v);
                avl.insertar(v, v);
            }
            assertEquals(new Ordenamiento().mergeSort(valores, Integer::compare), avl.enOrden());
            assertTrue(avl.estaBalanceado());
        }

        @Test
        void rangoYKMejoresSobreLosTerritoriosNeutrales() {
            // defensa inicial de los neutrales: tropas (1 + produccion) + bonus de terreno
            ArbolAVL<Mapa.Territorio> avl = new ArbolAVL<>();
            for (Mapa.Territorio t : MapaInicial.TERRITORIOS) {
                avl.insertar(1 + t.produccion() + t.terreno().bonusDefensa(), t);
            }
            // defensa <= 4: llanuras (3) y montanas y bosques (4)
            List<String> enRango = avl.rango(0, 4).stream().map(Mapa.Territorio::id).toList();
            assertEquals(Set.of("DOR", "VAD", "CON", "HEL", "IGN", "EBA", "PIN", "SEL"), new HashSet<>(enRango));
            List<String> mejores = avl.kMejoresEnRango(0, 4, 3, Mapa.Territorio::produccion)
                    .stream().map(Mapa.Territorio::id).toList();
            assertEquals(3, mejores.size());
            for (String id : mejores) {
                assertTrue(Set.of("DOR", "VAD", "EBA", "PIN", "SEL").contains(id), "produccion 2: " + id);
            }
            assertEquals(4, avl.rango(5, 5).size(), "las cuatro ciudades tienen defensa 5");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Huffman sobre el replay")
    class HuffmanTest {

        private final Huffman huffman = new Huffman();

        @Test
        void losSimbolosFrecuentesTienenCodigosMasCortos() {
            var c = huffman.comprimir("AAAAAAABBBCD");
            assertEquals(1, c.codigos().get('A').length());
            assertTrue(c.codigos().get('D').length() > c.codigos().get('A').length());
            assertEquals(7 * 1 + 3 * 2 + 3 + 3, c.bitsComprimidos(), "A=1 bit, B=2, C=3, D=3");
        }

        @Test
        void esUnCodigoPrefijoYDecodificaIgual() {
            String replay = "1A:CU1@AZU,CU4@PIN,APIN>NIEx11+;1R:CU1@BRA,CU4@SEL,MBRA>SELx5,ASEL>CORx16+;";
            var c = huffman.comprimir(replay);
            List<String> codigos = new ArrayList<>(c.codigos().values());
            for (String a : codigos) {
                for (String b : codigos) {
                    assertTrue(a.equals(b) || !b.startsWith(a), a + " es prefijo de " + b);
                }
            }
            assertEquals(replay, huffman.descomprimir(c.bits(), c.arbol()));
            assertTrue(c.tasaCompresion() < 0.7, "el replay comprime bien: " + c.tasaCompresion());
        }

        @Test
        void casosBorde() {
            var uno = huffman.comprimir("ZZZZ");
            assertEquals("0", uno.codigos().get('Z'));
            assertEquals("ZZZZ", huffman.descomprimir(uno.bits(), uno.arbol()));
            assertThrows(IllegalArgumentException.class, () -> huffman.comprimir(""));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Hito 8 - Backtracking de rutas de invasion con poda")
    class Hito8Test {

        private final Backtracking backtracking = new Backtracking();

        @Test
        void hay38RutasSimplesDeCapitalACapital() {
            assertEquals(38, backtracking.rutasSimples(mapa(), "AZU", "BRA", 1000, 20).size());
        }

        @Test
        void conLimiteDe22DiasY6SaltosQuedanSiete() {
            var rutas = backtracking.rutasSimples(mapa(), "AZU", "BRA", 22, 6);
            assertEquals(7, rutas.size());
            double minimo = Double.MAX_VALUE;
            for (var r : rutas) {
                assertTrue(r.costoTotal() <= 22);
                assertTrue(r.vertices().size() - 1 <= 6);
                assertEquals(new HashSet<>(r.vertices()).size(), r.vertices().size(), "ruta simple");
                minimo = Math.min(minimo, r.costoTotal());
            }
            assertEquals(15.0, minimo, 1e-9, "la mas barata coincide con Dijkstra");
        }

        @Test
        void laPodaExploraMenosNodosSinPerderRutas() {
            var c = backtracking.compararPoda(mapa(), "AZU", "BRA", 22, 6);
            assertEquals(50, c.exploradosConPoda());
            assertEquals(93, c.exploradosSinPoda());
            assertEquals(7, c.rutasEncontradas());

            var c2 = backtracking.compararPoda(mapa(), "AZU", "BRA", 20, 5);
            assertEquals(43, c2.exploradosConPoda());
            assertEquals(65, c2.exploradosSinPoda());
            assertEquals(5, c2.rutasEncontradas());
        }

        @Test
        void trecesRutasEnLaSemillaDeLaGuia() {
            assertEquals(13, backtracking.rutasSimples(semilla(true), "A", "H", 1000, 10).size());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Backtracking tipo 1 - generador de escenarios")
    class GeneradorDeEscenarioTest {

        private final GeneradorDeEscenario generador = new GeneradorDeEscenario(new Ordenamiento());
        private final Grafo g = mapa();
        private final double[][] dist = new CaminosMinimos().floydMatriz(g);
        private final int[] produccion = MapaInicial.TERRITORIOS.stream().mapToInt(Mapa.Territorio::produccion).toArray();
        private final boolean[] ciudades = new boolean[12];

        {
            for (int i = 0; i < 12; i++) {
                ciudades[i] = MapaInicial.TERRITORIOS.get(i).terreno().puedeSerCapital();
            }
        }

        @Test
        void escenarioClasicoEsAguazulContraBrasaroja() {
            var e = generador.generar(g, produccion, ciudades, dist, 3, 10, 0, null);
            assertEquals("AZU", e.capitalAzul());
            assertEquals(List.of("AZU", "PIN", "VAD"), e.regionAzul());
            assertEquals("BRA", e.capitalRoja());
            assertEquals(List.of("BRA", "DOR", "SEL"), e.regionRoja());
            assertEquals(7, e.produccionAzul());
            assertEquals(7, e.produccionRoja());
        }

        @Test
        void escenariosAleatoriosCumplenTodasLasRestricciones() {
            Recorridos recorridos = new Recorridos();
            for (long semilla = 1; semilla <= 25; semilla++) {
                var e = generador.generar(g, produccion, ciudades, dist, 3, 10, 1, semilla);
                assertTrue(dist[g.indiceDe(e.capitalAzul())][g.indiceDe(e.capitalRoja())] >= 10, "R1");
                assertTrue(Math.abs(e.produccionAzul() - e.produccionRoja()) <= 1, "R4");
                for (List<String> region : List.of(e.regionAzul(), e.regionRoja())) {
                    assertEquals(3, region.size());
                    Grafo sub = new Grafo(false);
                    region.forEach(sub::agregarVertice);
                    for (Mapa.Ruta r : MapaInicial.RUTAS) {
                        if (region.contains(r.origen()) && region.contains(r.destino())) {
                            sub.agregarArista(r.origen(), r.destino(), r.costo());
                        }
                    }
                    assertEquals(1, recorridos.componentesConexas(sub).size(), "R2: region conexa " + region);
                }
                for (String a : e.regionAzul()) {
                    for (Grafo.Arista ar : g.vecinos(g.indiceDe(a))) {
                        assertFalse(e.regionRoja().contains(g.idDe(ar.destino())), "R3: no se tocan");
                    }
                }
            }
        }

        @Test
        void restriccionesImposiblesSeInforman() {
            assertThrows(IllegalStateException.class,
                    () -> generador.generar(g, produccion, ciudades, dist, 3, 100, 0, null));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Hito 11 - Branch & Bound de reparto de refuerzos")
    class Hito11Test {

        private final RamificacionYPoda byp = new RamificacionYPoda(new Ordenamiento());

        @Test
        void dosColumnasLlegan21DondeGreedySeQuedaEn20() {
            // poderes 13, 9, 8, 6, 5, 1 = 42. Optimo 21: {13, 8} y {9, 6, 5, 1}
            var r = byp.repartir(catalogo(), 2);
            assertEquals(21.0, r.margenMinimo(), 1e-9);
            assertEquals(20.0, r.solucionInicial(), 1e-9, "greedy al mas debil da 20");
            assertEquals(21.0, r.cotaRaiz(), 1e-9, "42 / 2");
            assertTrue(r.nodosPodados() > 0, "si no poda nada, la cota no sirve");
        }

        @Test
        void tresColumnasLleganAlPromedio() {
            var r = byp.repartir(catalogo(), 3);
            assertEquals(14.0, r.margenMinimo(), 1e-9);
            int total = r.porFrente().values().stream().mapToInt(List::size).sum();
            assertEquals(6, total, "ninguna unidad se pierde ni se duplica");
        }

        @Test
        void frentesConMargenInicialDistinto() {
            // un frente ya muy fuerte no deberia recibir nada
            var frentes = List.of(new RamificacionYPoda.Frente("CON", -4),
                    new RamificacionYPoda.Frente("NIE", 2), new RamificacionYPoda.Frente("AZU", 40));
            var r = byp.repartir(catalogo(), frentes);
            assertTrue(r.porFrente().get("AZU").isEmpty());
            // CON recibe 13+6+5 (=20) y NIE 9+8+1 (=20): los dos frentes debiles quedan parejos
            assertEquals(20.0, r.margenMinimo(), 1e-9);
        }

        @Test
        void coincideConLaFuerzaBrutaEnInstanciasAleatorias() {
            Random rnd = new Random(11);
            for (int caso = 0; caso < 40; caso++) {
                int n = 2 + rnd.nextInt(5);
                int k = 1 + rnd.nextInt(3);
                List<Item> unidades = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    unidades.add(new Item("X" + i, "x", 1, 1 + rnd.nextInt(12), null));
                }
                List<RamificacionYPoda.Frente> frentes = new ArrayList<>();
                for (int j = 0; j < k; j++) {
                    frentes.add(new RamificacionYPoda.Frente("F" + j, rnd.nextInt(9) - 4));
                }
                assertEquals(fuerzaBruta(unidades, frentes), byp.repartir(unidades, frentes).margenMinimo(), 1e-9,
                        "caso " + caso);
            }
        }

        /** Prueba las k^n asignaciones. Solo sirve para validar el B&B en casos chicos. */
        private double fuerzaBruta(List<Item> unidades, List<RamificacionYPoda.Frente> frentes) {
            int n = unidades.size();
            int k = frentes.size();
            double mejor = Double.NEGATIVE_INFINITY;
            int total = (int) Math.pow(k, n);
            for (int codigo = 0; codigo < total; codigo++) {
                double[] carga = new double[k];
                for (int j = 0; j < k; j++) {
                    carga[j] = frentes.get(j).margen();
                }
                int c = codigo;
                for (int i = 0; i < n; i++) {
                    carga[c % k] += unidades.get(i).getValor();
                    c /= k;
                }
                double minimo = Double.POSITIVE_INFINITY;
                for (double v : carga) {
                    minimo = Math.min(minimo, v);
                }
                mejor = Math.max(mejor, minimo);
            }
            return mejor;
        }

        @Test
        void sinFrentesFalla() {
            assertThrows(IllegalArgumentException.class, () -> byp.repartir(catalogo(), 0));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("MIN-MAX y alfa-beta sobre los fosforos de la clase 11")
    class MinimaxTest {

        /** Fosforos: se sacan 1 o 2; pierde el que se queda con el ultimo (n == 1 en su turno). */
        record EstadoFosforos(int fosforos, boolean turnoMax) { }

        static final class Fosforos implements JuegoDeDosJugadores<EstadoFosforos, Integer> {
            @Override
            public List<Integer> jugadas(EstadoFosforos e) {
                List<Integer> r = new ArrayList<>();
                for (int q = 1; q <= 2 && q < e.fosforos(); q++) {
                    r.add(q);
                }
                return r;
            }

            @Override
            public EstadoFosforos aplicar(EstadoFosforos e, Integer quitar) {
                return new EstadoFosforos(e.fosforos() - quitar, !e.turnoMax());
            }

            @Override
            public boolean esTerminal(EstadoFosforos e) {
                return e.fosforos() == 1;
            }

            @Override
            public double evaluar(EstadoFosforos e) {
                return e.turnoMax() ? -Minimax.VICTORIA : Minimax.VICTORIA; // al que le toca con 1, pierde
            }

            @Override
            public boolean turnoDeMax(EstadoFosforos e) {
                return e.turnoMax();
            }
        }

        private final Minimax minimax = new Minimax();
        private final Fosforos fosforos = new Fosforos();

        @Test
        void conCincoGanaMaxSacandoUno() {
            var d = minimax.minimax(fosforos, new EstadoFosforos(5, true), 20);
            assertTrue(d.valor() > 0, "con 5 fosforos gana el que mueve");
            assertEquals(1, d.jugada(), "deja al rival en 4, posicion perdedora");
        }

        @Test
        void sePierdeExactamenteCuandoNEsCongruenteConUnoModuloTres() {
            for (int n = 2; n <= 16; n++) {
                double v = minimax.minimax(fosforos, new EstadoFosforos(n, true), 30).valor();
                assertEquals(n % 3 == 1, v < 0, "n = " + n);
            }
        }

        @Test
        void alfaBetaDaElMismoValorMirandoMenosNodos() {
            var mm = minimax.minimax(fosforos, new EstadoFosforos(15, true), 30);
            var ab = minimax.alfaBeta(fosforos, new EstadoFosforos(15, true), 30);
            assertEquals(mm.valor(), ab.valor(), 1e-9);
            assertEquals(mm.jugada(), ab.jugada());
            assertTrue(ab.nodosVisitados() < mm.nodosVisitados(),
                    "alfa-beta " + ab.nodosVisitados() + " contra minimax " + mm.nodosVisitados());
            assertTrue(ab.cortes() > 0);
        }

        /** Un arbol de juego explicito: nombre de nodo -> hijos, y valores en las hojas. */
        record Nodo(String nombre, boolean max) { }

        static final class Arbol implements JuegoDeDosJugadores<Nodo, String> {
            private final Map<String, List<String>> hijos;
            private final Map<String, Double> hojas;

            Arbol(Map<String, List<String>> hijos, Map<String, Double> hojas) {
                this.hijos = hijos;
                this.hojas = hojas;
            }

            @Override public List<String> jugadas(Nodo e) { return hijos.getOrDefault(e.nombre(), List.of()); }
            @Override public Nodo aplicar(Nodo e, String hijo) { return new Nodo(hijo, !e.max()); }
            @Override public boolean esTerminal(Nodo e) { return hojas.containsKey(e.nombre()); }
            @Override public double evaluar(Nodo e) { return hojas.getOrDefault(e.nombre(), 0.0); }
            @Override public boolean turnoDeMax(Nodo e) { return e.max(); }
        }

        @Test
        void elArbolDeLaClase13DosDeNueveHojasNoSeMiran() {
            // raiz MAX con tres MIN: [3, 12, 8], [2, 4, 6], [14, 5, 2]. Valor 3.
            Map<String, List<String>> hijos = Map.of(
                    "raiz", List.of("m1", "m2", "m3"),
                    "m1", List.of("a", "b", "c"), "m2", List.of("d", "e", "f"), "m3", List.of("g", "h", "i"));
            Map<String, Double> hojas = Map.of("a", 3.0, "b", 12.0, "c", 8.0, "d", 2.0, "e", 4.0, "f", 6.0,
                    "g", 14.0, "h", 5.0, "i", 2.0);
            Arbol arbol = new Arbol(hijos, hojas);
            var mm = minimax.minimax(arbol, new Nodo("raiz", true), 2);
            var ab = minimax.alfaBeta(arbol, new Nodo("raiz", true), 2);
            assertEquals(3.0, mm.valor(), 1e-9);
            assertEquals(3.0, ab.valor(), 1e-9);
            assertEquals("m1", ab.jugada());
            assertEquals(13, mm.nodosVisitados(), "raiz + 3 MIN + 9 hojas");
            assertEquals(11, ab.nodosVisitados(), "el 4 y el 6 no se miran");
        }

        @Test
        void prefiereLaVictoriaMasRapida() {
            // "lenta" gana dentro de dos jugadas mas; "rapida" gana ya. Las dos valen VICTORIA,
            // pero restando la profundidad la rapida vale mas aunque se mire segunda.
            Map<String, List<String>> hijos = Map.of(
                    "raiz", List.of("lenta", "rapida"), "lenta", List.of("l2"), "l2", List.of("fin"));
            Map<String, Double> hojas = Map.of("rapida", Minimax.VICTORIA, "fin", Minimax.VICTORIA);
            var d = minimax.minimax(new Arbol(hijos, hojas), new Nodo("raiz", true), 5);
            assertEquals("rapida", d.jugada());
            assertEquals(Minimax.VICTORIA - 1, d.valor(), 1e-9);
        }

        @Test
        void laRaizTieneQueSerDeMax() {
            assertThrows(IllegalArgumentException.class,
                    () -> minimax.minimax(fosforos, new EstadoFosforos(5, false), 3));
            assertThrows(IllegalArgumentException.class,
                    () -> minimax.alfaBeta(fosforos, new EstadoFosforos(5, true), 0));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Heuristica greedy ganancia / riesgo")
    class HeuristicaGreedyTest {

        private final HeuristicaGreedy greedy = new HeuristicaGreedy(new Ordenamiento());

        record Objetivo(String id, double ganancia, double riesgo) { }

        @Test
        void ordenaPorRatioYDesempataPorOrdenDeEntrada() {
            List<Objetivo> candidatos = List.of(new Objetivo("A", 4, 4), new Objetivo("B", 6, 2),
                    new Objetivo("C", 2, 2), new Objetivo("D", 1000, 9));
            List<String> orden = greedy.rankingPorRatio(candidatos, Objetivo::ganancia, Objetivo::riesgo)
                    .stream().map(Objetivo::id).toList();
            assertEquals(List.of("D", "B", "A", "C"), orden, "A y C empatan en 1.0: queda primero A");
            assertEquals("D", greedy.elegir(candidatos, Objetivo::ganancia, Objetivo::riesgo).id());
            assertNull(greedy.elegir(List.<Objetivo>of(), Objetivo::ganancia, Objetivo::riesgo));
        }
    }
}
