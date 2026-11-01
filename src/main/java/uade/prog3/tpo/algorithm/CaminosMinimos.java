package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * UNIDAD: Grafos II (clase 5) y PD sobre grafos (clase 8)
 * PUNTAJE: parte de los 3 puntos de "Dijkstra, Prim, Kruskal"
 *          y 1 punto de programacion dinamica si implementan Floyd.
 *
 * En el juego:
 *  - Dijkstra es la fase de MARCHA: llevar tropas de un territorio propio a
 *    otro por el camino de menos dias de marcha, pasando solo por territorio
 *    propio. Si el camino cuesta mas que los puntos de marcha del turno, la
 *    marcha no se puede hacer.
 *  - Floyd es la matriz de distancias precalculada que usa la IA para evaluar
 *    amenazas en O(1).
 */
@Component
public class CaminosMinimos {

    /** Resultado de una consulta de camino minimo. */
    public record Camino(List<String> vertices, double costoTotal) { }

    /** Entrada de la cola de prioridad: (vertice, distancia tentativa). */
    private record EnCola(int vertice, double distancia) { }

    /**
     * Dijkstra: camino de costo minimo desde un origen hasta un destino.
     *
     * INVARIANTE: cuando un vertice se extrae de la cola, su distancia ya es
     * definitiva. Eso vale solo si todos los pesos son no negativos: con un
     * peso negativo podria aparecer despues un camino mas barato hacia un
     * vertice ya cerrado, y el algoritmo no vuelve atras. Por eso se valida.
     *
     * Dijkstra es greedy porque en cada paso toma la decision localmente
     * optima (cerrar el vertice abierto mas cercano) y no la revisa nunca mas.
     *
     * Complejidad: O((V + E) log V). Cada arista puede provocar a lo sumo un
     * push (E log V) y cada vertice se cierra una vez (V log V).
     * Espacio: O(V) para dist[], previo[] y listo[], mas O(E) de cola.
     *
     * Estructura: PriorityQueue (heap binario) + arreglo de predecesores.
     * Se usa "lazy deletion": en vez de decrementar la clave, se pushea una
     * entrada nueva y se descartan las obsoletas al extraerlas.
     */
    public Camino dijkstra(Grafo grafo, String origenId, String destinoId) {
        int origen = grafo.indiceDe(origenId);
        int destino = grafo.indiceDe(destinoId);
        int n = grafo.cantidadVertices();

        validarPesosNoNegativos(grafo);

        double[] dist = new double[n];
        int[] previo = new int[n];
        boolean[] listo = new boolean[n];
        for (int i = 0; i < n; i++) {
            dist[i] = Grafo.INFINITO;
            previo[i] = -1;
        }
        dist[origen] = 0.0;

        PriorityQueue<EnCola> cola = new PriorityQueue<>((x, y) -> Double.compare(x.distancia(), y.distancia()));
        cola.add(new EnCola(origen, 0.0));

        while (!cola.isEmpty()) {
            EnCola actual = cola.poll();
            int u = actual.vertice();

            if (listo[u]) {
                continue; // entrada obsoleta: ya se cerro con una distancia menor
            }
            listo[u] = true;

            if (u == destino) {
                break; // su distancia ya es definitiva, no hace falta seguir
            }

            for (Grafo.Arista a : grafo.vecinos(u)) {
                int v = a.destino();
                // RELAJACION: si llegar a v pasando por u es mas barato, se mejora
                double candidato = dist[u] + a.costo();
                if (candidato < dist[v]) {
                    dist[v] = candidato;
                    previo[v] = u;
                    cola.add(new EnCola(v, candidato));
                }
            }
        }

        if (dist[destino] == Grafo.INFINITO) {
            throw new IllegalStateException(
                    "No existe camino de " + origenId + " a " + destinoId);
        }
        return new Camino(reconstruir(grafo, previo, origen, destino), dist[destino]);
    }

    /**
     * Reconstruye el camino siguiendo el arreglo de predecesores hacia atras
     * desde el destino y despues invirtiendo. Sin previo[] solo se tendria el
     * costo, y el enunciado pide el camino.
     */
    private List<String> reconstruir(Grafo grafo, int[] previo, int origen, int destino) {
        List<String> camino = new ArrayList<>();
        for (int actual = destino; actual != -1; actual = previo[actual]) {
            camino.add(grafo.idDe(actual));
            if (actual == origen) {
                break;
            }
        }
        Collections.reverse(camino);
        return camino;
    }

    private void validarPesosNoNegativos(Grafo grafo) {
        for (int u = 0; u < grafo.cantidadVertices(); u++) {
            for (Grafo.Arista a : grafo.vecinos(u)) {
                if (a.costo() < 0) {
                    throw new IllegalArgumentException(
                            "Dijkstra no admite aristas de costo negativo. La arista "
                                    + grafo.idDe(u) + "->" + grafo.idDe(a.destino())
                                    + " cuesta " + a.costo() + ". Usar Floyd-Warshall o Bellman-Ford.");
                }
            }
        }
    }

    /**
     * Floyd-Warshall: costo minimo entre TODOS los pares de vertices.
     *
     * INVARIANTE: al terminar la iteracion k, m[i][j] es el costo minimo de i
     * a j usando como intermedios solo vertices de indice <= k. Por eso el
     * bucle de k va SIEMPRE afuera: si estuviera adentro, se estarian mezclando
     * subproblemas de distinto nivel y la matriz queda mal aunque parezca bien.
     *
     * Es programacion dinamica: la recurrencia es
     *   d_k(i,j) = min( d_{k-1}(i,j) , d_{k-1}(i,k) + d_{k-1}(k,j) )
     *
     * Complejidad: O(V^3) en tiempo, O(V^2) en espacio.
     *
     * Devuelve un mapa "origen->destino" con el costo minimo de cada par.
     * Los pares inalcanzables se omiten: Infinity no es JSON valido.
     */
    public Map<String, Double> floydWarshall(Grafo grafo) {
        int n = grafo.cantidadVertices();
        double[][] m = floydMatriz(grafo);

        Map<String, Double> resultado = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (m[i][j] != Grafo.INFINITO) {
                    resultado.put(grafo.idDe(i) + "->" + grafo.idDe(j), m[i][j]);
                }
            }
        }
        return resultado;
    }

    /**
     * El nucleo de Floyd-Warshall, devolviendo la MATRIZ de distancias.
     *
     * USO EN EL JUEGO: el mapa no cambia durante la partida (cambian los
     * duenos y las tropas, no las rutas), asi que la matriz se calcula UNA vez
     * al crear la partida y la IA la consulta en O(1) cada vez que evalua un
     * estado ("que tan lejos esta este ejercito enemigo de mi capital"). La
     * funcion de evaluacion se llama miles de veces por jugada: correr
     * Dijkstra en cada una seria O((V+E) log V) por consulta en vez de O(1).
     *
     * Complejidad: O(V^3) tiempo, O(V^2) espacio.
     */
    public double[][] floydMatriz(Grafo grafo) {
        return floydConConteo(grafo, new long[2]);
    }

    /**
     * @param contadores [0] = combinaciones (k,i,j) evaluadas, [1] = mejoras.
     *                   Se usan para la comparacion contra Dijkstra del hito 7.
     */
    private double[][] floydConConteo(Grafo grafo, long[] contadores) {
        int n = grafo.cantidadVertices();
        double[][] m = grafo.matrizDeAdyacencia();

        for (int k = 0; k < n; k++) {          // <-- el intermedio va afuera
            for (int i = 0; i < n; i++) {
                if (m[i][k] == Grafo.INFINITO) {
                    contadores[0] += n;   // la fila entera se descarta de una
                    continue; // no hay forma de llegar a k desde i: nada que mejorar
                }
                for (int j = 0; j < n; j++) {
                    contadores[0]++;
                    if (m[k][j] == Grafo.INFINITO) {
                        continue;
                    }
                    double porK = m[i][k] + m[k][j];
                    if (porK < m[i][j]) {
                        m[i][j] = porK;
                        contadores[1]++;
                    }
                }
            }
        }

        // Un ciclo negativo se delata como una distancia de un vertice a si mismo
        // que quedo por debajo de 0: se pudo "dar la vuelta" abaratando.
        for (int i = 0; i < n; i++) {
            if (m[i][i] < 0) {
                throw new IllegalStateException(
                        "El grafo tiene un ciclo de costo negativo que pasa por "
                                + grafo.idDe(i) + ". El camino minimo no esta definido.");
            }
        }
        return m;
    }

    /** Evidencia del hito 7: Floyd-Warshall contra correr Dijkstra V veces. */
    public record ComparacionTodosContraTodos(int vertices, int aristas,
                                              long floydCombinacionesEvaluadas, long floydMejoras,
                                              long dijkstraVerticesExpandidos, long dijkstraAristasRelajadas,
                                              long dijkstraInsercionesEnCola,
                                              boolean mismosResultados, String conclusion) { }

    /**
     * Hito 7: "comparar los nodos expandidos contra correr Dijkstra V veces".
     *
     * Floyd no "expande" vertices como Dijkstra: evalua las V^3 combinaciones
     * (k, i, j) sin importar cuantas aristas haya. Dijkstra desde cada origen
     * expande cada vertice alcanzable una vez y relaja cada arista una vez, o
     * sea V*(V + E) operaciones de cola en total, cada una con su log V.
     *
     * Las dos tecnicas tienen que dar EXACTAMENTE las mismas distancias: si no
     * coinciden, alguna de las dos tiene un bug. Eso tambien se informa.
     *
     * Floyd conviene en grafos DENSOS (E ~ V^2) y cuando hay pesos negativos
     * sin ciclos negativos. Dijkstra V veces conviene en grafos RALOS como el
     * mapa del juego (cada territorio linda con 2 a 4).
     */
    public ComparacionTodosContraTodos compararFloydContraDijkstra(Grafo grafo) {
        int n = grafo.cantidadVertices();
        validarPesosNoNegativos(grafo); // Dijkstra no los admite

        long[] contFloyd = new long[2];
        double[][] porFloyd = floydConConteo(grafo, contFloyd);

        long[] contDijkstra = new long[3]; // expandidos, relajaciones, inserciones
        boolean coinciden = true;
        for (int origen = 0; origen < n; origen++) {
            double[] dist = dijkstraDesde(grafo, origen, contDijkstra);
            for (int j = 0; j < n; j++) {
                if (Math.abs(dist[j] - porFloyd[origen][j]) > 1e-9
                        && !(dist[j] == Grafo.INFINITO && porFloyd[origen][j] == Grafo.INFINITO)) {
                    coinciden = false;
                }
            }
        }

        String conclusion = contDijkstra[0] + contDijkstra[1] < contFloyd[0]
                ? "Grafo ralo: Dijkstra V veces hace menos trabajo que las V^3 combinaciones de Floyd."
                : "Grafo denso: las V^3 combinaciones de Floyd no son peores que Dijkstra V veces.";
        return new ComparacionTodosContraTodos(n, grafo.cantidadAristas(),
                contFloyd[0], contFloyd[1],
                contDijkstra[0], contDijkstra[1], contDijkstra[2],
                coinciden, conclusion);
    }

    /**
     * Dijkstra COMPLETO desde un origen: distancia minima a cada vertice
     * alcanzable (los inalcanzables no aparecen).
     *
     * USO EN EL JUEGO: la IA lo corre sobre el subgrafo de territorios
     * propios para saber desde donde puede traer tropas a un frente sin
     * pasarse de los puntos de marcha del turno.
     *
     * Complejidad: O((V + E) log V), igual que dijkstra(origen, destino) sin
     * la parada temprana.
     */
    public Map<String, Double> distanciasDesde(Grafo grafo, String origenId) {
        validarPesosNoNegativos(grafo);
        double[] dist = dijkstraDesde(grafo, grafo.indiceDe(origenId), new long[3]);
        Map<String, Double> resultado = new LinkedHashMap<>();
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] != Grafo.INFINITO) {
                resultado.put(grafo.idDe(i), dist[i]);
            }
        }
        return resultado;
    }

    /**
     * Dijkstra completo (sin destino) desde un origen, contando trabajo.
     * contadores: [0] vertices expandidos, [1] aristas relajadas, [2] pushes.
     */
    private double[] dijkstraDesde(Grafo grafo, int origen, long[] contadores) {
        int n = grafo.cantidadVertices();
        double[] dist = new double[n];
        boolean[] listo = new boolean[n];
        for (int i = 0; i < n; i++) {
            dist[i] = Grafo.INFINITO;
        }
        dist[origen] = 0.0;
        PriorityQueue<EnCola> cola = new PriorityQueue<>((x, y) -> Double.compare(x.distancia(), y.distancia()));
        cola.add(new EnCola(origen, 0.0));
        contadores[2]++;
        while (!cola.isEmpty()) {
            int u = cola.poll().vertice();
            if (listo[u]) {
                continue;
            }
            listo[u] = true;
            contadores[0]++;
            for (Grafo.Arista a : grafo.vecinos(u)) {
                contadores[1]++;
                double candidato = dist[u] + a.costo();
                if (candidato < dist[a.destino()]) {
                    dist[a.destino()] = candidato;
                    cola.add(new EnCola(a.destino(), candidato));
                    contadores[2]++;
                }
            }
        }
        return dist;
    }

    /**
     * UCS (Uniform Cost Search): igual que Dijkstra pero con parada temprana
     * al expandir el destino, y SIN precalcular distancias a todos los demas.
     *
     * La diferencia practica es cuantos vertices se expanden. Para medirla,
     * usar expandidosUcs() y expandidosDijkstraCompleto() sobre la misma
     * consulta: es la evidencia que pide el enunciado.
     *
     * Complejidad: la misma cota O((V + E) log V), pero en la practica expande
     * menos porque corta apenas cierra el destino.
     */
    public Camino ucs(Grafo grafo, String origenId, String destinoId) {
        return dijkstra(grafo, origenId, destinoId); // ya corta al cerrar el destino
    }

    /** Cuenta cuantos vertices cierra UCS antes de llegar al destino. */
    public int expandidosUcs(Grafo grafo, String origenId, String destinoId) {
        return contarExpandidos(grafo, origenId, destinoId, true);
    }

    /** Cuenta cuantos vertices cierra Dijkstra si se lo deja recorrer todo. */
    public int expandidosDijkstraCompleto(Grafo grafo, String origenId) {
        return contarExpandidos(grafo, origenId, null, false);
    }

    private int contarExpandidos(Grafo grafo, String origenId, String destinoId, boolean cortar) {
        int origen = grafo.indiceDe(origenId);
        int destino = destinoId == null ? -1 : grafo.indiceDe(destinoId);
        int n = grafo.cantidadVertices();

        double[] dist = new double[n];
        boolean[] listo = new boolean[n];
        for (int i = 0; i < n; i++) {
            dist[i] = Grafo.INFINITO;
        }
        dist[origen] = 0.0;

        PriorityQueue<EnCola> cola = new PriorityQueue<>((x, y) -> Double.compare(x.distancia(), y.distancia()));
        cola.add(new EnCola(origen, 0.0));
        int expandidos = 0;

        while (!cola.isEmpty()) {
            EnCola actual = cola.poll();
            int u = actual.vertice();
            if (listo[u]) {
                continue;
            }
            listo[u] = true;
            expandidos++;
            if (cortar && u == destino) {
                break;
            }
            for (Grafo.Arista a : grafo.vecinos(u)) {
                double candidato = dist[u] + a.costo();
                if (candidato < dist[a.destino()]) {
                    dist[a.destino()] = candidato;
                    cola.add(new EnCola(a.destino(), candidato));
                }
            }
        }
        return expandidos;
    }
}
