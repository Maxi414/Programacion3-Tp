package uade.prog3.tpo.algorithm;

import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.List;

/**
 * UNIDAD: Backtracking (clases 9 y 10)      PUNTAJE: 1 punto
 *
 * Enumerar caminos simples bajo restricciones. Es DFS con dos agregados:
 * un criterio de viabilidad que poda ramas, y el DESHACER al retroceder.
 */
public class Backtracking {

    /** Un camino simple encontrado, con su costo acumulado. */
    public record Ruta(List<String> vertices, double costoTotal) { }

    /** Comparacion de nodos explorados con poda y sin poda. */
    public record ComparacionPoda(int exploradosConPoda, int exploradosSinPoda,
                                  int rutasEncontradas, double porcentajeAhorrado) { }

    /**
     * Enumerar TODOS los caminos simples de origen a destino que no superen un
     * costo maximo ni una cantidad maxima de saltos.
     *
     * LOS TRES ELEMENTOS DEL BACKTRACKING, senalados en el codigo de abajo:
     *  1. construccion incremental: se agrega un vertice al camino parcial
     *  2. poda por viabilidad: si el costo parcial ya supera el maximo, se
     *     abandona la rama sin completar el camino. Como todos los costos son
     *     positivos, el costo solo puede crecer: seguir es inutil
     *  3. retroceso: al volver se desmarca el visitado y se saca el vertice del
     *     camino. Sin ese deshacer, el mismo vertice queda bloqueado para las
     *     ramas hermanas y se pierden soluciones validas
     *
     * Complejidad: O(V!) en el peor caso. En un grafo completo, desde cada
     * vertice se puede seguir a cualquiera de los no visitados, y la cantidad
     * de caminos simples crece factorialmente. La poda no cambia esa cota
     * teorica, solo el comportamiento practico.
     * Espacio: O(V) por el camino parcial y el arreglo de visitados, mas lo que
     * ocupen las rutas encontradas.
     */
    public List<Ruta> rutasSimples(Grafo grafo, String origenId, String destinoId,
                                   double costoMaximo, int saltosMaximos) {
        int origen = grafo.indiceDe(origenId);
        int destino = grafo.indiceDe(destinoId);
        if (saltosMaximos < 0) {
            throw new IllegalArgumentException("saltosMaximos no puede ser negativo: " + saltosMaximos);
        }

        List<Ruta> encontradas = new ArrayList<>();
        boolean[] visitado = new boolean[grafo.cantidadVertices()];
        List<Integer> caminoParcial = new ArrayList<>();
        int[] explorados = new int[1];

        visitado[origen] = true;
        caminoParcial.add(origen);
        explorar(grafo, origen, destino, 0.0, costoMaximo, saltosMaximos,
                visitado, caminoParcial, encontradas, explorados, true);

        return encontradas;
    }

    /**
     * @param podar si es false se recorre el arbol completo sin cortar por
     *              costo. Solo se usa para medir cuanto ahorra la poda.
     */
    private void explorar(Grafo grafo, int actual, int destino,
                          double costoAcumulado, double costoMaximo, int saltosMaximos,
                          boolean[] visitado, List<Integer> caminoParcial,
                          List<Ruta> encontradas, int[] explorados, boolean podar) {
        explorados[0]++;

        if (actual == destino) {
            if (costoAcumulado <= costoMaximo) {
                List<String> ids = new ArrayList<>(caminoParcial.size());
                for (int v : caminoParcial) {
                    ids.add(grafo.idDe(v));
                }
                encontradas.add(new Ruta(ids, costoAcumulado));
            }
            return; // un camino simple no vuelve a salir del destino
        }

        if (caminoParcial.size() - 1 >= saltosMaximos) {
            return; // poda por cantidad de saltos
        }

        for (Grafo.Arista a : grafo.vecinos(actual)) {
            int siguiente = a.destino();
            if (visitado[siguiente]) {
                continue; // camino SIMPLE: no se repiten vertices
            }
            double nuevoCosto = costoAcumulado + a.costo();

            // (2) PODA POR VIABILIDAD: se corta antes de bajar, no despues
            if (podar && nuevoCosto > costoMaximo) {
                continue;
            }

            visitado[siguiente] = true;          // (1) construccion incremental
            caminoParcial.add(siguiente);

            explorar(grafo, siguiente, destino, nuevoCosto, costoMaximo, saltosMaximos,
                    visitado, caminoParcial, encontradas, explorados, podar);

            caminoParcial.remove(caminoParcial.size() - 1); // (3) RETROCESO
            visitado[siguiente] = false;                    //     deshacer la decision
        }
    }

    /**
     * Corre la misma busqueda con poda y sin poda y devuelve cuantos nodos se
     * exploraron en cada caso. Es la evidencia que pide el hito 8.
     *
     * Las dos corridas encuentran EXACTAMENTE las mismas rutas: la poda por
     * costo nunca descarta una solucion valida, porque con costos positivos un
     * camino parcial que ya se paso del maximo no puede volver a bajar.
     */
    public ComparacionPoda compararPoda(Grafo grafo, String origenId, String destinoId,
                                        double costoMaximo, int saltosMaximos) {
        int conPoda = contar(grafo, origenId, destinoId, costoMaximo, saltosMaximos, true);
        int sinPoda = contar(grafo, origenId, destinoId, costoMaximo, saltosMaximos, false);
        int rutas = rutasSimples(grafo, origenId, destinoId, costoMaximo, saltosMaximos).size();
        double ahorro = sinPoda == 0 ? 0.0 : 100.0 * (sinPoda - conPoda) / sinPoda;
        return new ComparacionPoda(conPoda, sinPoda, rutas, Math.round(ahorro * 100.0) / 100.0);
    }

    private int contar(Grafo grafo, String origenId, String destinoId,
                       double costoMaximo, int saltosMaximos, boolean podar) {
        int origen = grafo.indiceDe(origenId);
        int destino = grafo.indiceDe(destinoId);
        boolean[] visitado = new boolean[grafo.cantidadVertices()];
        List<Integer> caminoParcial = new ArrayList<>();
        int[] explorados = new int[1];

        visitado[origen] = true;
        caminoParcial.add(origen);
        explorar(grafo, origen, destino, 0.0, costoMaximo, saltosMaximos,
                visitado, caminoParcial, new ArrayList<>(), explorados, podar);
        return explorados[0];
    }
}
