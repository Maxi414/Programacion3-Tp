package uade.prog3.tpo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uade.prog3.tpo.algorithm.Ordenamiento;
import uade.prog3.tpo.model.Conexion;
import uade.prog3.tpo.model.Nodo;
import uade.prog3.tpo.repository.NodoRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Carga el grafo desde Neo4j a memoria.
 *
 * INFRAESTRUCTURA: no forma parte de lo que hay que implementar.
 *
 * Decision de diseno importante para el trabajo: los algoritmos NO consultan
 * la base. Se carga el grafo una vez, se opera en memoria, y se devuelve el
 * resultado. Asi la complejidad temporal que se mide y se justifica en el
 * coloquio es la del algoritmo, no la de la red ni la del motor de base.
 *
 * ORDEN CANONICO: Neo4j no garantiza en que orden devuelve los nodos ni las
 * relaciones de cada nodo, y ese orden decide el orden de las listas de
 * adyacencia (y con eso el orden de visita de BFS y DFS, y el escenario que
 * arma el backtracking). Para que los resultados sean reproducibles, los
 * nodos se ordenan por id y las conexiones de cada uno por id de destino, con
 * el MergeSort propio.
 */
@Service
public class GrafoService {

    private static final Logger log = LoggerFactory.getLogger(GrafoService.class);

    private final NodoRepository nodoRepository;
    private final Ordenamiento ordenamiento;

    public GrafoService(NodoRepository nodoRepository, Ordenamiento ordenamiento) {
        this.nodoRepository = nodoRepository;
        this.ordenamiento = ordenamiento;
    }

    /**
     * Todos los nodos de la base, en orden canonico: por id, y las conexiones
     * de cada nodo por id de destino. Una sola consulta a la base.
     */
    public List<Nodo> nodosOrdenados() {
        List<Nodo> nodos = ordenamiento.mergeSort(nodoRepository.findAll(), Comparator.comparing(Nodo::getId));
        for (Nodo n : nodos) {
            n.setConexiones(ordenamiento.mergeSort(n.getConexiones(),
                    Comparator.comparing((Conexion c) -> c.getDestino().getId())));
        }
        return nodos;
    }

    /**
     * @param dirigido true si las relaciones CONECTA tienen sentido unico.
     *                 En el juego las rutas se recorren en los dos sentidos,
     *                 asi que los endpoints cargan el grafo NO dirigido.
     *                 Para el arbol de recubrimiento minimo (Prim y Kruskal)
     *                 el grafo DEBE cargarse como NO dirigido.
     */
    public Grafo cargar(boolean dirigido) {
        return construir(nodosOrdenados(), dirigido);
    }

    /** Arma el grafo en memoria a partir de nodos ya leidos. O(V + E). */
    public Grafo construir(List<Nodo> nodos, boolean dirigido) {
        Grafo grafo = new Grafo(dirigido);

        // 1) primero todos los vertices, para que queden en el grafo
        //    incluso los que no tienen ninguna arista
        for (Nodo n : nodos) {
            grafo.agregarVertice(n.getId());
        }

        // 2) despues las aristas, con su costo
        for (Nodo n : nodos) {
            for (Conexion c : n.getConexiones()) {
                grafo.agregarArista(n.getId(), c.getDestino().getId(), c.getCosto());
            }
        }

        log.debug("Grafo cargado: {} vertices, {} aristas, dirigido={}",
                grafo.cantidadVertices(), grafo.cantidadAristas(), dirigido);
        return grafo;
    }
}
