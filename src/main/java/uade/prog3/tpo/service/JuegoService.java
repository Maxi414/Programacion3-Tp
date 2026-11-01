package uade.prog3.tpo.service;

import org.springframework.stereotype.Service;
import uade.prog3.tpo.algorithm.ArbolAVL;
import uade.prog3.tpo.algorithm.ArbolRecubrimiento;
import uade.prog3.tpo.algorithm.Backtracking;
import uade.prog3.tpo.algorithm.BusquedaBinaria;
import uade.prog3.tpo.algorithm.CaminosMinimos;
import uade.prog3.tpo.algorithm.GeneradorDeEscenario;
import uade.prog3.tpo.algorithm.HeuristicaGreedy;
import uade.prog3.tpo.algorithm.Huffman;
import uade.prog3.tpo.algorithm.Ordenamiento;
import uade.prog3.tpo.algorithm.RamificacionYPoda;
import uade.prog3.tpo.algorithm.Recorridos;
import uade.prog3.tpo.algorithm.Seleccion;
import uade.prog3.tpo.juego.Dificultad;
import uade.prog3.tpo.juego.Estado;
import uade.prog3.tpo.juego.Estratega;
import uade.prog3.tpo.juego.Jugadas.Abastecimiento;
import uade.prog3.tpo.juego.Jugadas.Compra;
import uade.prog3.tpo.juego.Jugadas.Movimiento;
import uade.prog3.tpo.juego.Jugadas.ResultadoAtaque;
import uade.prog3.tpo.juego.Mapa;
import uade.prog3.tpo.juego.MotorDeJuego;
import uade.prog3.tpo.juego.Partida;
import uade.prog3.tpo.juego.Terreno;
import uade.prog3.tpo.model.Conexion;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.model.Nodo;
import uade.prog3.tpo.repository.ItemRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Orquesta las partidas: las crea (leyendo el mapa de Neo4j UNA vez), aplica
 * las acciones del humano, juega el turno de la IA y responde las consultas
 * de ayuda, cada una resuelta por un algoritmo de la materia.
 *
 * No tiene logica algoritmica propia: arma los datos y delega en el paquete
 * algorithm (y en el motor de reglas del paquete juego).
 *
 * Las partidas viven en memoria. El humano juega con AZUL y la IA con ROJO.
 */
@Service
public class JuegoService {

    static final int TAMANIO_REGION_INICIAL = 3;
    static final double DISTANCIA_MINIMA_ENTRE_CAPITALES = 10;
    static final int MAX_PARTIDAS_EN_MEMORIA = 100;

    private final GrafoService grafoService;
    private final ItemRepository itemRepository;
    private final MotorDeJuego motor;
    private final Estratega estratega;
    private final GeneradorDeEscenario generador;
    private final Recorridos recorridos;
    private final CaminosMinimos caminos;
    private final ArbolRecubrimiento arbol;
    private final Backtracking backtracking;
    private final RamificacionYPoda ramificacionYPoda;
    private final Ordenamiento ordenamiento;
    private final HeuristicaGreedy greedy;
    private final Huffman huffman;
    private final BusquedaBinaria busquedaBinaria;

    private final Map<String, Partida> partidas = new ConcurrentHashMap<>();
    private final Queue<String> ordenDeCreacion = new ConcurrentLinkedQueue<>();

    public JuegoService(GrafoService grafoService, ItemRepository itemRepository, MotorDeJuego motor,
                        Estratega estratega, GeneradorDeEscenario generador, Recorridos recorridos,
                        CaminosMinimos caminos, ArbolRecubrimiento arbol, Backtracking backtracking,
                        RamificacionYPoda ramificacionYPoda, Ordenamiento ordenamiento,
                        HeuristicaGreedy greedy, Huffman huffman, BusquedaBinaria busquedaBinaria) {
        this.grafoService = grafoService;
        this.itemRepository = itemRepository;
        this.motor = motor;
        this.estratega = estratega;
        this.generador = generador;
        this.recorridos = recorridos;
        this.caminos = caminos;
        this.arbol = arbol;
        this.backtracking = backtracking;
        this.ramificacionYPoda = ramificacionYPoda;
        this.ordenamiento = ordenamiento;
        this.greedy = greedy;
        this.huffman = huffman;
        this.busquedaBinaria = busquedaBinaria;
    }

    // ==================================================================
    // Vistas (lo que se devuelve como JSON)
    // ==================================================================

    public record VistaTerritorio(String id, String nombre, String terreno, int produccion, double x, double y,
                                  String duenio, int tropas, int defensa, boolean esCapital, boolean abastecido) { }

    public record VistaJugador(String nombre, int oro, String capital, int territorios, int tropas,
                               int produccion, int mantenimiento, int ingresoNeto, double costoRed,
                               List<ArbolRecubrimiento.AristaMst> red, List<String> aislados, int componentes,
                               int puntaje) { }

    public record VistaPartida(String id, String dificultad, int ronda, int limiteRondas, String turno,
                               String fase, String ganador, int puntosDeMarcha,
                               List<VistaTerritorio> territorios, List<Mapa.Ruta> rutas,
                               Map<String, VistaJugador> jugadores, List<Item> catalogo,
                               List<String> eventos, Estratega.ReporteIa ultimaJugadaIa,
                               GeneradorDeEscenario.Escenario escenario) { }

    public record VistaMercado(String jugador, int oro, List<Item> catalogoPorCosto, int ultimoAsequible,
                               List<Item> asequibles, Seleccion.Resultado greedy, Seleccion.Resultado mochila,
                               double poderQuePierdeGreedy) { }

    public record VistaMarcha(List<String> vertices, double dias, int puntosDeMarcha, boolean alcanza) { }

    public record SugerenciaAtaque(String origen, String destino, String nombreDestino, int tropasDisponibles,
                                   int defensa, double ganancia, double riesgo, double ratio) { }

    public record VistaObjetivo(String id, String nombre, String duenio, int defensa, int produccion) { }

    public record VistaObjetivos(double defensaMinima, double defensaMaxima, int k, int territoriosEnElArbol,
                                 int alturaDelArbol, int rotaciones, List<VistaObjetivo> enRango,
                                 List<VistaObjetivo> masValiosos) { }

    public record VistaImperio(String jugador, List<List<String>> componentes, boolean partido,
                               List<String> abastecidos, List<String> aislados,
                               ArbolRecubrimiento.Mst prim, ArbolRecubrimiento.Mst kruskal,
                               boolean mismoCosto, int mantenimiento) { }

    public record VistaReparto(List<Item> unidades, List<RamificacionYPoda.Frente> frentes,
                               RamificacionYPoda.Reparto reparto, List<Compra> comoCompra) { }

    public record VistaRanking(String id, String nombre, String duenio, double puntaje) { }

    public record VistaReplay(String texto, int caracteres, int simbolosDistintos, Map<Character, String> codigos,
                              long bitsOriginales, long bitsComprimidos, double tasaCompresion,
                              double ahorroPorcentaje, boolean decodificaIgual, String primerosBits) { }

    public record VistaInvasion(String origen, String destino, CaminosMinimos.Camino masBarataPorDijkstra,
                                List<Backtracking.Ruta> rutas, Backtracking.ComparacionPoda poda) { }

    // ==================================================================
    // Creacion
    // ==================================================================

    /** Crea una partida leyendo el mapa y el catalogo de Neo4j. */
    public VistaPartida crear(Dificultad dificultad, boolean aleatorio, Long semilla) {
        return vista(crearPartida(cargarMapa(), dificultad, aleatorio, semilla));
    }

    /**
     * Arma la partida sobre un mapa ya construido. Separado de crear() para
     * poder probarlo sin Neo4j.
     *
     * Escenario: backtracking (GeneradorDeEscenario). CLASICO = orden
     * determinista y produccion exactamente igual; ALEATORIO = orden mezclado
     * con la semilla y tolerancia de 1 de produccion.
     */
    public Partida crearPartida(Mapa mapa, Dificultad dificultad, boolean aleatorio, Long semilla) {
        GeneradorDeEscenario.Escenario escenario = escenario(mapa, aleatorio, semilla);
        Estado estado = motor.estadoInicial(mapa, escenario);
        String id = UUID.randomUUID().toString().substring(0, 8);
        Partida p = new Partida(id, mapa, dificultad, escenario, estado);
        p.registrar("Nueva partida (" + dificultad + "). AZUL arranca en " + nombre(mapa, escenario.capitalAzul())
                + " y ROJO en " + nombre(mapa, escenario.capitalRoja()) + ". Escenario armado por backtracking: "
                + escenario.nodosExplorados() + " nodos explorados, " + escenario.nodosPodados() + " podados.");
        registrarCobro(p, Estado.AZUL);

        partidas.put(id, p);
        ordenDeCreacion.add(id);
        while (ordenDeCreacion.size() > MAX_PARTIDAS_EN_MEMORIA) {
            partidas.remove(ordenDeCreacion.poll());
        }
        return p;
    }

    /** Backtracking: un arranque valido sobre el mapa de la base, sin crear partida. */
    public GeneradorDeEscenario.Escenario generarEscenario(Long semilla) {
        return escenario(cargarMapa(), semilla != null, semilla);
    }

    GeneradorDeEscenario.Escenario escenario(Mapa mapa, boolean aleatorio, Long semilla) {
        int n = mapa.cantidad();
        int[] produccion = new int[n];
        boolean[] puedeSerCapital = new boolean[n];
        for (int i = 0; i < n; i++) {
            produccion[i] = mapa.territorio(i).produccion();
            puedeSerCapital[i] = mapa.territorio(i).terreno().puedeSerCapital();
        }
        Long semillaReal = aleatorio ? (semilla != null ? semilla : System.nanoTime()) : null;
        return generador.generar(mapa.grafo(), produccion, puedeSerCapital, mapa.matrizDeDistancias(),
                TAMANIO_REGION_INICIAL, DISTANCIA_MINIMA_ENTRE_CAPITALES, aleatorio ? 1 : 0, semillaReal);
    }

    /** Lee territorios, rutas y catalogo de Neo4j (una consulta por coleccion). */
    Mapa cargarMapa() {
        List<Nodo> nodos = grafoService.nodosOrdenados();
        List<Mapa.Territorio> territorios = new ArrayList<>();
        List<Mapa.Ruta> rutas = new ArrayList<>();
        for (Nodo n : nodos) {
            Terreno terreno;
            try {
                terreno = Terreno.valueOf(n.getTipo());
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IllegalStateException("El nodo " + n.getId() + " tiene tipo '" + n.getTipo()
                        + "', que no es un terreno del juego. ¿La base tiene otro dominio cargado? "
                        + "Reiniciar con app.seed.enabled=true recarga el mapa.");
            }
            territorios.add(new Mapa.Territorio(n.getId(), n.getNombre(), terreno, (int) n.getValor(),
                    n.getX(), n.getY()));
            for (Conexion c : n.getConexiones()) {
                rutas.add(new Mapa.Ruta(n.getId(), c.getDestino().getId(), (int) c.getCosto()));
            }
        }
        return Mapa.construir(territorios, rutas, itemRepository.findAll(), caminos, ordenamiento);
    }

    public Partida partida(String id) {
        Partida p = partidas.get(id);
        if (p == null) {
            throw new IllegalArgumentException("No existe la partida " + id + ". Creá una nueva con POST /api/juego.");
        }
        return p;
    }

    // ==================================================================
    // Acciones del humano (AZUL)
    // ==================================================================

    public VistaPartida vista(String id) {
        return vista(partida(id));
    }

    /** Fase 2: comprar unidades. */
    public VistaPartida comprar(String id, List<Compra> compras) {
        Partida p = partida(id);
        synchronized (p) {
            validarTurnoHumano(p);
            List<Item> compradas = motor.comprar(p.mapa(), p.estado(), compras);
            registrarCompras(p, "AZUL", compras, compradas);
            return vista(p);
        }
    }

    /** Fase 3: marchar (Dijkstra por territorio propio). */
    public VistaPartida marchar(String id, Movimiento marcha) {
        Partida p = partida(id);
        synchronized (p) {
            validarTurnoHumano(p);
            CaminosMinimos.Camino c = motor.marchar(p.mapa(), p.estado(), marcha);
            registrarMarcha(p, "AZUL", marcha, c);
            return vista(p);
        }
    }

    /** Fase 4: atacar un vecino. */
    public VistaPartida atacar(String id, Movimiento ataque) {
        Partida p = partida(id);
        synchronized (p) {
            validarTurnoHumano(p);
            ResultadoAtaque r = motor.atacar(p.mapa(), p.estado(), ataque);
            registrarAtaque(p, "AZUL", r);
            return vista(p);
        }
    }

    /** Termina el turno del humano y la IA juega el suyo completo. */
    public VistaPartida terminarTurno(String id) {
        Partida p = partida(id);
        synchronized (p) {
            validarTurnoHumano(p);
            Estado e = p.estado();
            p.cerrarTurno(e.ronda(), Estado.AZUL);
            motor.terminarTurno(p.mapa(), e);
            if (e.terminada()) {
                registrarFinPorRondas(p);
                return vista(p);
            }
            registrarCobro(p, Estado.ROJO);
            jugarTurnoIa(p);
            return vista(p);
        }
    }

    private void jugarTurnoIa(Partida p) {
        Estado e = p.estado();
        Mapa mapa = p.mapa();
        Estratega.ReporteIa reporte = estratega.decidir(mapa, e, p.dificultad());
        p.setUltimoReporteIa(reporte);
        p.registrar("ROJO piensa con " + reporte.tecnica() + " (profundidad " + reporte.profundidad() + "): "
                + reporte.nodosVisitados() + " nodos, " + reporte.cortes() + " podas, " + reporte.milisegundos()
                + " ms. Plan: " + reporte.plan().descripcion() + ".");

        var plan = reporte.plan();
        List<Item> compradas = motor.comprar(mapa, e, plan.compras());
        registrarCompras(p, "ROJO", plan.compras(), compradas);
        if (plan.marcha() != null) {
            registrarMarcha(p, "ROJO", plan.marcha(), motor.marchar(mapa, e, plan.marcha()));
        }
        if (plan.ataque() != null) {
            registrarAtaque(p, "ROJO", motor.atacar(mapa, e, plan.ataque()));
        }
        int ronda = e.ronda();
        p.cerrarTurno(ronda, Estado.ROJO);
        if (e.terminada()) {
            return;
        }
        motor.terminarTurno(mapa, e);
        if (e.terminada()) {
            registrarFinPorRondas(p);
            return;
        }
        registrarCobro(p, Estado.AZUL);
    }

    private void validarTurnoHumano(Partida p) {
        Estado e = p.estado();
        if (e.terminada()) {
            throw new IllegalStateException("La partida terminó: ganó " + Estado.nombreJugador(e.ganador()) + ".");
        }
        if (e.jugadorActual() != Estado.AZUL) {
            throw new IllegalStateException("No es tu turno.");
        }
    }

    // ==================================================================
    // Consultas: cada una es un algoritmo
    // ==================================================================

    /** Dijkstra: vista previa del camino de marcha por territorio propio. */
    public VistaMarcha rutaDeMarcha(String id, String origen, String destino) {
        Partida p = partida(id);
        synchronized (p) {
            CaminosMinimos.Camino c = motor.rutaDeMarcha(p.mapa(), p.estado(), origen, destino);
            return new VistaMarcha(c.vertices(), c.costoTotal(), MotorDeJuego.PUNTOS_DE_MARCHA,
                    c.costoTotal() <= MotorDeJuego.PUNTOS_DE_MARCHA);
        }
    }

    /** Busqueda binaria + greedy + mochila 0/1 sobre el oro del jugador que tiene el turno. */
    public VistaMercado mercado(String id) {
        Partida p = partida(id);
        synchronized (p) {
            Estado e = p.estado();
            int oro = e.oro(e.jugadorActual());
            List<Item> catalogo = p.mapa().catalogo();
            int corte = busquedaBinaria.ultimoMenorOIgual(catalogo, oro, Item::getPeso);
            Seleccion.Resultado g = motor.compraGreedy(p.mapa(), oro);
            Seleccion.Resultado pd = motor.compraOptima(p.mapa(), oro);
            return new VistaMercado(Estado.nombreJugador(e.jugadorActual()), oro, catalogo, corte,
                    busquedaBinaria.asequibles(catalogo, oro), g, pd, pd.valorTotal() - g.valorTotal());
        }
    }

    /** Greedy: ataques que ganan hoy, ordenados por ratio ganancia/riesgo. */
    public List<SugerenciaAtaque> sugerenciasDeAtaque(String id) {
        Partida p = partida(id);
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            int j = e.jugadorActual();
            int rival = Estado.rival(j);
            List<SugerenciaAtaque> candidatos = new ArrayList<>();
            for (int s = 0; s < mapa.cantidad(); s++) {
                if (e.duenio(s) != j) {
                    continue;
                }
                for (Grafo.Arista a : mapa.grafo().vecinos(s)) {
                    int t = a.destino();
                    if (e.duenio(t) == j) {
                        continue;
                    }
                    int disponibles = e.tropas(s) - 1;
                    int def = motor.defensa(mapa, e, t);
                    if (disponibles <= def) {
                        continue; // no gana: no es candidato
                    }
                    boolean capitalRival = e.duenio(t) == rival && e.capital(rival) == t;
                    double ganancia = 2.0 * mapa.territorio(t).produccion() + (e.duenio(t) == rival ? 3 : 0)
                            + (capitalRival ? 1000 : 0);
                    double riesgo = def + 1 + Math.max(0, motor.amenazaDirecta(mapa, e, t, j) - (disponibles - def));
                    candidatos.add(new SugerenciaAtaque(mapa.id(s), mapa.id(t), mapa.territorio(t).nombre(),
                            disponibles, def, ganancia, riesgo, greedy.ratio(ganancia, riesgo)));
                }
            }
            return greedy.rankingPorRatio(candidatos, SugerenciaAtaque::ganancia, SugerenciaAtaque::riesgo);
        }
    }

    /**
     * AVL: territorios ajenos con clave = defensa efectiva. Consulta por rango
     * [defensaMinima, defensaMaxima] y los k de mayor produccion. Si no se da
     * defensaMaxima, se usa lo maximo que se puede mandar desde un solo
     * territorio menos 1 (lo que se puede conquistar hoy).
     */
    public VistaObjetivos objetivos(String id, double defensaMinima, Double defensaMaxima, int k) {
        Partida p = partida(id);
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            int j = e.jugadorActual();
            double maxima = defensaMaxima != null ? defensaMaxima : maximoEnviable(mapa, e, j) - 1;
            ArbolAVL<VistaObjetivo> avl = new ArbolAVL<>();
            for (int t = 0; t < mapa.cantidad(); t++) {
                if (e.duenio(t) != j) {
                    int def = motor.defensa(mapa, e, t);
                    avl.insertar(def, new VistaObjetivo(mapa.id(t), mapa.territorio(t).nombre(),
                            Estado.nombreJugador(e.duenio(t)), def, mapa.territorio(t).produccion()));
                }
            }
            return new VistaObjetivos(defensaMinima, maxima, k, avl.tamanio(), avl.altura(), avl.rotaciones(),
                    avl.rango(defensaMinima, maxima),
                    avl.kMejoresEnRango(defensaMinima, maxima, k, VistaObjetivo::produccion));
        }
    }

    private int maximoEnviable(Mapa mapa, Estado e, int j) {
        int max = 0;
        for (int i = 0; i < mapa.cantidad(); i++) {
            if (e.duenio(i) == j) {
                max = Math.max(max, e.tropas(i) - 1);
            }
        }
        return max;
    }

    /** BFS multi-fuente: a cuantas conquistas (turnos) esta cada territorio. */
    public Map<String, Integer> alcance(String id, int turnos) {
        Partida p = partida(id);
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            List<String> propios = new ArrayList<>();
            for (int i = 0; i < mapa.cantidad(); i++) {
                if (e.duenio(i) == e.jugadorActual()) {
                    propios.add(mapa.id(i));
                }
            }
            return recorridos.bfsPorNiveles(mapa.grafo(), propios, turnos);
        }
    }

    /** DFS (componentes) + Prim y Kruskal sobre la red de abastecimiento de un jugador. */
    public VistaImperio imperio(String id, String jugador) {
        Partida p = partida(id);
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            int j = jugador == null ? e.jugadorActual() : "ROJO".equalsIgnoreCase(jugador) ? Estado.ROJO : Estado.AZUL;
            Grafo propio = motor.subgrafoPropio(mapa, e, j);
            List<List<String>> componentes = recorridos.componentesConexas(propio);
            Abastecimiento ab = motor.abastecimiento(mapa, e, j);
            ArbolRecubrimiento.Mst prim = new ArbolRecubrimiento.Mst(List.of(), 0);
            ArbolRecubrimiento.Mst kruskal = new ArbolRecubrimiento.Mst(List.of(), 0);
            if (!ab.abastecidos().isEmpty()) {
                Grafo red = new Grafo(false);
                for (String t : ab.abastecidos()) {
                    red.agregarVertice(t);
                }
                for (Mapa.Ruta r : mapa.rutas()) {
                    if (ab.abastecidos().contains(r.origen()) && ab.abastecidos().contains(r.destino())) {
                        red.agregarArista(r.origen(), r.destino(), r.costo());
                    }
                }
                prim = arbol.prim(red, mapa.id(e.capital(j)));
                kruskal = arbol.kruskal(red);
            }
            return new VistaImperio(Estado.nombreJugador(j), componentes, componentes.size() > 1,
                    ab.abastecidos(), ab.aislados(), prim, kruskal,
                    Math.abs(prim.costoTotal() - kruskal.costoTotal()) < 1e-9, ab.mantenimiento());
        }
    }

    /**
     * Branch & Bound: reparte la compra optima de este turno entre los frentes
     * del jugador (territorios abastecidos que lindan con algo ajeno),
     * maximizando el margen del frente mas debil.
     * margen de un frente = defensa - amenaza directa del rival.
     */
    public VistaReparto repartir(String id) {
        Partida p = partida(id);
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            int j = e.jugadorActual();
            Abastecimiento ab = motor.abastecimiento(mapa, e, j);
            List<RamificacionYPoda.Frente> frentes = new ArrayList<>();
            for (int f : motor.frontera(mapa, e, j, false)) {
                if (ab.abastecido(f)) {
                    frentes.add(new RamificacionYPoda.Frente(mapa.id(f),
                            motor.defensa(mapa, e, f) - motor.amenazaDirecta(mapa, e, f, j)));
                }
            }
            if (frentes.isEmpty()) {
                throw new IllegalStateException("No tenés frentes abastecidos para reforzar.");
            }
            List<Item> unidades = motor.compraOptima(mapa, e.oro(j)).elegidos();
            RamificacionYPoda.Reparto reparto = ramificacionYPoda.repartir(unidades, frentes);
            List<Compra> comoCompra = new ArrayList<>();
            for (Map.Entry<String, List<Item>> en : reparto.porFrente().entrySet()) {
                for (Item u : en.getValue()) {
                    comoCompra.add(new Compra(u.getId(), en.getKey()));
                }
            }
            return new VistaReparto(unidades, frentes, reparto, comoCompra);
        }
    }

    /**
     * QuickSort / MergeSort sobre territorios.
     *   criterio=valor   -> QuickSort de TODOS los territorios por valor estrategico
     *                       (2*produccion + bonus de terreno + cantidad de rutas + 5 si es capital)
     *   criterio=amenaza -> MergeSort (estable) de los territorios PROPIOS por amenaza
     *                       directa del rival menos su defensa
     */
    public List<VistaRanking> ranking(String id, String criterio) {
        Partida p = partida(id);
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            int j = e.jugadorActual();
            List<VistaRanking> filas = new ArrayList<>();
            switch (criterio) {
                case "valor" -> {
                    for (int t = 0; t < mapa.cantidad(); t++) {
                        int dueno = e.duenio(t);
                        boolean capital = dueno != Estado.NEUTRAL && e.capital(dueno) == t;
                        double valor = 2.0 * mapa.territorio(t).produccion()
                                + mapa.territorio(t).terreno().bonusDefensa()
                                + mapa.grafo().vecinos(t).size() + (capital ? 5 : 0);
                        filas.add(new VistaRanking(mapa.id(t), mapa.territorio(t).nombre(),
                                Estado.nombreJugador(dueno), valor));
                    }
                    return ordenamiento.quickSort(filas, (a, b) -> Double.compare(b.puntaje(), a.puntaje()));
                }
                case "amenaza" -> {
                    for (int t = 0; t < mapa.cantidad(); t++) {
                        if (e.duenio(t) == j) {
                            double amenaza = motor.amenazaDirecta(mapa, e, t, j) - motor.defensa(mapa, e, t);
                            filas.add(new VistaRanking(mapa.id(t), mapa.territorio(t).nombre(),
                                    Estado.nombreJugador(j), amenaza));
                        }
                    }
                    return ordenamiento.mergeSort(filas, (a, b) -> Double.compare(b.puntaje(), a.puntaje()));
                }
                default -> throw new IllegalArgumentException("Criterio inválido: " + criterio + ". Use valor o amenaza.");
            }
        }
    }

    /** MIN-MAX contra alfa-beta sobre el estado actual, para el jugador que tiene el turno. */
    public Estratega.ComparacionIa comparacionIa(String id, int profundidad) {
        Partida p = partida(id);
        synchronized (p) {
            return estratega.comparar(p.mapa(), p.estado(), profundidad);
        }
    }

    /** "Que haria la IA en mi lugar": alfa-beta a profundidad 2 para AZUL, sin aplicar nada. */
    public Estratega.ReporteIa consejo(String id) {
        Partida p = partida(id);
        synchronized (p) {
            validarTurnoHumano(p);
            return estratega.decidir(p.mapa(), p.estado(), Dificultad.NORMAL);
        }
    }

    /** Huffman sobre el replay compacto de los turnos ya jugados. */
    public VistaReplay replay(String id) {
        Partida p = partida(id);
        String texto;
        synchronized (p) {
            texto = p.replay();
        }
        if (texto.isEmpty()) {
            throw new IllegalStateException("Todavía no terminó ningún turno: no hay replay para comprimir.");
        }
        Huffman.Compresion c = huffman.comprimir(texto);
        boolean igual = huffman.descomprimir(c.bits(), c.arbol()).equals(texto);
        double ahorro = Math.round(10000.0 * (1 - c.tasaCompresion())) / 100.0;
        String muestra = c.bits().length() > 120 ? c.bits().substring(0, 120) + "..." : c.bits();
        return new VistaReplay(texto, texto.length(), c.codigos().size(), c.codigos(), c.bitsOriginales(),
                c.bitsComprimidos(), c.tasaCompresion(), ahorro, igual, muestra);
    }

    /**
     * Backtracking: rutas simples desde la capital del jugador hasta la capital
     * rival por el mapa completo, con limite de dias y de territorios cruzados,
     * y la comparacion de nodos explorados con poda y sin poda.
     */
    public VistaInvasion rutasDeInvasion(String id, double costoMaximo, int saltosMaximos) {
        Partida p = partida(id);
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            int j = e.jugadorActual();
            String origen = mapa.id(e.capital(j));
            String destino = mapa.id(e.capital(Estado.rival(j)));
            return new VistaInvasion(origen, destino,
                    caminos.dijkstra(mapa.grafo(), origen, destino),
                    backtracking.rutasSimples(mapa.grafo(), origen, destino, costoMaximo, saltosMaximos),
                    backtracking.compararPoda(mapa.grafo(), origen, destino, costoMaximo, saltosMaximos));
        }
    }

    // ==================================================================
    // Registro de eventos y armado de la vista
    // ==================================================================

    private void registrarCobro(Partida p, int jugador) {
        Abastecimiento ab = motor.abastecimiento(p.mapa(), p.estado(), jugador);
        String aislados = ab.aislados().isEmpty() ? ""
                : " ¡" + ab.aislados().size() + " territorio(s) aislado(s) de la capital: " + ab.aislados() + "!";
        p.registrar("— Ronda " + p.estado().ronda() + ", turno de " + Estado.nombreJugador(jugador) + ": cobra "
                + ab.ingresoNeto() + " de oro (producción " + ab.produccion() + " - mantenimiento de la red "
                + ab.mantenimiento() + "). Tesoro: " + p.estado().oro(jugador) + "." + aislados);
    }

    private void registrarCompras(Partida p, String quien, List<Compra> compras, List<Item> compradas) {
        if (compras.isEmpty()) {
            return;
        }
        int costo = 0;
        int poder = 0;
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < compradas.size(); i++) {
            Item u = compradas.get(i);
            costo += (int) u.getPeso();
            poder += (int) u.getValor();
            nombres.add(u.getNombre() + " en " + compras.get(i).destino());
            p.registrarAccion("C" + u.getId() + "@" + compras.get(i).destino());
        }
        p.registrar(quien + " recluta " + String.join(", ", nombres) + " (costo " + costo + ", poder +" + poder + ").");
    }

    private void registrarMarcha(Partida p, String quien, Movimiento m, CaminosMinimos.Camino c) {
        p.registrarAccion("M" + m.origen() + ">" + m.destino() + "x" + m.tropas());
        p.registrar(quien + " marcha con " + m.tropas() + " tropas por " + String.join("→", c.vertices())
                + " (" + (int) c.costoTotal() + " días).");
    }

    private void registrarAtaque(Partida p, String quien, ResultadoAtaque r) {
        p.registrarAccion("A" + r.origen() + ">" + r.destino() + "x" + r.enviadas() + (r.conquistado() ? "+" : "-"));
        String texto = quien + " ataca " + r.destino() + " desde " + r.origen() + " con " + r.enviadas()
                + " contra defensa " + r.defensa() + ": "
                + (r.conquistado() ? "¡CONQUISTADO! Quedan " + r.tropasQueQuedan() + " tropas."
                : "rechazado. El defensor queda con " + r.tropasQueQuedan() + ".");
        if (r.capitalCapturada()) {
            texto += " Cayó la capital: ¡gana " + quien + "!";
        }
        p.registrar(texto);
    }

    private void registrarFinPorRondas(Partida p) {
        Estado e = p.estado();
        p.registrar("Se llegó al límite de " + MotorDeJuego.LIMITE_RONDAS + " rondas. Puntaje AZUL "
                + motor.puntaje(p.mapa(), e, Estado.AZUL) + ", ROJO " + motor.puntaje(p.mapa(), e, Estado.ROJO)
                + ": " + (e.ganador() == Estado.EMPATE ? "empate." : "gana " + Estado.nombreJugador(e.ganador()) + "."));
    }

    private String nombre(Mapa mapa, String id) {
        return mapa.territorio(mapa.indice(id)).nombre() + " (" + id + ")";
    }

    VistaPartida vista(Partida p) {
        synchronized (p) {
            Mapa mapa = p.mapa();
            Estado e = p.estado();
            Abastecimiento azul = motor.abastecimiento(mapa, e, Estado.AZUL);
            Abastecimiento rojo = motor.abastecimiento(mapa, e, Estado.ROJO);

            List<VistaTerritorio> territorios = new ArrayList<>();
            for (int i = 0; i < mapa.cantidad(); i++) {
                Mapa.Territorio t = mapa.territorio(i);
                int dueno = e.duenio(i);
                boolean capital = dueno != Estado.NEUTRAL && e.capital(dueno) == i;
                boolean abastecido = dueno == Estado.AZUL ? azul.abastecido(i)
                        : dueno == Estado.ROJO && rojo.abastecido(i);
                territorios.add(new VistaTerritorio(t.id(), t.nombre(), t.terreno().nombre(), t.produccion(),
                        t.x(), t.y(), Estado.nombreJugador(dueno), e.tropas(i), motor.defensa(mapa, e, i),
                        capital, abastecido));
            }
            Map<String, VistaJugador> jugadores = new LinkedHashMap<>();
            jugadores.put("AZUL", vistaJugador(mapa, e, Estado.AZUL, azul));
            jugadores.put("ROJO", vistaJugador(mapa, e, Estado.ROJO, rojo));

            List<String> eventos = p.eventos();
            List<String> ultimos = new ArrayList<>(eventos.subList(Math.max(0, eventos.size() - 40), eventos.size()));
            return new VistaPartida(p.id(), p.dificultad().name(), e.ronda(), MotorDeJuego.LIMITE_RONDAS,
                    Estado.nombreJugador(e.jugadorActual()), e.fase().name(),
                    e.terminada() ? Estado.nombreJugador(e.ganador()) : null, MotorDeJuego.PUNTOS_DE_MARCHA,
                    territorios, mapa.rutas(), jugadores, mapa.catalogo(), ultimos, p.ultimoReporteIa(),
                    p.escenario());
        }
    }

    private VistaJugador vistaJugador(Mapa mapa, Estado e, int j, Abastecimiento ab) {
        int territorios = 0;
        int tropas = 0;
        for (int i = 0; i < mapa.cantidad(); i++) {
            if (e.duenio(i) == j) {
                territorios++;
                tropas += e.tropas(i);
            }
        }
        return new VistaJugador(Estado.nombreJugador(j), e.oro(j), mapa.id(e.capital(j)), territorios, tropas,
                ab.produccion(), ab.mantenimiento(), ab.ingresoNeto(), ab.costoRed(), ab.red(), ab.aislados(),
                ab.componentes(), motor.puntaje(mapa, e, j));
    }
}
