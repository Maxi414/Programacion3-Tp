package uade.prog3.tpo.algorithm;

import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * UNIDAD: Grafos II (clase 5)
 * PUNTAJE: parte de los 3 puntos de "Dijkstra, Prim, Kruskal"
 *
 * ATENCION: el MST solo tiene sentido sobre un grafo NO DIRIGIDO y CONEXO.
 * El controller ya lo carga con grafoService.cargar(false).
 */
public class ArbolRecubrimiento {

    /** Una arista del arbol resultante, con ids de negocio. */
    public record AristaMst(String origen, String destino, double costo) { }

    /** Arbol de recubrimiento minimo completo. */
    public record Mst(List<AristaMst> aristas, double costoTotal) { }

    /** Entrada de la cola de Prim: la arista mas barata que cruza el corte. */
    private record Candidata(int vertice, int desde, double costo) { }

    /**
     * Prim: hace crecer UN solo arbol desde un vertice inicial, tomando en cada
     * paso la arista mas barata que cruza de dentro hacia afuera.
     *
     * INVARIANTE (propiedad del corte): en todo momento el conjunto de aristas
     * elegidas es subconjunto de algun MST. La arista minima que cruza el corte
     * entre lo que esta adentro y lo que esta afuera siempre pertenece a algun
     * MST, y por eso tomarla nunca se lamenta.
     *
     * Complejidad: O(E log V) con cola de prioridad. La alternativa con
     * busqueda lineal del minimo es O(V^2), que conviene solo en grafos densos
     * (E ~ V^2). Aca esta implementada la version con heap.
     * Espacio: O(V + E).
     *
     * Estructura: PriorityQueue + arreglos enMst[] y costoMinimo[].
     */
    public Mst prim(Grafo grafo, String origenId) {
        if (grafo.esDirigido()) {
            throw new IllegalArgumentException(
                    "Prim requiere un grafo NO dirigido. Cargalo con grafoService.cargar(false).");
        }
        int origen = grafo.indiceDe(origenId);
        int n = grafo.cantidadVertices();

        boolean[] enMst = new boolean[n];
        List<AristaMst> aristas = new ArrayList<>();
        double costoTotal = 0.0;

        PriorityQueue<Candidata> cola = new PriorityQueue<>((x, y) -> Double.compare(x.costo(), y.costo()));
        cola.add(new Candidata(origen, -1, 0.0));

        while (!cola.isEmpty() && aristas.size() < n - 1) {
            Candidata c = cola.poll();
            if (enMst[c.vertice()]) {
                continue; // ya lo agrego una arista mas barata
            }
            enMst[c.vertice()] = true;

            if (c.desde() != -1) { // el origen no aporta arista
                aristas.add(new AristaMst(grafo.idDe(c.desde()), grafo.idDe(c.vertice()), c.costo()));
                costoTotal += c.costo();
            }

            for (Grafo.Arista a : grafo.vecinos(c.vertice())) {
                if (!enMst[a.destino()]) {
                    cola.add(new Candidata(a.destino(), c.vertice(), a.costo()));
                }
            }
        }

        if (aristas.size() != n - 1) {
            throw new IllegalStateException(
                    "El grafo no es conexo: Prim llego a " + aristas.size()
                            + " aristas y un MST de " + n + " vertices necesita " + (n - 1)
                            + ". No existe arbol de recubrimiento.");
        }
        return new Mst(aristas, costoTotal);
    }

    /**
     * Kruskal: ordena TODAS las aristas por costo y las agrega salteando las
     * que formarian ciclo.
     *
     * INVARIANTE: el conjunto elegido nunca tiene ciclos, porque una arista se
     * descarta exactamente cuando sus dos extremos ya estan en la misma
     * componente. Ese chequeo es find(u) == find(v).
     *
     * Complejidad: O(E log E) dominada por el orden de las aristas. Las
     * operaciones de Union-Find con compresion de caminos y union por rango
     * son O(alfa(V)), practicamente constantes.
     * Espacio: O(V + E).
     *
     * Estructura: PriorityQueue sobre las aristas + Union-Find propio.
     * Se usa un heap en lugar de Collections.sort para no apoyarse en una
     * libreria de ordenamiento, que el enunciado prohibe.
     *
     * Prim vs Kruskal: Prim conviene en grafos DENSOS (crece un solo arbol y
     * mira solo la frontera); Kruskal conviene en grafos RALOS, donde ordenar
     * pocas aristas es barato.
     */
    public Mst kruskal(Grafo grafo) {
        if (grafo.esDirigido()) {
            throw new IllegalArgumentException(
                    "Kruskal requiere un grafo NO dirigido. Cargalo con grafoService.cargar(false).");
        }
        int n = grafo.cantidadVertices();

        // En un grafo no dirigido cada arista esta almacenada dos veces (u->v y
        // v->u). Se toma solo u < v para no procesarla por duplicado.
        PriorityQueue<Candidata> aristasOrdenadas =
                new PriorityQueue<>((x, y) -> Double.compare(x.costo(), y.costo()));
        for (int u = 0; u < n; u++) {
            for (Grafo.Arista a : grafo.vecinos(u)) {
                if (u < a.destino()) {
                    aristasOrdenadas.add(new Candidata(a.destino(), u, a.costo()));
                }
            }
        }

        UnionFind uf = new UnionFind(n);
        List<AristaMst> aristas = new ArrayList<>();
        double costoTotal = 0.0;

        while (!aristasOrdenadas.isEmpty() && aristas.size() < n - 1) {
            Candidata c = aristasOrdenadas.poll();
            int u = c.desde();
            int v = c.vertice();
            if (uf.find(u) == uf.find(v)) {
                continue; // formaria ciclo
            }
            uf.union(u, v);
            aristas.add(new AristaMst(grafo.idDe(u), grafo.idDe(v), c.costo()));
            costoTotal += c.costo();
        }

        if (aristas.size() != n - 1) {
            throw new IllegalStateException(
                    "El grafo no es conexo: Kruskal llego a " + aristas.size()
                            + " aristas y un MST de " + n + " vertices necesita " + (n - 1) + ".");
        }
        return new Mst(aristas, costoTotal);
    }

    /**
     * Union-Find (conjuntos disjuntos) implementado a mano.
     *
     * Dos optimizaciones, y hay que saber explicarlas:
     *  - compresion de caminos: al buscar la raiz, se cuelgan todos los nodos
     *    del camino directamente de ella, achatando el arbol
     *  - union por rango: siempre se cuelga el arbol mas bajo del mas alto,
     *    para que la profundidad no crezca
     * Juntas dan un costo amortizado de O(alfa(n)), inversa de Ackermann,
     * que para cualquier n real es menor que 5.
     */
    private static class UnionFind {
        private final int[] padre;
        private final int[] rango;

        UnionFind(int n) {
            padre = new int[n];
            rango = new int[n];
            for (int i = 0; i < n; i++) {
                padre[i] = i; // cada vertice arranca solo en su componente
            }
        }

        int find(int x) {
            if (padre[x] != x) {
                padre[x] = find(padre[x]); // compresion de caminos
            }
            return padre[x];
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return;
            }
            if (rango[ra] < rango[rb]) {          // union por rango
                padre[ra] = rb;
            } else if (rango[ra] > rango[rb]) {
                padre[rb] = ra;
            } else {
                padre[rb] = ra;
                rango[ra]++;
            }
        }
    }
}
