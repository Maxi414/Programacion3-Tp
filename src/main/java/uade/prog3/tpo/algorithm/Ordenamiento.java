package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * UNIDAD: Divide y Conquista (clase 2)     PUNTAJE: 1 punto
 *
 * IMPORTANTE: no se usa Collections.sort ni Arrays.sort en ningun lado.
 * El punto se asigna por implementar el algoritmo, no por llamarlo.
 *
 * Los dos metodos son GENERICOS: en el juego se usan para ordenar unidades
 * del catalogo (por costo, poder o ratio), territorios (por valor
 * estrategico o por nivel de amenaza) y jugadas candidatas de la IA.
 */
@Component
public class Ordenamiento {

    /**
     * QuickSort sobre una lista cualquiera.
     *
     * ESTRATEGIA DE PIVOTE: mediana de tres (primero, medio, ultimo). Se elige
     * asi porque con pivote fijo (por ejemplo, siempre el ultimo) una lista ya
     * ordenada produce particiones de tamano 0 y n-1, que es exactamente el
     * peor caso O(n^2). La mediana de tres lo evita en las entradas ordenadas
     * o casi ordenadas, que son las mas frecuentes en la practica.
     *
     * Complejidad: O(n log n) en promedio, O(n^2) en el peor caso.
     * El peor caso ocurre cuando el pivote resulta ser sistematicamente el
     * minimo o el maximo del subarreglo: la recursion se vuelve lineal y da
     * T(n) = T(n-1) + O(n) = O(n^2).
     * Espacio: O(log n) promedio por la pila de recursion. La copia inicial
     * agrega O(n) porque no se modifica la lista de entrada.
     *
     * Particion usada: Lomuto. NO es estable.
     */
    public <T> List<T> quickSort(List<T> items, Comparator<? super T> criterio) {
        List<T> copia = new ArrayList<>(items); // no se toca la lista de entrada
        quickSortRec(copia, 0, copia.size() - 1, criterio);
        return copia;
    }

    private <T> void quickSortRec(List<T> lista, int desde, int hasta, Comparator<? super T> criterio) {
        if (desde >= hasta) {
            return; // 0 o 1 elemento: ya esta ordenado
        }
        int p = particionar(lista, desde, hasta, criterio);
        quickSortRec(lista, desde, p - 1, criterio);
        quickSortRec(lista, p + 1, hasta, criterio);
    }

    /**
     * Particion de Lomuto. Al terminar, el pivote esta en su posicion
     * definitiva: todo lo menor o igual quedo a la izquierda y todo lo mayor
     * a la derecha. Por eso el pivote ya no entra en las llamadas recursivas.
     */
    private <T> int particionar(List<T> lista, int desde, int hasta, Comparator<? super T> criterio) {
        moverMedianaDeTresAlFinal(lista, desde, hasta, criterio);
        T pivote = lista.get(hasta);
        int i = desde - 1; // ultimo indice de la zona "menor o igual al pivote"

        for (int j = desde; j < hasta; j++) {
            if (criterio.compare(lista.get(j), pivote) <= 0) {
                i++;
                intercambiar(lista, i, j);
            }
        }
        intercambiar(lista, i + 1, hasta); // el pivote a su lugar final
        return i + 1;
    }

    private <T> void moverMedianaDeTresAlFinal(List<T> lista, int desde, int hasta, Comparator<? super T> criterio) {
        int medio = desde + (hasta - desde) / 2;
        if (criterio.compare(lista.get(medio), lista.get(desde)) < 0) {
            intercambiar(lista, desde, medio);
        }
        if (criterio.compare(lista.get(hasta), lista.get(desde)) < 0) {
            intercambiar(lista, desde, hasta);
        }
        if (criterio.compare(lista.get(hasta), lista.get(medio)) < 0) {
            intercambiar(lista, medio, hasta);
        }
        intercambiar(lista, medio, hasta); // la mediana queda como pivote
    }

    private <T> void intercambiar(List<T> lista, int i, int j) {
        T tmp = lista.get(i);
        lista.set(i, lista.get(j));
        lista.set(j, tmp);
    }

    /**
     * MergeSort sobre una lista cualquiera.
     *
     * ESTABILIDAD: en la mezcla se toma del lado izquierdo cuando los dos
     * elementos comparan igual (compare <= 0). Eso preserva el orden relativo
     * original de los elementos equivalentes, que es la definicion de estable.
     * En el juego importa: al ordenar territorios por amenaza, los empatados
     * conservan el orden del mapa y la IA es determinista.
     *
     * Complejidad: O(n log n) SIEMPRE, sin peor caso degradado, porque la
     * division es siempre por la mitad y no depende de los datos.
     * Recurrencia: T(n) = 2 T(n/2) + O(n).
     * Con la regla practica (a=2, b=2, k=1): a = b^k, o sea 2 = 2^1, entonces
     * T(n) = O(n^k log n) = O(n log n).
     * Espacio: O(n) por los arreglos auxiliares de la mezcla. Esa es la
     * desventaja frente a QuickSort, que ordena in situ.
     */
    public <T> List<T> mergeSort(List<T> items, Comparator<? super T> criterio) {
        if (items.size() <= 1) {
            return new ArrayList<>(items);
        }
        int medio = items.size() / 2;
        List<T> izquierda = mergeSort(new ArrayList<>(items.subList(0, medio)), criterio);
        List<T> derecha = mergeSort(new ArrayList<>(items.subList(medio, items.size())), criterio);
        return mezclar(izquierda, derecha, criterio);
    }

    /** Mezcla dos listas ya ordenadas en una sola, en O(n). */
    private <T> List<T> mezclar(List<T> izq, List<T> der, Comparator<? super T> criterio) {
        List<T> salida = new ArrayList<>(izq.size() + der.size());
        int i = 0;
        int j = 0;
        while (i < izq.size() && j < der.size()) {
            if (criterio.compare(izq.get(i), der.get(j)) <= 0) {
                salida.add(izq.get(i++)); // <= mantiene la estabilidad
            } else {
                salida.add(der.get(j++));
            }
        }
        while (i < izq.size()) {
            salida.add(izq.get(i++));
        }
        while (j < der.size()) {
            salida.add(der.get(j++));
        }
        return salida;
    }
}
