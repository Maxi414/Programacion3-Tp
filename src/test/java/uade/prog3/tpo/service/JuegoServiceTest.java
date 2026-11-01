package uade.prog3.tpo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uade.prog3.tpo.algorithm.*;
import uade.prog3.tpo.juego.Dificultad;
import uade.prog3.tpo.juego.Estratega;
import uade.prog3.tpo.juego.Jugadas.Compra;
import uade.prog3.tpo.juego.Jugadas.Movimiento;
import uade.prog3.tpo.juego.Mapa;
import uade.prog3.tpo.juego.MotorDeJuego;
import uade.prog3.tpo.seed.MapaInicial;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Una partida completa contra la IA a traves del servicio, sin Neo4j: el mapa
 * se arma con MapaInicial (los mismos datos que CargaInicial guarda en la base).
 */
class JuegoServiceTest {

    private JuegoService servicio;
    private String id;

    @BeforeEach
    void crear() {
        Ordenamiento ord = new Ordenamiento();
        CaminosMinimos caminos = new CaminosMinimos();
        Recorridos recorridos = new Recorridos();
        ArbolRecubrimiento arbol = new ArbolRecubrimiento();
        HeuristicaGreedy greedy = new HeuristicaGreedy(ord);
        MotorDeJuego motor = new MotorDeJuego(recorridos, caminos, arbol, new Seleccion(ord), new BusquedaBinaria());
        Estratega estratega = new Estratega(motor, caminos, greedy, ord, new Minimax());
        servicio = new JuegoService(null, null, motor, estratega, new GeneradorDeEscenario(ord), recorridos,
                caminos, arbol, new Backtracking(), new RamificacionYPoda(ord), ord, greedy, new Huffman(),
                new BusquedaBinaria());
        Mapa mapa = Mapa.construir(MapaInicial.TERRITORIOS, MapaInicial.RUTAS, MapaInicial.catalogo(), caminos, ord);
        id = servicio.crearPartida(mapa, Dificultad.NORMAL, false, null).id();
    }

    @Test
    void unTurnoCompletoYLaRespuestaDeLaIa() {
        var v = servicio.vista(id);
        assertEquals("AZUL", v.turno());
        assertEquals(6, v.jugadores().get("AZUL").oro());
        assertEquals(12, v.territorios().size());
        assertEquals(17, v.rutas().size());

        servicio.comprar(id, List.of(new Compra("U1", "VAD"), new Compra("U4", "VAD")));
        servicio.marchar(id, new Movimiento("AZU", "VAD", 3));
        v = servicio.atacar(id, new Movimiento("VAD", "CON", 14)); // 3 + 9 + 3 - 1
        assertEquals("AZUL", v.territorios().stream().filter(t -> t.id().equals("CON")).findFirst().orElseThrow().duenio());

        v = servicio.terminarTurno(id);
        assertEquals("AZUL", v.turno(), "la IA ya jugo y devolvio el turno");
        assertEquals(2, v.ronda());
        assertNotNull(v.ultimaJugadaIa());
        assertTrue(v.eventos().stream().anyMatch(ev -> ev.startsWith("ROJO piensa con ALFA-BETA")));

        var replay = servicio.replay(id);
        assertTrue(replay.texto().startsWith("1A:CU1@VAD,CU4@VAD,MAZU>VADx3,AVAD>CONx14+;1R:"), replay.texto());
        assertTrue(replay.decodificaIgual());
        assertTrue(replay.bitsComprimidos() < replay.bitsOriginales());
    }

    @Test
    void partidaInexistenteYReplayVacio() {
        servicio.terminarTurno(id);
        assertThrows(IllegalArgumentException.class, () -> servicio.vista("no-existe"));
        assertThrows(IllegalStateException.class, () -> servicio.replay(servicioNuevo()));
    }

    private String servicioNuevo() {
        Ordenamiento ord = new Ordenamiento();
        CaminosMinimos caminos = new CaminosMinimos();
        Mapa mapa = Mapa.construir(MapaInicial.TERRITORIOS, MapaInicial.RUTAS, MapaInicial.catalogo(), caminos, ord);
        return servicio.crearPartida(mapa, Dificultad.FACIL, false, null).id();
    }

    @Test
    void mercadoConBusquedaBinariaGreedyYMochila() {
        var m = servicio.mercado(id);
        assertEquals(6, m.oro());
        assertEquals(4, m.ultimoAsequible(), "U5 (costo 6) es la ultima que se puede pagar");
        assertEquals(5, m.asequibles().size());
        assertEquals(6.0, m.greedy().valorTotal(), 1e-9);
        assertEquals(9.0, m.mochila().valorTotal(), 1e-9);
        assertEquals(3.0, m.poderQuePierdeGreedy(), 1e-9);
    }

    @Test
    void consultasDeDijkstraBfsAvlYGreedy() {
        var marcha = servicio.rutaDeMarcha(id, "PIN", "VAD");
        assertEquals(List.of("PIN", "AZU", "VAD"), marcha.vertices());
        assertTrue(marcha.alcanza());

        Map<String, Integer> alcance = servicio.alcance(id, 1);
        assertEquals(Map.of("AZU", 0, "PIN", 0, "VAD", 0, "CON", 1, "NIE", 1, "IGN", 1), alcance);

        // AZU tiene 6 tropas: puede mandar 5, asi que conquista defensas de hasta 4
        var objetivos = servicio.objetivos(id, 0, null, 2);
        assertEquals(4.0, objetivos.defensaMaxima(), 1e-9);
        assertEquals(9, objetivos.territoriosEnElArbol());
        assertEquals(2, objetivos.masValiosos().size());
        assertTrue(objetivos.enRango().stream().allMatch(o -> o.defensa() <= 4));

        // al arrancar ningun territorio gana un ataque sin comprar antes
        assertTrue(servicio.sugerenciasDeAtaque(id).isEmpty());
        servicio.comprar(id, List.of(new Compra("U1", "VAD"), new Compra("U4", "VAD")));
        var sugerencias = servicio.sugerenciasDeAtaque(id);
        assertFalse(sugerencias.isEmpty());
        for (int k = 1; k < sugerencias.size(); k++) {
            assertTrue(sugerencias.get(k - 1).ratio() >= sugerencias.get(k).ratio(), "ordenadas por ratio");
        }
    }

    @Test
    void imperioRepartoRankingIaEInvasion() {
        var imperio = servicio.imperio(id, "AZUL");
        assertFalse(imperio.partido());
        assertTrue(imperio.mismoCosto(), "Prim y Kruskal coinciden");
        assertEquals(5.0, imperio.prim().costoTotal(), 1e-9);

        var reparto = servicio.repartir(id);
        assertEquals(9.0, reparto.unidades().stream().mapToDouble(u -> u.getValor()).sum(), 1e-9);
        assertEquals(reparto.unidades().size(), reparto.comoCompra().size());
        servicio.comprar(id, reparto.comoCompra()); // el reparto sugerido es una compra valida

        var ranking = servicio.ranking(id, "valor");
        assertEquals(12, ranking.size());
        for (int k = 1; k < ranking.size(); k++) {
            assertTrue(ranking.get(k - 1).puntaje() >= ranking.get(k).puntaje());
        }
        assertEquals(3, servicio.ranking(id, "amenaza").size());
        assertThrows(IllegalArgumentException.class, () -> servicio.ranking(id, "otro"));

        var invasion = servicio.rutasDeInvasion(id, 22, 6);
        assertEquals(15.0, invasion.masBarataPorDijkstra().costoTotal(), 1e-9);
        assertEquals(7, invasion.rutas().size());
        assertTrue(invasion.poda().exploradosConPoda() < invasion.poda().exploradosSinPoda());
    }

    @Test
    void comparacionYConsejoDeLaIa() {
        var c = servicio.comparacionIa(id, 3);
        assertTrue(c.mismoValor());
        assertTrue(c.nodosAlfaBeta() < c.nodosMinimax());
        var consejo = servicio.consejo(id);
        assertEquals("AZUL", consejo.jugador());
        assertNotNull(consejo.plan());
    }

    @Test
    void escenarioAleatorioConSemillaEsReproducible() {
        Ordenamiento ord = new Ordenamiento();
        Mapa mapa = Mapa.construir(MapaInicial.TERRITORIOS, MapaInicial.RUTAS, MapaInicial.catalogo(),
                new CaminosMinimos(), ord);
        var a = servicio.escenario(mapa, true, 42L);
        var b = servicio.escenario(mapa, true, 42L);
        assertEquals(a, b);
    }
}
