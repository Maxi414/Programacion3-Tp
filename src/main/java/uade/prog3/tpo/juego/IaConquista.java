package uade.prog3.tpo.juego;

import uade.prog3.tpo.algorithm.CaminosMinimos;
import uade.prog3.tpo.algorithm.HeuristicaGreedy;
import uade.prog3.tpo.algorithm.JuegoDeDosJugadores;
import uade.prog3.tpo.algorithm.Minimax;
import uade.prog3.tpo.algorithm.Ordenamiento;
import uade.prog3.tpo.juego.Jugadas.Abastecimiento;
import uade.prog3.tpo.juego.Jugadas.Compra;
import uade.prog3.tpo.juego.Jugadas.Movimiento;
import uade.prog3.tpo.juego.Jugadas.PlanDeTurno;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El juego de conquista visto como un juego de dos jugadores para MIN-MAX.
 *
 * Las tres piezas (clase 11):
 *   1. GENERADOR DE MOVIMIENTOS: jugadas() arma los planes de turno posibles.
 *      Un plan = comprar lo que dice la mochila + (marcha opcional) + (ataque
 *      opcional). 1 ply = el turno completo de un jugador.
 *   2. TEST DE TERMINAL: alguien tomo la capital rival o se llego al limite de
 *      rondas.
 *   3. EVALUACION: produccion neta, tropas, oro, territorios, aislados y
 *      peligro sobre la capital, usando la matriz de Floyd precalculada.
 *
 * MAX es 'jugadorMax' (la maquina cuando juega ella; el humano cuando pide
 * consejo). MIN es su rival.
 *
 * El generador NO devuelve todos los turnos posibles (comprar cualquier
 * subconjunto, marchar cualquier cantidad por cualquier camino, atacar con
 * cualquier cantidad): eso explota el factor de ramificacion. Devuelve los
 * planes "razonables": ataques que ganan, reforzar los frentes mas
 * amenazados y pasar. Es la decision de diseno que hace que el arbol entre en
 * memoria y alfa-beta llegue a profundidad 4.
 */
public final class IaConquista implements JuegoDeDosJugadores<Estado, PlanDeTurno> {

    /** Ancho maximo de ataques por estado: limita la ramificacion. */
    static final int MAX_ATAQUES = 12;
    /** Frentes amenazados que se consideran para planes defensivos. */
    static final int MAX_DEFENSIVOS = 2;

    // pesos de la funcion de evaluacion
    static final double PESO_INGRESO = 4.0;
    static final double PESO_TROPAS = 1.0;
    static final double PESO_ORO = 0.6;
    static final double PESO_TERRITORIO = 1.5;
    static final double PESO_AISLADO = 2.0;
    static final double PESO_PELIGRO = 0.5;

    private final Mapa mapa;
    private final MotorDeJuego motor;
    private final CaminosMinimos caminos;
    private final HeuristicaGreedy greedy;
    private final Ordenamiento ordenamiento;
    private final int jugadorMax;

    public IaConquista(Mapa mapa, MotorDeJuego motor, CaminosMinimos caminos, HeuristicaGreedy greedy,
                       Ordenamiento ordenamiento, int jugadorMax) {
        this.mapa = mapa;
        this.motor = motor;
        this.caminos = caminos;
        this.greedy = greedy;
        this.ordenamiento = ordenamiento;
        this.jugadorMax = jugadorMax;
    }

    /** Candidato de ataque con lo que necesita la heuristica greedy. */
    private record CandidatoAtaque(PlanDeTurno plan, double ganancia, double riesgo) { }

    // ------------------------------------------------------------------
    // 1. Generador de movimientos
    // ------------------------------------------------------------------

    @Override
    public List<PlanDeTurno> jugadas(Estado e) {
        if (e.terminada()) {
            return List.of();
        }
        int p = e.jugadorActual();
        int rival = Estado.rival(p);
        int cap = e.capital(p);
        Abastecimiento ab = motor.abastecimiento(mapa, e, p);

        // fase 2: la compra optima (busqueda binaria + mochila) es la misma en
        // todos los planes; lo que cambia es DONDE se ubica
        List<Item> compra = motor.compraOptima(mapa, e.oro(p)).elegidos();
        int poder = 0;
        for (Item u : compra) {
            poder += (int) u.getValor();
        }

        Grafo propio = motor.subgrafoPropio(mapa, e, p);
        Map<Integer, Map<String, Double>> distanciasPropias = new HashMap<>();

        // ataques que ganan, con o sin una marcha previa de refuerzo
        List<CandidatoAtaque> ataques = new ArrayList<>();
        for (int s = 0; s < mapa.cantidad(); s++) {
            if (e.duenio(s) != p) {
                continue;
            }
            int rally = ab.abastecido(s) ? s : cap; // donde se ubica lo comprado
            for (Grafo.Arista a : mapa.grafo().vecinos(s)) {
                int t = a.destino();
                if (e.duenio(t) == p) {
                    continue;
                }
                int def = motor.defensa(mapa, e, t);
                int propias = e.tropas(s) - 1 + (rally == s ? poder : 0);
                Movimiento marcha = mejorMarchaHacia(e, p, s, propio, distanciasPropias, rally, poder);
                // variante 1: atacar solo con lo que hay en s (si alcanza)
                if (propias > def) {
                    ataques.add(candidato(e, p, rival, s, t, def, propias, null, compra, rally));
                }
                // variante 2: traer refuerzos antes de atacar (si hay de donde traerlos y
                // con eso se gana): entra mas fuerte y el territorio conquistado queda mejor defendido
                if (marcha != null && propias + marcha.tropas() > def) {
                    ataques.add(candidato(e, p, rival, s, t, def, propias + marcha.tropas(), marcha, compra, rally));
                }
            }
        }
        List<CandidatoAtaque> rankeados = greedy.rankingPorRatio(ataques,
                CandidatoAtaque::ganancia, CandidatoAtaque::riesgo);

        List<PlanDeTurno> planes = new ArrayList<>();
        for (int i = 0; i < rankeados.size() && i < MAX_ATAQUES; i++) {
            planes.add(rankeados.get(i).plan());
        }

        // planes defensivos: reforzar los frentes mas amenazados por el rival
        List<Integer> frentes = new ArrayList<>();
        for (int f : motor.frontera(mapa, e, p, true)) {
            if (ab.abastecido(f)) {
                frentes.add(f);
            }
        }
        frentes = ordenamiento.mergeSort(frentes, (x, y) -> Integer.compare(
                motor.amenazaDirecta(mapa, e, y, p) - motor.defensa(mapa, e, y),
                motor.amenazaDirecta(mapa, e, x, p) - motor.defensa(mapa, e, x)));
        for (int i = 0; i < frentes.size() && i < MAX_DEFENSIVOS; i++) {
            int f = frentes.get(i);
            Movimiento marcha = mejorMarchaHacia(e, p, f, propio, distanciasPropias, f, poder);
            String desc = "refuerza " + mapa.id(f)
                    + (marcha == null ? "" : " (marcha " + marcha.tropas() + " desde " + marcha.origen() + ")");
            planes.add(new PlanDeTurno(compras(compra, f), marcha, null, desc, 0.0));
        }

        // plan pasivo: comprar en la capital y no hacer nada mas
        planes.add(new PlanDeTurno(compras(compra, cap), null, null, "fortifica la capital", -1.0));
        return planes;
    }

    /** Arma el plan de ataque y le calcula ganancia y riesgo para el ranking greedy. */
    private CandidatoAtaque candidato(Estado e, int p, int rival, int s, int t, int def, int enviadas,
                                      Movimiento marcha, List<Item> compra, int rally) {
        boolean esCapitalRival = e.duenio(t) == rival && e.capital(rival) == t;
        double ganancia = 2.0 * mapa.territorio(t).produccion()
                + (e.duenio(t) == rival ? 3.0 : 0.0)
                + (esCapitalRival ? 1000.0 : 0.0);
        int quedan = enviadas - def;
        double riesgo = def + 1 + Math.max(0, motor.amenazaDirecta(mapa, e, t, p) - quedan);
        String desc = (marcha == null ? "" : "marcha " + marcha.tropas() + " de " + marcha.origen()
                + " a " + marcha.destino() + ", ") + "ataca " + mapa.id(t) + " desde " + mapa.id(s)
                + " con " + enviadas;
        PlanDeTurno plan = new PlanDeTurno(compras(compra, rally), marcha,
                new Movimiento(mapa.id(s), mapa.id(t), enviadas), desc, greedy.ratio(ganancia, riesgo));
        return new CandidatoAtaque(plan, ganancia, riesgo);
    }

    private List<Compra> compras(List<Item> unidades, int destino) {
        List<Compra> salida = new ArrayList<>(unidades.size());
        for (Item u : unidades) {
            salida.add(new Compra(u.getId(), mapa.id(destino)));
        }
        return List.copyOf(salida);
    }

    /**
     * La mejor marcha hacia 'destino': desde el territorio propio con mas
     * tropas SOBRANTES (las que no hacen falta para frenar la amenaza directa
     * que tiene encima) que este a <= PUNTOS_DE_MARCHA dias por territorio
     * propio. Las distancias salen de Dijkstra sobre el subgrafo propio.
     */
    private Movimiento mejorMarchaHacia(Estado e, int p, int destino, Grafo propio,
                                        Map<Integer, Map<String, Double>> cache, int rally, int poder) {
        Map<String, Double> dist = cache.computeIfAbsent(destino,
                d -> caminos.distanciasDesde(propio, mapa.id(d)));
        int mejor = -1;
        int mejorSobrante = 0;
        for (Map.Entry<String, Double> en : dist.entrySet()) {
            int m = mapa.indice(en.getKey());
            if (m == destino || en.getValue() > MotorDeJuego.PUNTOS_DE_MARCHA) {
                continue;
            }
            int sobrante = e.tropas(m) - 1 - motor.amenazaDirecta(mapa, e, m, p) + (m == rally ? poder : 0);
            if (sobrante > mejorSobrante) {
                mejorSobrante = sobrante;
                mejor = m;
            }
        }
        return mejor == -1 ? null : new Movimiento(mapa.id(mejor), mapa.id(destino), mejorSobrante);
    }

    @Override
    public Estado aplicar(Estado estado, PlanDeTurno plan) {
        Estado hijo = estado.copia();          // el padre queda intacto: no hace falta retroceso
        motor.aplicarPlan(mapa, hijo, plan);
        if (!hijo.terminada()) {
            motor.terminarTurno(mapa, hijo);   // el turno pasa al rival y cobra su ingreso
        }
        return hijo;
    }

    // ------------------------------------------------------------------
    // 2. Test de terminal
    // ------------------------------------------------------------------

    @Override
    public boolean esTerminal(Estado e) {
        return e.terminada();
    }

    @Override
    public boolean turnoDeMax(Estado e) {
        return e.jugadorActual() == jugadorMax;
    }

    // ------------------------------------------------------------------
    // 3. Funcion de evaluacion
    // ------------------------------------------------------------------

    /**
     * Desde el punto de vista de MAX: valor(MAX) - valor(MIN). Suma cero, asi
     * que alcanza con una sola funcion.
     */
    @Override
    public double evaluar(Estado e) {
        int rival = Estado.rival(jugadorMax);
        if (e.ganador() == jugadorMax) {
            return Minimax.VICTORIA;
        }
        if (e.ganador() == rival) {
            return -Minimax.VICTORIA;
        }
        if (e.ganador() == Estado.EMPATE) {
            return 0.0;
        }
        return valor(e, jugadorMax) - valor(e, rival);
    }

    private double valor(Estado e, int p) {
        Abastecimiento ab = motor.abastecimiento(mapa, e, p);
        int tropas = 0;
        int territorios = 0;
        for (int i = 0; i < mapa.cantidad(); i++) {
            if (e.duenio(i) == p) {
                tropas += e.tropas(i);
                territorios++;
            }
        }
        return PESO_INGRESO * ab.ingresoNeto()
                + PESO_TROPAS * tropas
                + PESO_ORO * e.oro(p)
                + PESO_TERRITORIO * territorios
                - PESO_AISLADO * ab.aislados().size()
                - PESO_PELIGRO * peligroSobreCapital(e, p);
    }

    /**
     * Presion rival sobre la capital de 'p': ejercito rival cercano menos la
     * defensa de la capital (puede ser negativo: capital holgada). Usa la
     * matriz de Floyd: la distancia de cada territorio rival a la capital se
     * lee en O(1) (por eso se precalcula). Lo que esta a una marcha cuenta
     * entero; a dos marchas, la mitad; mas lejos, nada.
     *
     * Es un gradiente suave a proposito: como valor(MAX) - valor(MIN) resta la
     * presion propia y suma la del rival, acercar tropas a la capital enemiga
     * mejora la evaluacion de a poco, y la IA aprende a acumular antes de
     * golpear en vez de intercambiar el mismo territorio turno tras turno.
     */
    private double peligroSobreCapital(Estado e, int p) {
        int cap = e.capital(p);
        int rival = Estado.rival(p);
        double fuerza = 0.0;
        for (int t = 0; t < mapa.cantidad(); t++) {
            if (e.duenio(t) != rival) {
                continue;
            }
            double d = mapa.distancia(t, cap);
            if (d <= MotorDeJuego.PUNTOS_DE_MARCHA) {
                fuerza += e.tropas(t) - 1;
            } else if (d <= 2 * MotorDeJuego.PUNTOS_DE_MARCHA) {
                fuerza += (e.tropas(t) - 1) / 2.0;
            }
        }
        return fuerza - motor.defensa(mapa, e, cap);
    }
}
