package uade.prog3.tpo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uade.prog3.tpo.algorithm.GeneradorDeEscenario;
import uade.prog3.tpo.juego.Dificultad;
import uade.prog3.tpo.juego.Estratega;
import uade.prog3.tpo.juego.Jugadas.Compra;
import uade.prog3.tpo.juego.Jugadas.Movimiento;
import uade.prog3.tpo.service.JuegoService;

import java.util.List;
import java.util.Map;

/**
 * Endpoints del juego. Igual que los demas controllers: solo traducen HTTP a
 * llamadas al servicio. Ninguna logica de juego ni algoritmica vive aca.
 *
 * El humano juega con AZUL. Al terminar su turno, la IA (ROJO) juega el suyo
 * en la misma request y la respuesta ya trae el estado actualizado.
 */
@RestController
@RequestMapping("/api/juego")
public class JuegoController {

    private final JuegoService juego;

    public JuegoController(JuegoService juego) {
        this.juego = juego;
    }

    /** Cuerpo de POST /comprar. */
    public record PedidoDeCompra(List<Compra> compras) { }

    /** POST /api/juego?dificultad=NORMAL&escenario=CLASICO|ALEATORIO&semilla=42 */
    @PostMapping
    public JuegoService.VistaPartida crear(@RequestParam(defaultValue = "NORMAL") String dificultad,
                                          @RequestParam(defaultValue = "CLASICO") String escenario,
                                          @RequestParam(required = false) Long semilla) {
        return juego.crear(dificultad(dificultad), esAleatorio(escenario), semilla);
    }

    /** GET /api/juego/escenario?semilla=7  (backtracking: un arranque valido, sin crear partida) */
    @GetMapping("/escenario")
    public GeneradorDeEscenario.Escenario escenario(@RequestParam(required = false) Long semilla) {
        return juego.generarEscenario(semilla);
    }

    @GetMapping("/{id}")
    public JuegoService.VistaPartida ver(@PathVariable String id) {
        return juego.vista(id);
    }

    /** Fase 2. Cuerpo: {"compras":[{"unidad":"U5","destino":"VAD"}]} */
    @PostMapping("/{id}/comprar")
    public JuegoService.VistaPartida comprar(@PathVariable String id, @RequestBody PedidoDeCompra pedido) {
        return juego.comprar(id, pedido.compras() == null ? List.of() : pedido.compras());
    }

    /** Fase 3. Cuerpo: {"origen":"AZU","destino":"VAD","tropas":4} */
    @PostMapping("/{id}/marchar")
    public JuegoService.VistaPartida marchar(@PathVariable String id, @RequestBody Movimiento marcha) {
        return juego.marchar(id, marcha);
    }

    /** Fase 4. Cuerpo: {"origen":"VAD","destino":"CON","tropas":11} */
    @PostMapping("/{id}/atacar")
    public JuegoService.VistaPartida atacar(@PathVariable String id, @RequestBody Movimiento ataque) {
        return juego.atacar(id, ataque);
    }

    /** Termina el turno del humano; la IA juega el suyo. */
    @PostMapping("/{id}/terminar")
    public JuegoService.VistaPartida terminar(@PathVariable String id) {
        return juego.terminarTurno(id);
    }

    // ---------------- consultas: un algoritmo cada una ----------------

    /** Dijkstra: GET /api/juego/{id}/marcha?origen=AZU&destino=CON */
    @GetMapping("/{id}/marcha")
    public JuegoService.VistaMarcha marcha(@PathVariable String id, @RequestParam String origen,
                                           @RequestParam String destino) {
        return juego.rutaDeMarcha(id, origen, destino);
    }

    /** Busqueda binaria + greedy + mochila 0/1 */
    @GetMapping("/{id}/mercado")
    public JuegoService.VistaMercado mercado(@PathVariable String id) {
        return juego.mercado(id);
    }

    /** Greedy ganancia/riesgo */
    @GetMapping("/{id}/sugerencias")
    public List<JuegoService.SugerenciaAtaque> sugerencias(@PathVariable String id) {
        return juego.sugerenciasDeAtaque(id);
    }

    /** AVL con consulta por rango: GET /api/juego/{id}/objetivos?min=0&max=8&k=3 */
    @GetMapping("/{id}/objetivos")
    public JuegoService.VistaObjetivos objetivos(@PathVariable String id,
                                                 @RequestParam(defaultValue = "0") double min,
                                                 @RequestParam(required = false) Double max,
                                                 @RequestParam(defaultValue = "3") int k) {
        return juego.objetivos(id, min, max, k);
    }

    /** BFS por niveles: GET /api/juego/{id}/alcance?turnos=2 */
    @GetMapping("/{id}/alcance")
    public Map<String, Integer> alcance(@PathVariable String id, @RequestParam(defaultValue = "2") int turnos) {
        return juego.alcance(id, turnos);
    }

    /** DFS (componentes) + Prim + Kruskal: GET /api/juego/{id}/imperio?jugador=AZUL */
    @GetMapping("/{id}/imperio")
    public JuegoService.VistaImperio imperio(@PathVariable String id,
                                             @RequestParam(required = false) String jugador) {
        return juego.imperio(id, jugador);
    }

    /** Branch & Bound de reparto de refuerzos entre frentes */
    @GetMapping("/{id}/reparto")
    public JuegoService.VistaReparto reparto(@PathVariable String id) {
        return juego.repartir(id);
    }

    /** QuickSort (valor) / MergeSort (amenaza): GET /api/juego/{id}/ranking?criterio=valor */
    @GetMapping("/{id}/ranking")
    public List<JuegoService.VistaRanking> ranking(@PathVariable String id,
                                                   @RequestParam(defaultValue = "valor") String criterio) {
        return juego.ranking(id, criterio);
    }

    /** MIN-MAX contra alfa-beta: GET /api/juego/{id}/ia/comparacion?profundidad=3 */
    @GetMapping("/{id}/ia/comparacion")
    public Estratega.ComparacionIa comparacionIa(@PathVariable String id,
                                                 @RequestParam(defaultValue = "3") int profundidad) {
        return juego.comparacionIa(id, profundidad);
    }

    /** Alfa-beta para el humano: que haria la IA en tu lugar */
    @GetMapping("/{id}/consejo")
    public Estratega.ReporteIa consejo(@PathVariable String id) {
        return juego.consejo(id);
    }

    /** Huffman sobre el replay */
    @GetMapping("/{id}/replay")
    public JuegoService.VistaReplay replay(@PathVariable String id) {
        return juego.replay(id);
    }

    /** Backtracking de rutas de invasion: GET /api/juego/{id}/invasion?costoMaximo=22&saltosMaximos=6 */
    @GetMapping("/{id}/invasion")
    public JuegoService.VistaInvasion invasion(@PathVariable String id,
                                               @RequestParam(defaultValue = "22") double costoMaximo,
                                               @RequestParam(defaultValue = "6") int saltosMaximos) {
        return juego.rutasDeInvasion(id, costoMaximo, saltosMaximos);
    }

    private Dificultad dificultad(String texto) {
        try {
            return Dificultad.valueOf(texto.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Dificultad invalida: " + texto + ". Use FACIL, NORMAL o DIFICIL.");
        }
    }

    private boolean esAleatorio(String escenario) {
        return switch (escenario.toUpperCase()) {
            case "CLASICO" -> false;
            case "ALEATORIO" -> true;
            default -> throw new IllegalArgumentException("Escenario invalido: " + escenario + ". Use CLASICO o ALEATORIO.");
        };
    }
}
