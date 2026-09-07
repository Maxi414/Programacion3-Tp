package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * UNIDAD: Grafos I (clase 4)          PUNTAJE: 2 puntos
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
     * costo. En el grafo de ejemplo A->C es 1 arista y cuesta 9, mientras que
     * A->B->C son 2 aristas y cuesta 7. Para minimizar costo hace falta Dijkstra.
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
}
