package uade.prog3.tpo.controller;

import org.springframework.web.bind.annotation.*;
import uade.prog3.tpo.algorithm.ArbolRecubrimiento;
import uade.prog3.tpo.algorithm.Backtracking;
import uade.prog3.tpo.algorithm.CaminosMinimos;
import uade.prog3.tpo.algorithm.Recorridos;
import uade.prog3.tpo.service.Grafo;
import uade.prog3.tpo.service.GrafoService;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de los algoritmos sobre el grafo del mapa.
 *
 * Cada endpoint carga el grafo y delega en la clase de algoritmo
 * correspondiente. Los controllers NO contienen logica algoritmica.
 *
 * El mapa del juego es NO DIRIGIDO (una ruta se recorre en los dos sentidos al
 * mismo costo), asi que todos los algoritmos lo cargan con cargar(false).
 * /resumen admite ?dirigido=true para ver la carga dirigida del scaffold.
 */
@RestController
@RequestMapping("/api/grafo")
public class GrafoController {

    private final GrafoService grafoService;
    private final Recorridos recorridos;
    private final CaminosMinimos caminosMinimos;
    private final ArbolRecubrimiento arbolRecubrimiento;
    private final Backtracking backtracking;

    public GrafoController(GrafoService grafoService,
                           Recorridos recorridos,
                           CaminosMinimos caminosMinimos,
                           ArbolRecubrimiento arbolRecubrimiento,
                           Backtracking backtracking) {
        this.grafoService = grafoService;
        this.recorridos = recorridos;
        this.caminosMinimos = caminosMinimos;
        this.arbolRecubrimiento = arbolRecubrimiento;
        this.backtracking = backtracking;
    }

    private Grafo mapa() {
        return grafoService.cargar(false);
    }

    /** Metadatos del grafo cargado. Sirve para verificar la conexion a la base. */
    @GetMapping("/resumen")
    public Map<String, Object> resumen(@RequestParam(defaultValue = "false") boolean dirigido) {
        Grafo g = grafoService.cargar(dirigido);
        return Map.of(
                "vertices", g.cantidadVertices(),
                "aristas", g.cantidadAristas(),
                "dirigido", g.esDirigido(),
                "ids", g.ids());
    }

    /** GET /api/grafo/dfs?origen=AZU */
    @GetMapping("/dfs")
    public List<String> dfs(@RequestParam String origen) {
        return recorridos.dfs(mapa(), origen);
    }

    /** GET /api/grafo/bfs?origen=AZU */
    @GetMapping("/bfs")
    public List<String> bfs(@RequestParam String origen) {
        return recorridos.bfs(mapa(), origen);
    }

    /** GET /api/grafo/alcance?origenes=AZU,PIN,VAD&k=2  (BFS multi-fuente por niveles) */
    @GetMapping("/alcance")
    public Map<String, Integer> alcance(@RequestParam List<String> origenes,
                                        @RequestParam(defaultValue = "2") int k) {
        return recorridos.bfsPorNiveles(mapa(), origenes, k);
    }

    /** GET /api/grafo/componentes  (DFS repetido: componentes conexas) */
    @GetMapping("/componentes")
    public List<List<String>> componentes() {
        return recorridos.componentesConexas(mapa());
    }

    /** GET /api/grafo/dijkstra?origen=AZU&destino=BRA */
    @GetMapping("/dijkstra")
    public CaminosMinimos.Camino dijkstra(@RequestParam String origen, @RequestParam String destino) {
        return caminosMinimos.dijkstra(mapa(), origen, destino);
    }

    /** GET /api/grafo/floyd */
    @GetMapping("/floyd")
    public Map<String, Double> floyd() {
        return caminosMinimos.floydWarshall(mapa());
    }

    /** GET /api/grafo/floyd/comparacion  (hito 7: Floyd contra Dijkstra V veces) */
    @GetMapping("/floyd/comparacion")
    public CaminosMinimos.ComparacionTodosContraTodos floydContraDijkstra() {
        return caminosMinimos.compararFloydContraDijkstra(mapa());
    }

    /** GET /api/grafo/ucs?origen=AZU&destino=CON */
    @GetMapping("/ucs")
    public Map<String, Object> ucs(@RequestParam String origen, @RequestParam String destino) {
        Grafo g = mapa();
        return Map.of(
                "camino", caminosMinimos.ucs(g, origen, destino),
                "expandidosUcs", caminosMinimos.expandidosUcs(g, origen, destino),
                "expandidosDijkstraCompleto", caminosMinimos.expandidosDijkstraCompleto(g, origen));
    }

    /** GET /api/grafo/prim?origen=AZU   (el MST exige grafo NO dirigido) */
    @GetMapping("/prim")
    public ArbolRecubrimiento.Mst prim(@RequestParam String origen) {
        return arbolRecubrimiento.prim(mapa(), origen);
    }

    /** GET /api/grafo/kruskal */
    @GetMapping("/kruskal")
    public ArbolRecubrimiento.Mst kruskal() {
        return arbolRecubrimiento.kruskal(mapa());
    }

    /** GET /api/grafo/rutas?origen=AZU&destino=BRA&costoMaximo=22&saltosMaximos=6 */
    @GetMapping("/rutas")
    public List<Backtracking.Ruta> rutas(@RequestParam String origen,
                                         @RequestParam String destino,
                                         @RequestParam(defaultValue = "1000") double costoMaximo,
                                         @RequestParam(defaultValue = "10") int saltosMaximos) {
        return backtracking.rutasSimples(mapa(), origen, destino, costoMaximo, saltosMaximos);
    }

    /** GET /api/grafo/rutas/poda?origen=AZU&destino=BRA&costoMaximo=22&saltosMaximos=6  (hito 8) */
    @GetMapping("/rutas/poda")
    public Backtracking.ComparacionPoda rutasConYSinPoda(@RequestParam String origen,
                                                         @RequestParam String destino,
                                                         @RequestParam(defaultValue = "1000") double costoMaximo,
                                                         @RequestParam(defaultValue = "10") int saltosMaximos) {
        return backtracking.compararPoda(mapa(), origen, destino, costoMaximo, saltosMaximos);
    }
}
