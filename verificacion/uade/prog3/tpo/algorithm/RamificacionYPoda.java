package uade.prog3.tpo.algorithm;

import uade.prog3.tpo.model.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UNIDAD: Branch and Bound (clase 12)       PUNTAJE: 1 punto
 *
 * Es backtracking mas una cota. La diferencia con la clase Backtracking es el
 * criterio de poda: alli se podaba por VIABILIDAD (esta rama viola una
 * restriccion), aca se poda por OPTIMALIDAD (esta rama no puede mejorar lo que
 * ya tengo).
 */
public class RamificacionYPoda {

    /**
     * Asignacion de items a contenedores.
     *
     * NOTA: se agregaron nodosExplorados y nodosPodados al record original del
     * scaffold porque el hito 11 pide reportar los nodos podados. El controller
     * no necesita cambios: sigue devolviendo el record tal cual.
     */
    public record Asignacion(Map<String, List<Item>> porContenedor, double cargaMaxima,
                             int nodosExplorados, int nodosPodados, double cotaInicial) { }

    private final Ordenamiento ordenamiento;

    public RamificacionYPoda(Ordenamiento ordenamiento) {
        this.ordenamiento = ordenamiento;
    }

    /** Estado mutable de la busqueda, para no arrastrar diez parametros. */
    private static final class Busqueda {
        double[] carga;
        int[] asignacion;
        int[] mejorAsignacion;
        double mejorMakespan;
        double sumaTotal;
        int explorados;
        int podados;
    }

    /**
     * Repartir items entre k contenedores identicos minimizando la carga del
     * contenedor mas cargado (problema de makespan, P||Cmax).
     *
     * RAMIFICAR: cada item puede ir a cualquiera de los k contenedores, asi que
     * el arbol tiene k^n hojas. Se ramifica item por item, en orden de peso
     * decreciente: colocar primero los items grandes hace que el makespan
     * parcial crezca rapido y la poda empiece a cortar mucho antes.
     *
     * ACOTAR: la cota inferior del makespan alcanzable desde el nodo actual es
     *     cota = max( carga maxima actual , suma de TODOS los pesos / k )
     * Los dos terminos son optimistas por separado:
     *   - la carga maxima actual ya esta puesta y solo puede crecer
     *   - ni el reparto perfecto puede bajar de la media, porque el trabajo
     *     total es fijo y hay k contenedores
     * Al ser una cota INFERIOR verdadera, si ya es >= al mejor makespan
     * conocido, ninguna hoja de esa rama puede mejorarlo: podar es seguro.
     * Si la cota fuera pesimista (sobrestimara el potencial de la rama) se
     * podarian ramas que contenian el optimo y el resultado dejaria de serlo.
     *
     * PODAR: dos cortes.
     *   1. por cota, el descripto arriba
     *   2. por simetria: los contenedores son identicos, asi que poner el
     *      primer item en el contenedor 0 o en el 5 da soluciones equivalentes.
     *      Se permite usar un contenedor vacio solo si es el primero vacio.
     *      Sin esto se exploran k! copias del mismo reparto.
     *
     * COTA INICIAL: se arranca con una solucion LPT (Longest Processing Time:
     * items de mayor a menor, cada uno al contenedor menos cargado). Da un
     * makespan razonable de entrada, con lo cual la poda por cota funciona
     * desde el primer nodo en vez de tener que descubrir una solucion primero.
     *
     * Complejidad: O(k^n) en el peor caso, muchisimo menor con la poda.
     * Espacio: O(n + k) mas la pila de recursion, de profundidad n.
     */
    public Asignacion repartir(List<Item> items, int cantidadContenedores) {
        if (cantidadContenedores < 1) {
            throw new IllegalArgumentException(
                    "La cantidad de contenedores debe ser al menos 1: " + cantidadContenedores);
        }
        if (items.isEmpty()) {
            return new Asignacion(contenedoresVacios(cantidadContenedores), 0.0, 0, 0, 0.0);
        }
        for (Item i : items) {
            if (i.getPeso() < 0) {
                throw new IllegalArgumentException(
                        "El item " + i.getId() + " tiene peso negativo: " + i.getPeso());
            }
        }

        // Orden decreciente por peso, con el MergeSort propio.
        Comparator<Item> porPesoDescendente = (a, b) -> Double.compare(b.getPeso(), a.getPeso());
        List<Item> ordenados = ordenamiento.mergeSort(items, porPesoDescendente);

        Busqueda b = new Busqueda();
        b.carga = new double[cantidadContenedores];
        b.asignacion = new int[ordenados.size()];
        b.mejorAsignacion = new int[ordenados.size()];
        b.sumaTotal = 0.0;
        for (Item i : ordenados) {
            b.sumaTotal += i.getPeso();
        }

        b.mejorMakespan = solucionInicialLpt(ordenados, cantidadContenedores, b.mejorAsignacion);
        double cotaInicial = b.mejorMakespan;

        ramificar(ordenados, 0, cantidadContenedores, b);

        Map<String, List<Item>> porContenedor = contenedoresVacios(cantidadContenedores);
        for (int i = 0; i < ordenados.size(); i++) {
            porContenedor.get("contenedor-" + b.mejorAsignacion[i]).add(ordenados.get(i));
        }
        return new Asignacion(porContenedor, b.mejorMakespan, b.explorados, b.podados, cotaInicial);
    }

    private void ramificar(List<Item> items, int indice, int k, Busqueda b) {
        b.explorados++;

        if (indice == items.size()) { // hoja: todos los items colocados
            double makespan = 0.0;
            for (double c : b.carga) {
                makespan = Math.max(makespan, c);
            }
            if (makespan < b.mejorMakespan) {
                b.mejorMakespan = makespan;
                System.arraycopy(b.asignacion, 0, b.mejorAsignacion, 0, items.size());
            }
            return;
        }

        double cargaMaximaActual = 0.0;
        for (double c : b.carga) {
            cargaMaximaActual = Math.max(cargaMaximaActual, c);
        }

        // COTA INFERIOR optimista del makespan alcanzable desde aca
        double cota = Math.max(cargaMaximaActual, b.sumaTotal / k);
        if (cota >= b.mejorMakespan) {
            b.podados++;
            return; // ninguna hoja de esta rama puede mejorar lo que ya tengo
        }

        Item item = items.get(indice);
        boolean yaProbeUnContenedorVacio = false;

        for (int c = 0; c < k; c++) {
            // PODA POR SIMETRIA: los contenedores vacios son intercambiables
            if (b.carga[c] == 0.0) {
                if (yaProbeUnContenedorVacio) {
                    continue;
                }
                yaProbeUnContenedorVacio = true;
            }
            // corte local: si poner el item aca ya empata o supera el mejor, no sirve
            if (b.carga[c] + item.getPeso() >= b.mejorMakespan) {
                b.podados++;
                continue;
            }

            b.carga[c] += item.getPeso();
            b.asignacion[indice] = c;

            ramificar(items, indice + 1, k, b);

            b.carga[c] -= item.getPeso(); // retroceso
        }
    }

    /**
     * LPT: items de mayor a menor, cada uno al contenedor menos cargado.
     * Es una heuristica greedy con garantia conocida de 4/3 del optimo.
     * Aca se usa solo para tener una cota superior inicial.
     */
    private double solucionInicialLpt(List<Item> ordenados, int k, int[] asignacionSalida) {
        double[] carga = new double[k];
        for (int i = 0; i < ordenados.size(); i++) {
            int menos = 0;
            for (int c = 1; c < k; c++) {
                if (carga[c] < carga[menos]) {
                    menos = c;
                }
            }
            carga[menos] += ordenados.get(i).getPeso();
            asignacionSalida[i] = menos;
        }
        double makespan = 0.0;
        for (double c : carga) {
            makespan = Math.max(makespan, c);
        }
        return makespan;
    }

    private Map<String, List<Item>> contenedoresVacios(int k) {
        Map<String, List<Item>> mapa = new LinkedHashMap<>();
        for (int c = 0; c < k; c++) {
            mapa.put("contenedor-" + c, new ArrayList<>());
        }
        return mapa;
    }
}
