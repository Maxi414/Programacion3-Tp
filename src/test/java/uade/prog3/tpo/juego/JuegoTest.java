package uade.prog3.tpo.juego;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uade.prog3.tpo.algorithm.*;
import uade.prog3.tpo.juego.Jugadas.Abastecimiento;
import uade.prog3.tpo.juego.Jugadas.Compra;
import uade.prog3.tpo.juego.Jugadas.Movimiento;
import uade.prog3.tpo.seed.MapaInicial;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reglas del juego e IA, sin Neo4j. Esta en el paquete 'juego' para poder
 * armar estados a mano (los campos de Estado son package-private).
 */
class JuegoTest {

    static final Ordenamiento ORD = new Ordenamiento();
    static final CaminosMinimos CAMINOS = new CaminosMinimos();
    static final MotorDeJuego MOTOR = new MotorDeJuego(new Recorridos(), CAMINOS, new ArbolRecubrimiento(),
            new Seleccion(ORD), new BusquedaBinaria());
    static final Estratega ESTRATEGA = new Estratega(MOTOR, CAMINOS, new HeuristicaGreedy(ORD), ORD, new Minimax());
    static final Mapa MAPA = Mapa.construir(MapaInicial.TERRITORIOS, MapaInicial.RUTAS, MapaInicial.catalogo(),
            CAMINOS, ORD);

    /** El escenario clasico: AZU+PIN+VAD contra BRA+DOR+SEL. */
    static Estado clasico() {
        int n = MAPA.cantidad();
        int[] prod = new int[n];
        boolean[] cap = new boolean[n];
        for (int i = 0; i < n; i++) {
            prod[i] = MAPA.territorio(i).produccion();
            cap[i] = MAPA.territorio(i).terreno().puedeSerCapital();
        }
        var esc = new GeneradorDeEscenario(ORD).generar(MAPA.grafo(), prod, cap, MAPA.matrizDeDistancias(),
                3, 10, 0, null);
        return MOTOR.estadoInicial(MAPA, esc);
    }

    static int i(String id) {
        return MAPA.indice(id);
    }

    private Estado e;

    @BeforeEach
    void arrancar() {
        e = clasico();
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Arranque y fase 1: cobro con red de abastecimiento (DFS + Prim)")
    class Cobro {

        @Test
        void estadoInicialDelEscenarioClasico() {
            assertEquals(Estado.AZUL, e.jugadorActual());
            assertEquals(Fase.RECLUTAMIENTO, e.fase());
            assertEquals(6, e.tropas(i("AZU")));
            assertEquals(3, e.tropas(i("PIN")));
            assertEquals(Estado.ROJO, e.duenio(i("SEL")));
            assertEquals(Estado.NEUTRAL, e.duenio(i("CON")));
            assertEquals(2, e.tropas(i("CON")), "neutral: 1 + produccion");
            assertEquals(4, e.tropas(i("NIE")));
            assertEquals(6, e.oro(Estado.AZUL), "ya cobro el primer turno: 7 - 5/4");
            assertEquals(0, e.oro(Estado.ROJO), "ROJO todavia no cobro");
        }

        @Test
        void redDeAbastecimientoInicial() {
            Abastecimiento ab = MOTOR.abastecimiento(MAPA, e, Estado.AZUL);
            assertEquals(List.of("AZU", "PIN", "VAD"), ab.abastecidos());
            assertTrue(ab.aislados().isEmpty());
            assertEquals(1, ab.componentes());
            assertEquals(5.0, ab.costoRed(), 1e-9, "AZU-VAD (2) + AZU-PIN (3)");
            assertEquals(2, ab.red().size());
            assertEquals(7, ab.produccion());
            assertEquals(1, ab.mantenimiento());
            assertEquals(6, ab.ingresoNeto());
        }

        @Test
        void unImperioPartidoNoCobraLoAislado() {
            e.duenio[i("EBA")] = Estado.AZUL; // EBA no toca nada azul
            Abastecimiento ab = MOTOR.abastecimiento(MAPA, e, Estado.AZUL);
            assertEquals(2, ab.componentes(), "DFS encuentra dos componentes");
            assertEquals(List.of("EBA"), ab.aislados());
            assertEquals(7, ab.produccion(), "EBA (produccion 2) no cuenta");
            assertThrows(IllegalArgumentException.class,
                    () -> MOTOR.comprar(MAPA, e, List.of(new Compra("U1", "EBA"))), "lo aislado no recibe refuerzos");
        }

        @Test
        void expandirsePorRutasCarasSubeElMantenimiento() {
            e.duenio[i("CON")] = Estado.AZUL;
            e.duenio[i("NIE")] = Estado.AZUL;
            e.duenio[i("EBA")] = Estado.AZUL;
            // MST: AZU-VAD 2, AZU-PIN 3, NIE-PIN 3, CON-EBA 3, CON-VAD 4 = 15 (EBA-NIE 4 queda afuera por ciclo)
            Abastecimiento ab = MOTOR.abastecimiento(MAPA, e, Estado.AZUL);
            assertEquals(15.0, ab.costoRed(), 1e-9);
            assertEquals(3, ab.mantenimiento());
            assertEquals(13 - 3, ab.ingresoNeto());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Fases 2 a 4: compra, marcha y ataque")
    class Acciones {

        @Test
        void comprarUbicaElPoderYCobra() {
            MOTOR.comprar(MAPA, e, List.of(new Compra("U5", "VAD")));
            assertEquals(12, e.tropas(i("VAD")));
            assertEquals(0, e.oro(Estado.AZUL));
            assertEquals(Fase.MARCHA, e.fase());
        }

        @Test
        void comprasInvalidas() {
            assertThrows(IllegalArgumentException.class, () -> MOTOR.comprar(MAPA, e,
                    List.of(new Compra("U1", "AZU"), new Compra("U1", "PIN"))), "una de cada tipo");
            assertThrows(IllegalArgumentException.class, () -> MOTOR.comprar(MAPA, e,
                    List.of(new Compra("U6", "AZU"))), "cuesta 8 y hay 6");
            assertThrows(IllegalArgumentException.class, () -> MOTOR.comprar(MAPA, e,
                    List.of(new Compra("U1", "CON"))), "CON no es de AZUL");
        }

        @Test
        void compraOptimaYGreedyDelPrimerTurno() {
            assertEquals(9.0, MOTOR.compraOptima(MAPA, 6).valorTotal(), 1e-9);
            assertEquals(6.0, MOTOR.compraGreedy(MAPA, 6).valorTotal(), 1e-9);
        }

        @Test
        void marcharUsaDijkstraPorTerritorioPropio() {
            var c = MOTOR.marchar(MAPA, e, new Movimiento("PIN", "VAD", 2));
            assertEquals(List.of("PIN", "AZU", "VAD"), c.vertices());
            assertEquals(5.0, c.costoTotal(), 1e-9);
            assertEquals(1, e.tropas(i("PIN")));
            assertEquals(5, e.tropas(i("VAD")));
            assertEquals(Fase.ATAQUE, e.fase());
            assertThrows(IllegalStateException.class,
                    () -> MOTOR.comprar(MAPA, e, List.of(new Compra("U1", "AZU"))), "ya paso la compra");
        }

        @Test
        void noSeMarchaPorTerritorioAjenoNiMasAllaDeLosPuntosDeMarcha() {
            e.duenio[i("NIE")] = Estado.AZUL;
            e.duenio[i("HEL")] = Estado.AZUL;
            e.tropas[i("AZU")] = 10;
            // AZU -> HEL por propio: AZU-PIN-NIE-HEL = 3+3+5 = 11 dias > 8
            assertThrows(IllegalArgumentException.class,
                    () -> MOTOR.marchar(MAPA, e, new Movimiento("AZU", "HEL", 3)));
            e.duenio[i("EBA")] = Estado.AZUL;
            e.duenio[i("PIN")] = Estado.NEUTRAL; // corta el camino
            assertThrows(IllegalStateException.class,
                    () -> MOTOR.marchar(MAPA, e, new Movimiento("AZU", "NIE", 3)), "no hay camino propio");
        }

        @Test
        void conquistarUnNeutral() {
            MOTOR.comprar(MAPA, e, List.of(new Compra("U5", "VAD")));
            var r = MOTOR.atacar(MAPA, e, new Movimiento("VAD", "CON", 11));
            assertEquals(4, r.defensa(), "2 tropas + 2 de montana");
            assertTrue(r.conquistado());
            assertEquals(Estado.AZUL, e.duenio(i("CON")));
            assertEquals(7, e.tropas(i("CON")));
            assertEquals(1, e.tropas(i("VAD")));
            assertEquals(Fase.CIERRE, e.fase());
            assertThrows(IllegalStateException.class,
                    () -> MOTOR.atacar(MAPA, e, new Movimiento("AZU", "PIN", 1)), "un ataque por turno");
        }

        @Test
        void ataqueRechazadoYAtaquesInvalidos() {
            var r = MOTOR.atacar(MAPA, e, new Movimiento("PIN", "NIE", 2));
            assertFalse(r.conquistado());
            assertEquals(5, r.defensa(), "4 tropas + 1 de ciudad");
            assertEquals(3, e.tropas(i("NIE")), "pierde 2/2 = 1");
            assertEquals(1, e.tropas(i("PIN")));

            Estado otro = clasico();
            assertThrows(IllegalArgumentException.class,
                    () -> MOTOR.atacar(MAPA, otro, new Movimiento("AZU", "CON", 2)), "no son vecinos");
            assertThrows(IllegalArgumentException.class,
                    () -> MOTOR.atacar(MAPA, otro, new Movimiento("AZU", "PIN", 2)), "PIN ya es propio");
            assertThrows(IllegalArgumentException.class,
                    () -> MOTOR.atacar(MAPA, otro, new Movimiento("VAD", "CON", 3)), "tiene que quedar 1");
        }

        @Test
        void tomarLaCapitalRivalGanaLaPartida() {
            e.duenio[i("DOR")] = Estado.AZUL;
            e.tropas[i("DOR")] = 20;
            assertEquals(9, MOTOR.defensa(MAPA, e, i("BRA")), "6 tropas + 1 de ciudad + 2 de capital");
            var r = MOTOR.atacar(MAPA, e, new Movimiento("DOR", "BRA", 19));
            assertTrue(r.capitalCapturada());
            assertEquals(Estado.AZUL, e.ganador());
            assertThrows(IllegalStateException.class, () -> MOTOR.terminarTurno(MAPA, e));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Turnos y fin de partida")
    class Turnos {

        @Test
        void terminarTurnoPasaAlRivalYLeCobra() {
            MOTOR.terminarTurno(MAPA, e);
            assertEquals(Estado.ROJO, e.jugadorActual());
            assertEquals(6, e.oro(Estado.ROJO));
            assertEquals(1, e.ronda());
            MOTOR.terminarTurno(MAPA, e);
            assertEquals(Estado.AZUL, e.jugadorActual());
            assertEquals(2, e.ronda());
            assertEquals(12, e.oro(Estado.AZUL), "6 que no gasto + 6 de ingreso");
        }

        @Test
        void alLimiteDeRondasGanaElDeMayorPuntaje() {
            e.ronda = MotorDeJuego.LIMITE_RONDAS;
            e.jugadorActual = Estado.ROJO;
            e.tropas[i("BRA")] = 30;
            MOTOR.terminarTurno(MAPA, e);
            assertEquals(Estado.ROJO, e.ganador());
        }

        @Test
        void copiarElEstadoNoComparteArreglos() {
            Estado copia = e.copia();
            copia.tropas[i("AZU")] = 99;
            copia.oro[Estado.AZUL] = 99;
            assertEquals(6, e.tropas(i("AZU")));
            assertEquals(6, e.oro(Estado.AZUL));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("IA: MIN-MAX, alfa-beta y greedy jugando de verdad")
    class Ia {

        @Test
        void elGeneradorDeJugadasDevuelvePlanesValidos() {
            IaConquista ia = ESTRATEGA.ia(MAPA, Estado.AZUL);
            List<Jugadas.PlanDeTurno> planes = ia.jugadas(e);
            assertFalse(planes.isEmpty());
            for (Jugadas.PlanDeTurno plan : planes) {
                Estado hijo = ia.aplicar(e, plan); // si el plan no fuera legal, el motor lanzaria excepcion
                assertEquals(Estado.ROJO, hijo.jugadorActual());
            }
            assertEquals(6, e.oro(Estado.AZUL), "aplicar no toca el estado padre");
        }

        @Test
        void siPuedeTomarLaCapitalLaToma() {
            e.duenio[i("DOR")] = Estado.AZUL;
            e.tropas[i("DOR")] = 20;
            for (Dificultad d : Dificultad.values()) {
                var r = ESTRATEGA.decidir(MAPA, e, d);
                assertEquals("BRA", r.plan().ataque().destino(), "dificultad " + d);
            }
            var r = ESTRATEGA.decidir(MAPA, e, Dificultad.DIFICIL);
            assertEquals(Minimax.VICTORIA - 1, r.valor(), 1e-9, "victoria en la primera jugada");
        }

        @Test
        void minimaxYAlfaBetaDanElMismoValor() {
            for (int prof = 1; prof <= 3; prof++) {
                var c = ESTRATEGA.comparar(MAPA, e, prof);
                assertTrue(c.mismoValor(), "profundidad " + prof);
                assertTrue(c.nodosAlfaBeta() <= c.nodosMinimax());
            }
            var c3 = ESTRATEGA.comparar(MAPA, e, 3);
            assertTrue(c3.nodosAlfaBeta() < c3.nodosMinimax(), "a profundidad 3 ya poda");
            assertTrue(c3.cortesAlfaBeta() > 0);
        }

        @Test
        void laIaDificilLeGanaALaFacil() {
            Estado partida = clasico();
            while (!partida.terminada()) {
                Dificultad d = partida.jugadorActual() == Estado.AZUL ? Dificultad.DIFICIL : Dificultad.FACIL;
                var r = ESTRATEGA.decidir(MAPA, partida, d);
                MOTOR.aplicarPlan(MAPA, partida, r.plan());
                if (!partida.terminada()) {
                    MOTOR.terminarTurno(MAPA, partida);
                }
            }
            assertEquals(Estado.AZUL, partida.ganador());
            assertTrue(partida.ronda() < MotorDeJuego.LIMITE_RONDAS, "gana por capital, no por puntaje");
        }

        @Test
        void elAnalisisSePideAlPrincipioDelTurno() {
            MOTOR.comprar(MAPA, e, List.of(new Compra("U1", "AZU")));
            assertThrows(IllegalStateException.class, () -> ESTRATEGA.decidir(MAPA, e, Dificultad.NORMAL));
        }
    }
}
