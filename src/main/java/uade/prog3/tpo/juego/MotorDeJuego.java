package uade.prog3.tpo.juego;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.algorithm.ArbolRecubrimiento;
import uade.prog3.tpo.algorithm.BusquedaBinaria;
import uade.prog3.tpo.algorithm.CaminosMinimos;
import uade.prog3.tpo.algorithm.GeneradorDeEscenario;
import uade.prog3.tpo.algorithm.Recorridos;
import uade.prog3.tpo.algorithm.Seleccion;
import uade.prog3.tpo.juego.Jugadas.Abastecimiento;
import uade.prog3.tpo.juego.Jugadas.Compra;
import uade.prog3.tpo.juego.Jugadas.Movimiento;
import uade.prog3.tpo.juego.Jugadas.PlanDeTurno;
import uade.prog3.tpo.juego.Jugadas.ResultadoAtaque;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Las REGLAS del juego. No guarda estado: cada metodo recibe el Mapa
 * (estatico) y el Estado (dinamico) y lo modifica o lo consulta.
 *
 * Lo usan igual el humano (via JuegoService) y la IA (via IaConquista, que lo
 * llama sobre copias del estado para simular jugadas). Asi la IA juega
 * exactamente con las mismas reglas que el jugador.
 *
 * Cada fase del turno delega en un algoritmo de la materia:
 *   1. cobro          -> DFS (componentes) + Prim (red de abastecimiento)
 *   2. reclutamiento  -> busqueda binaria + mochila 0/1 con PD (sugerencia)
 *   3. marcha         -> Dijkstra sobre el territorio propio
 *   4. ataque         -> combate determinista
 */
@Component
public class MotorDeJuego {

    /** Dias de marcha que puede recorrer una marcha en un turno. */
    public static final int PUNTOS_DE_MARCHA = 8;
    /** El mantenimiento de la red de abastecimiento es costoMST / 4 (redondeado para abajo). */
    public static final int DIVISOR_MANTENIMIENTO = 4;
    /** Defensa extra de una capital. */
    public static final int BONUS_CAPITAL = 2;
    public static final int TROPAS_CAPITAL_INICIAL = 6;
    public static final int TROPAS_REGION_INICIAL = 3;
    /** Al terminar esta ronda sin que caiga ninguna capital, gana el de mayor puntaje. */
    public static final int LIMITE_RONDAS = 30;

    private final Recorridos recorridos;
    private final CaminosMinimos caminos;
    private final ArbolRecubrimiento arbol;
    private final Seleccion seleccion;
    private final BusquedaBinaria busquedaBinaria;

    public MotorDeJuego(Recorridos recorridos, CaminosMinimos caminos, ArbolRecubrimiento arbol,
                        Seleccion seleccion, BusquedaBinaria busquedaBinaria) {
        this.recorridos = recorridos;
        this.caminos = caminos;
        this.arbol = arbol;
        this.seleccion = seleccion;
        this.busquedaBinaria = busquedaBinaria;
    }

    // ------------------------------------------------------------------
    // Arranque
    // ------------------------------------------------------------------

    /**
     * Estado inicial a partir de un escenario (generado por backtracking).
     * Los neutrales arrancan con 1 + produccion tropas: las ciudades son las
     * mas defendidas. Ya se cobra el primer turno de AZUL.
     */
    public Estado estadoInicial(Mapa mapa, GeneradorDeEscenario.Escenario escenario) {
        Estado e = new Estado(mapa.cantidad());
        for (int i = 0; i < mapa.cantidad(); i++) {
            e.duenio[i] = Estado.NEUTRAL;
            e.tropas[i] = 1 + mapa.territorio(i).produccion();
        }
        ubicar(mapa, e, Estado.AZUL, escenario.regionAzul());
        ubicar(mapa, e, Estado.ROJO, escenario.regionRoja());
        e.capital[Estado.AZUL] = mapa.indice(escenario.capitalAzul());
        e.capital[Estado.ROJO] = mapa.indice(escenario.capitalRoja());
        e.tropas[e.capital[Estado.AZUL]] = TROPAS_CAPITAL_INICIAL;
        e.tropas[e.capital[Estado.ROJO]] = TROPAS_CAPITAL_INICIAL;
        e.jugadorActual = Estado.AZUL;
        e.ronda = 1;
        iniciarTurno(mapa, e);
        return e;
    }

    private void ubicar(Mapa mapa, Estado e, int jugador, List<String> region) {
        for (String id : region) {
            int i = mapa.indice(id);
            e.duenio[i] = jugador;
            e.tropas[i] = TROPAS_REGION_INICIAL;
        }
    }

    // ------------------------------------------------------------------
    // Fase 1: cobro con red de abastecimiento
    // ------------------------------------------------------------------

    /**
     * La red de abastecimiento de un jugador.
     *
     *  1. Se arma el subgrafo con SOLO sus territorios y las rutas entre ellos.
     *  2. DFS (componentes conexas): los territorios en la componente de la
     *     capital estan abastecidos; el resto quedo AISLADO (imperio partido).
     *  3. Prim desde la capital sobre la componente abastecida: el arbol de
     *     recubrimiento minimo es la red de caminos mas barata que conecta todo
     *     el imperio. Mantenerla cuesta costoMST / 4 de oro por turno.
     *
     * Por eso expandirse por pasos de montana caros sale caro: agranda el MST.
     * Y por eso cortar al rival por la mitad es tan fuerte: todo lo que queda
     * del otro lado deja de producir y de recibir refuerzos.
     *
     * Complejidad: O(V + E) del DFS + O(E log V) de Prim, sobre el subgrafo.
     */
    public Abastecimiento abastecimiento(Mapa mapa, Estado e, int jugador) {
        boolean[] mascara = new boolean[mapa.cantidad()];
        int cap = e.capital[jugador];
        List<String> aislados = new ArrayList<>();
        if (e.duenio[cap] != jugador) { // sin capital no hay red (la partida ya termino)
            for (int i = 0; i < mapa.cantidad(); i++) {
                if (e.duenio[i] == jugador) {
                    aislados.add(mapa.id(i));
                }
            }
            return new Abastecimiento(jugador, List.of(), aislados, 0, List.of(), 0, 0, 0, 0, mascara);
        }

        Grafo propio = subgrafoPropio(mapa, e, jugador);
        List<List<String>> componentes = recorridos.componentesConexas(propio);
        String idCapital = mapa.id(cap);
        List<String> abastecidos = List.of();
        for (List<String> comp : componentes) {
            if (comp.contains(idCapital)) {
                abastecidos = comp;
            }
        }
        Set<String> enRed = new HashSet<>(abastecidos);
        int produccion = 0;
        for (int i = 0; i < mapa.cantidad(); i++) {
            if (e.duenio[i] != jugador) {
                continue;
            }
            if (enRed.contains(mapa.id(i))) {
                mascara[i] = true;
                produccion += mapa.territorio(i).produccion();
            } else {
                aislados.add(mapa.id(i));
            }
        }

        // red de abastecimiento: MST con Prim desde la capital, solo sobre lo abastecido
        Grafo red = new Grafo(false);
        for (String id : abastecidos) {
            red.agregarVertice(id);
        }
        for (Mapa.Ruta r : mapa.rutas()) {
            if (enRed.contains(r.origen()) && enRed.contains(r.destino())) {
                red.agregarArista(r.origen(), r.destino(), r.costo());
            }
        }
        ArbolRecubrimiento.Mst mst = arbol.prim(red, idCapital);
        int mantenimiento = (int) (mst.costoTotal() / DIVISOR_MANTENIMIENTO);
        return new Abastecimiento(jugador, abastecidos, aislados, componentes.size(), mst.aristas(),
                mst.costoTotal(), produccion, mantenimiento, produccion - mantenimiento, mascara);
    }

    /** Subgrafo NO dirigido con los territorios de un jugador y las rutas entre ellos. O(V + E). */
    public Grafo subgrafoPropio(Mapa mapa, Estado e, int jugador) {
        Grafo g = new Grafo(false);
        for (int i = 0; i < mapa.cantidad(); i++) {
            if (e.duenio[i] == jugador) {
                g.agregarVertice(mapa.id(i));
            }
        }
        for (Mapa.Ruta r : mapa.rutas()) {
            if (e.duenio[mapa.indice(r.origen())] == jugador && e.duenio[mapa.indice(r.destino())] == jugador) {
                g.agregarArista(r.origen(), r.destino(), r.costo());
            }
        }
        return g;
    }

    /** Fase 1: se cobra el ingreso neto al empezar el turno. El oro nunca baja de 0. */
    public Abastecimiento iniciarTurno(Mapa mapa, Estado e) {
        int j = e.jugadorActual;
        Abastecimiento ab = abastecimiento(mapa, e, j);
        e.oro[j] = Math.max(0, e.oro[j] + ab.ingresoNeto());
        e.fase = Fase.RECLUTAMIENTO;
        return ab;
    }

    // ------------------------------------------------------------------
    // Fase 2: reclutamiento
    // ------------------------------------------------------------------

    /**
     * Compra OPTIMA para un presupuesto: busqueda binaria para quedarse con las
     * unidades que se pueden pagar + mochila 0/1 con PD sobre esas.
     * O(log n) + O(n * oro).
     */
    public Seleccion.Resultado compraOptima(Mapa mapa, int oro) {
        List<Item> asequibles = busquedaBinaria.asequibles(mapa.catalogo(), oro);
        return seleccion.programacionDinamica(asequibles, oro);
    }

    /** La misma compra resuelta con greedy por ratio poder/costo (para comparar). */
    public Seleccion.Resultado compraGreedy(Mapa mapa, int oro) {
        List<Item> asequibles = busquedaBinaria.asequibles(mapa.catalogo(), oro);
        return seleccion.greedy(asequibles, oro);
    }

    /**
     * Fase 2. Cada unidad se compra a lo sumo una vez por turno (0/1) y se
     * ubica en un territorio propio ABASTECIDO (los aislados no reciben
     * refuerzos). Una lista vacia no hace nada.
     */
    public List<Item> comprar(Mapa mapa, Estado e, List<Compra> compras) {
        validarEnJuego(e);
        if (e.fase != Fase.RECLUTAMIENTO) {
            throw new IllegalStateException("Ya pasó la fase de reclutamiento de este turno.");
        }
        if (compras.isEmpty()) {
            return List.of();
        }
        int j = e.jugadorActual;
        Abastecimiento ab = abastecimiento(mapa, e, j);
        Set<String> vistas = new HashSet<>();
        int costo = 0;
        List<Item> compradas = new ArrayList<>();
        for (Compra c : compras) {
            Item u = mapa.unidad(c.unidad());
            if (!vistas.add(u.getId())) {
                throw new IllegalArgumentException("El mercado ofrece una sola unidad de cada tipo por turno: "
                        + u.getNombre() + " está repetida.");
            }
            int d = mapa.indice(c.destino());
            if (e.duenio[d] != j) {
                throw new IllegalArgumentException(mapa.territorio(d).nombre() + " no es tuyo.");
            }
            if (!ab.abastecido(d)) {
                throw new IllegalArgumentException(mapa.territorio(d).nombre()
                        + " está aislado de la capital: no puede recibir refuerzos.");
            }
            costo += (int) u.getPeso();
            compradas.add(u);
        }
        if (costo > e.oro[j]) {
            throw new IllegalArgumentException("La compra cuesta " + costo + " de oro y hay " + e.oro[j] + ".");
        }
        e.oro[j] -= costo;
        for (Compra c : compras) {
            e.tropas[mapa.indice(c.destino())] += (int) mapa.unidad(c.unidad()).getValor();
        }
        e.fase = Fase.MARCHA;
        return compradas;
    }

    // ------------------------------------------------------------------
    // Fase 3: marcha
    // ------------------------------------------------------------------

    /**
     * Camino de marcha: Dijkstra sobre el subgrafo de territorios PROPIOS (no se
     * puede marchar a traves de territorio enemigo ni neutral).
     * Lanza IllegalStateException si no hay camino por territorio propio.
     */
    public CaminosMinimos.Camino rutaDeMarcha(Mapa mapa, Estado e, String origen, String destino) {
        int j = e.jugadorActual;
        int o = mapa.indice(origen);
        int d = mapa.indice(destino);
        if (e.duenio[o] != j || e.duenio[d] != j) {
            throw new IllegalArgumentException("Solo se puede marchar entre territorios propios.");
        }
        return caminos.dijkstra(subgrafoPropio(mapa, e, j), origen, destino);
    }

    /** Fase 3: mover tropas si el camino mas barato entra en los puntos de marcha del turno. */
    public CaminosMinimos.Camino marchar(Mapa mapa, Estado e, Movimiento m) {
        validarEnJuego(e);
        if (e.fase.ordinal() > Fase.MARCHA.ordinal()) {
            throw new IllegalStateException("Ya pasó la fase de marcha de este turno.");
        }
        int o = mapa.indice(m.origen());
        int d = mapa.indice(m.destino());
        if (o == d) {
            throw new IllegalArgumentException("El origen y el destino de la marcha son el mismo territorio.");
        }
        validarTropas(e, o, m.tropas());
        CaminosMinimos.Camino camino = rutaDeMarcha(mapa, e, m.origen(), m.destino());
        if (camino.costoTotal() > PUNTOS_DE_MARCHA) {
            throw new IllegalArgumentException("La marcha " + m.origen() + "->" + m.destino() + " cuesta "
                    + (int) camino.costoTotal() + " días y el límite por turno es " + PUNTOS_DE_MARCHA + ".");
        }
        e.tropas[o] -= m.tropas();
        e.tropas[d] += m.tropas();
        e.fase = Fase.ATAQUE;
        return camino;
    }

    // ------------------------------------------------------------------
    // Fase 4: ataque
    // ------------------------------------------------------------------

    /** Defensa efectiva: tropas + bonus de terreno + bonus de capital. */
    public int defensa(Mapa mapa, Estado e, int t) {
        int dueno = e.duenio[t];
        boolean esCapital = dueno != Estado.NEUTRAL && e.capital[dueno] == t;
        return e.tropas[t] + mapa.territorio(t).terreno().bonusDefensa() + (esCapital ? BONUS_CAPITAL : 0);
    }

    /**
     * Fase 4, combate DETERMINISTA (sin dados, para que el minimax aplique):
     *   - si enviadas > defensa: se conquista y quedan enviadas - defensa tropas
     *   - si no: el ataque fracasa, se pierden las enviadas y el defensor
     *     pierde la mitad de esa cantidad (pero conserva al menos 1)
     * Tomar la capital rival gana la partida.
     */
    public ResultadoAtaque atacar(Mapa mapa, Estado e, Movimiento m) {
        validarEnJuego(e);
        if (e.fase == Fase.CIERRE) {
            throw new IllegalStateException("Ya atacaste este turno.");
        }
        int j = e.jugadorActual;
        int o = mapa.indice(m.origen());
        int d = mapa.indice(m.destino());
        if (e.duenio[d] == j) {
            throw new IllegalArgumentException(mapa.territorio(d).nombre() + " ya es tuyo.");
        }
        if (!mapa.sonAdyacentes(o, d)) {
            throw new IllegalArgumentException("Solo se puede atacar un territorio vecino: "
                    + m.origen() + " y " + m.destino() + " no están conectados por una ruta.");
        }
        validarTropas(e, o, m.tropas());

        int def = defensa(mapa, e, d);
        int defensor = e.duenio[d];
        boolean eraCapital = defensor != Estado.NEUTRAL && e.capital[defensor] == d;
        e.tropas[o] -= m.tropas();
        boolean conquistado = m.tropas() > def;
        if (conquistado) {
            e.duenio[d] = j;
            e.tropas[d] = m.tropas() - def;
            if (eraCapital) {
                e.ganador = j;
            }
        } else {
            e.tropas[d] = Math.max(1, e.tropas[d] - m.tropas() / 2);
        }
        e.fase = Fase.CIERRE;
        return new ResultadoAtaque(m.origen(), m.destino(), m.tropas(), def, conquistado,
                e.tropas[d], conquistado && eraCapital);
    }

    // ------------------------------------------------------------------
    // Fin de turno
    // ------------------------------------------------------------------

    /** Pasa el turno al rival y le cobra su ingreso. Controla el limite de rondas. */
    public void terminarTurno(Mapa mapa, Estado e) {
        validarEnJuego(e);
        if (e.jugadorActual == Estado.ROJO) {
            e.ronda++;
            if (e.ronda > LIMITE_RONDAS) {
                int azul = puntaje(mapa, e, Estado.AZUL);
                int rojo = puntaje(mapa, e, Estado.ROJO);
                e.ganador = azul > rojo ? Estado.AZUL : rojo > azul ? Estado.ROJO : Estado.EMPATE;
                return;
            }
        }
        e.jugadorActual = Estado.rival(e.jugadorActual);
        iniciarTurno(mapa, e);
    }

    /** Resultado de aplicar un plan completo (sin terminar el turno). */
    public record ResultadoPlan(List<Item> compradas, CaminosMinimos.Camino marcha, ResultadoAtaque ataque) { }

    /** Ejecuta las tres fases de un plan sobre el estado. No termina el turno. */
    public ResultadoPlan aplicarPlan(Mapa mapa, Estado e, PlanDeTurno plan) {
        List<Item> compradas = comprar(mapa, e, plan.compras());
        CaminosMinimos.Camino camino = plan.marcha() == null ? null : marchar(mapa, e, plan.marcha());
        ResultadoAtaque ataque = plan.ataque() == null ? null : atacar(mapa, e, plan.ataque());
        return new ResultadoPlan(compradas, camino, ataque);
    }

    // ------------------------------------------------------------------
    // Consultas que usan la IA y las sugerencias
    // ------------------------------------------------------------------

    /** Tropas rivales que pueden atacar 't' el proximo turno (vecinos del rival, menos 1 c/u). */
    public int amenazaDirecta(Mapa mapa, Estado e, int t, int jugador) {
        int rival = Estado.rival(jugador);
        int total = 0;
        for (Grafo.Arista a : mapa.grafo().vecinos(t)) {
            if (e.duenio[a.destino()] == rival) {
                total += Math.max(0, e.tropas[a.destino()] - 1);
            }
        }
        return total;
    }

    /** Territorios propios que lindan con alguien que no es el jugador (o solo con el rival). */
    public List<Integer> frontera(Mapa mapa, Estado e, int jugador, boolean soloContraRival) {
        List<Integer> salida = new ArrayList<>();
        int rival = Estado.rival(jugador);
        for (int i = 0; i < mapa.cantidad(); i++) {
            if (e.duenio[i] != jugador) {
                continue;
            }
            for (Grafo.Arista a : mapa.grafo().vecinos(i)) {
                int v = e.duenio[a.destino()];
                if (soloContraRival ? v == rival : v != jugador) {
                    salida.add(i);
                    break;
                }
            }
        }
        return salida;
    }

    /** Puntaje para el desempate por limite de rondas: 10 por cada punto de produccion + tropas. */
    public int puntaje(Mapa mapa, Estado e, int jugador) {
        int p = 0;
        for (int i = 0; i < mapa.cantidad(); i++) {
            if (e.duenio[i] == jugador) {
                p += 10 * mapa.territorio(i).produccion() + e.tropas[i];
            }
        }
        return p;
    }

    private void validarEnJuego(Estado e) {
        if (e.terminada()) {
            throw new IllegalStateException("La partida ya terminó: ganó " + Estado.nombreJugador(e.ganador) + ".");
        }
    }

    private void validarTropas(Estado e, int origen, int tropas) {
        if (e.duenio[origen] != e.jugadorActual) {
            throw new IllegalArgumentException("El territorio de origen no es tuyo.");
        }
        if (tropas < 1 || tropas > e.tropas[origen] - 1) {
            throw new IllegalArgumentException("Se pueden mover entre 1 y " + (e.tropas[origen] - 1)
                    + " tropas: siempre tiene que quedar al menos 1 en el territorio de origen.");
        }
    }
}
