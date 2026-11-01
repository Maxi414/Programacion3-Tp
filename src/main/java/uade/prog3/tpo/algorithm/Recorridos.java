package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UNIDAD: Grafos I (clase 4)          PUNTAJE: 2 puntos
 *
 * En el juego: el mapa es un grafo NO dirigido (una ruta se recorre en los
 * dos sentidos al mismo costo). BFS responde "a cuantos turnos esta cada
 * territorio" y DFS "mi imperio sigue entero o quedo partido".
 */
@Component
public class Recorridos {

    /**
     * Recorrido en profundidad desde un vertice.
     *
     * INVARIANTE: un vertice se marca visitado ANTES de expandir sus vecinos.
     * Eso garantiza terminacion aunque el grafo tenga ciclos: cada vertice
     * entra a la recursion exactamente una vez.
     *
     * Complejidad: O(V + E). Cada vertice se marca una sola vez (V) y la lista
     * de adyacencia de cada uno se recorre una sola vez, sumando E en total.
     * Espacio: O(V) por el arreglo de visitados mas la pila de llamadas, que
     * en el peor caso (grafo camino) tiene profundidad V.
     *
     * Estructura: recursion (pila de llamadas implicita) + boolean[] visitado.
     */
    public List<String> dfs(Grafo grafo, String origenId) {
        int origen = grafo.indiceDe(origenId); // valida el id: lanza IllegalArgumentException si no existe
        boolean[] visitado = new boolean[grafo.cantidadVertices()];
        List<String> orden = new ArrayList<>();
        dfsRecursivo(grafo, origen, visitado, orden);
        return orden;
    }

    /** Marcar, registrar, expandir. Marcar despues de expandir cicla al infinito. */
    private void dfsRecursivo(Grafo grafo, int u, boolean[] visitado, List<String> orden) {
        visitado[u] = true;
        orden.add(grafo.idDe(u));
        for (Grafo.Arista a : grafo.vecinos(u)) {
            if (!visitado[a.destino()]) {
                dfsRecursivo(grafo, a.destino(), visitado, orden);
            }
        }
    }

    /**
     * Variante iterativa con pila explicita. No la usa el endpoint, pero es la
     * misma idea sin recursion y evita StackOverflowError en grafos profundos.
     * El orden puede diferir del recursivo: la pila invierte el orden de los
     * vecinos. Ambos son DFS validos.
     */
    public List<String> dfsIterativo(Grafo grafo, String origenId) {
        int origen = grafo.indiceDe(origenId);
        boolean[] visitado = new boolean[grafo.cantidadVertices()];
        List<String> orden = new ArrayList<>();
        Deque<Integer> pila = new ArrayDeque<>();

        pila.push(origen);
        while (!pila.isEmpty()) {
            int u = pila.pop();
            if (visitado[u]) {
                continue; // pudo entrar dos veces antes de ser expandido
            }
            visitado[u] = true;
            orden.add(grafo.idDe(u));
            List<Grafo.Arista> vecinos = grafo.vecinos(u);
            for (int i = vecinos.size() - 1; i >= 0; i--) { // reverso: el primer vecino queda arriba
                if (!visitado[vecinos.get(i).destino()]) {
                    pila.push(vecinos.get(i).destino());
                }
            }
        }
        return orden;
    }

    /**
     * Recorrido en anchura desde un vertice.
     *
     * INVARIANTE: se marca visitado AL ENCOLAR, no al desencolar. Marcando al
     * desencolar el resultado sigue siendo alcanzable pero un mismo vertice
     * entra varias veces a la cola: aparecen duplicados en el orden y la
     * complejidad se degrada.
     *
     * Complejidad: O(V + E). Espacio: O(V).
     * Estructura: cola FIFO (ArrayDeque) + boolean[] visitado.
     *
     * Para el parcial: BFS da el camino con MENOS ARISTAS, no el de menor
     * costo. En el mapa del juego PIN->CON es 1 arista y cuesta 10 dias de
     * marcha (el paso de montana), mientras que PIN->AZU->VAD->CON son 3
     * aristas y cuestan 9. Para minimizar costo hace falta Dijkstra.
     */
    public List<String> bfs(Grafo grafo, String origenId) {
        int origen = grafo.indiceDe(origenId);
        boolean[] visitado = new boolean[grafo.cantidadVertices()];
        List<String> orden = new ArrayList<>();
        Deque<Integer> cola = new ArrayDeque<>();

        visitado[origen] = true; // marcar AL ENCOLAR
        cola.add(origen);

        while (!cola.isEmpty()) {
            int u = cola.poll();
            orden.add(grafo.idDe(u));
            for (Grafo.Arista a : grafo.vecinos(u)) {
                if (!visitado[a.destino()]) {
                    visitado[a.destino()] = true;
                    cola.add(a.destino());
                }
            }
        }
        return orden;
    }

    /**
     * BFS POR NIVELES con varios origenes a la vez (BFS multi-fuente) y
     * profundidad maxima k.
     *
     * USO EN EL JUEGO: "que territorios puedo alcanzar en k turnos desde mi
     * frontera". Todos los territorios propios arrancan en el nivel 0 y cada
     * nivel es una conquista mas. Como se conquista a lo sumo un territorio por
     * turno, el nivel de un territorio es la cantidad minima de turnos que
     * hacen falta para llegar a el. Esto es exactamente lo que BFS garantiza:
     * el primer nivel en el que aparece un vertice es su distancia en
     * CANTIDAD DE ARISTAS (no en costo: para eso esta Dijkstra).
     *
     * Devuelve id -> nivel, en el orden en que BFS los descubre. Los vertices
     * con nivel mayor a k no se incluyen ni se expanden (corte por nivel).
     *
     * Complejidad: O(V + E). Cada vertice entra a la cola a lo sumo una vez.
     * Espacio: O(V) por nivel[] y la cola.
     * Estructura: cola FIFO + int[] nivel (que tambien hace de visitado).
     */
    public Map<String, Integer> bfsPorNiveles(Grafo grafo, Collection<String> origenesIds, int k) {
        if (k < 0) {
            throw new IllegalArgumentException("La profundidad k no puede ser negativa: " + k);
        }
        int n = grafo.cantidadVertices();
        int[] nivel = new int[n];
        for (int i = 0; i < n; i++) {
            nivel[i] = -1; // -1 = no visitado
        }
        Deque<Integer> cola = new ArrayDeque<>();
        Map<String, Integer> resultado = new LinkedHashMap<>();

        for (String id : origenesIds) {
            int o = grafo.indiceDe(id);
            if (nivel[o] == -1) {
                nivel[o] = 0;            // todos los origenes en el nivel 0
                cola.add(o);
                resultado.put(id, 0);
            }
        }

        while (!cola.isEmpty()) {
            int u = cola.poll();
            if (nivel[u] == k) {
                continue; // corte por nivel: no se expande mas alla de k
            }
            for (Grafo.Arista a : grafo.vecinos(u)) {
                int v = a.destino();
                if (nivel[v] == -1) {
                    nivel[v] = nivel[u] + 1; // marcar AL ENCOLAR
                    cola.add(v);
                    resultado.put(grafo.idDe(v), nivel[v]);
                }
            }
        }
        return resultado;
    }

    /**
     * COMPONENTES CONEXAS con DFS (grafo NO dirigido).
     *
     * USO EN EL JUEGO: detectar si el imperio de un jugador quedo partido en
     * dos. Se arma el subgrafo con los territorios propios y se cuentan las
     * componentes: si hay mas de una, los territorios que no estan en la
     * componente de la capital quedan AISLADOS (sin abastecimiento).
     *
     * IDEA: se lanza un DFS desde cada vertice todavia no visitado. Cada DFS
     * pinta exactamente una componente, porque DFS visita todo lo alcanzable
     * desde su origen y nada mas.
     *
     * Complejidad: O(V + E). Aunque haya varios DFS, cada vertice se visita una
     * sola vez en total y cada lista de adyacencia se recorre una sola vez.
     * Espacio: O(V).
     * Estructura: boolean[] visitado + pila de llamadas (el DFS recursivo).
     */
    public List<List<String>> componentesConexas(Grafo grafo) {
        if (grafo.esDirigido()) {
            throw new IllegalArgumentException(
                    "Las componentes conexas se calculan sobre un grafo NO dirigido.");
        }
        boolean[] visitado = new boolean[grafo.cantidadVertices()];
        List<List<String>> componentes = new ArrayList<>();
        for (int v = 0; v < grafo.cantidadVertices(); v++) {
            if (!visitado[v]) {
                List<String> componente = new ArrayList<>();
                dfsRecursivo(grafo, v, visitado, componente); // pinta toda la componente de v
                componentes.add(componente);
            }
        }
        return componentes;
    }
}
