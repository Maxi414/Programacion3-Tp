package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * UNIDAD: Divide y Conquista (clase 2) — busqueda binaria.
 *
 * USO EN EL JUEGO: el catalogo de unidades esta ordenado por costo (se ordena
 * UNA vez con MergeSort al crear la partida). En cada fase de compra hay que
 * saber que unidades se pueden pagar con el oro del turno: son exactamente las
 * de un prefijo del catalogo, y el fin de ese prefijo se encuentra con
 * busqueda binaria. Solo esas unidades entran a la mochila.
 *
 * PRECONDICION: la lista tiene que estar ordenada de forma no decreciente por
 * la clave. Sobre una lista desordenada la busqueda binaria devuelve basura
 * sin avisar, porque descarta mitades basandose en un orden que no existe.
 */
@Component
public class BusquedaBinaria {

    /**
     * Indice del ULTIMO elemento cuya clave es <= limite, o -1 si no hay
     * ninguno ("upper bound" menos uno).
     *
     * INVARIANTE: todo lo que esta a la izquierda de 'desde' tiene clave <=
     * limite y todo lo que esta a la derecha de 'hasta' tiene clave > limite.
     * Cada vuelta descarta la mitad del rango sin violar el invariante.
     *
     * Recurrencia (version recursiva equivalente): T(n) = T(n/2) + O(1).
     * Regla practica: a=1, b=2, k=0 -> a = b^k -> O(n^0 log n) = O(log n).
     * Complejidad: O(log n) tiempo, O(1) espacio (version iterativa).
     */
    public <T> int ultimoMenorOIgual(List<T> ordenada, double limite, ToDoubleFunction<T> clave) {
        int desde = 0;
        int hasta = ordenada.size() - 1;
        int respuesta = -1;
        while (desde <= hasta) {
            int medio = desde + (hasta - desde) / 2; // evita overflow de (desde+hasta)/2
            if (clave.applyAsDouble(ordenada.get(medio)) <= limite) {
                respuesta = medio;      // candidato; puede haber uno mas a la derecha
                desde = medio + 1;
            } else {
                hasta = medio - 1;      // todo desde medio en adelante se pasa del limite
            }
        }
        return respuesta;
    }

    /**
     * Busqueda binaria clasica: indice de ALGUN elemento con clave igual a
     * 'valor', o -1 si no esta. Complejidad O(log n).
     */
    public <T> int buscar(List<T> ordenada, double valor, ToDoubleFunction<T> clave) {
        int desde = 0;
        int hasta = ordenada.size() - 1;
        while (desde <= hasta) {
            int medio = desde + (hasta - desde) / 2;
            double c = clave.applyAsDouble(ordenada.get(medio));
            if (c == valor) {
                return medio;
            }
            if (c < valor) {
                desde = medio + 1;
            } else {
                hasta = medio - 1;
            }
        }
        return -1;
    }

    /**
     * Las unidades que se pueden pagar con 'oro': el prefijo del catalogo
     * (ordenado por costo = peso) hasta el ultimo costo <= oro.
     * O(log n) para encontrar el corte + O(m) para copiar las m asequibles.
     */
    public List<Item> asequibles(List<Item> catalogoPorCosto, double oro) {
        int ultimo = ultimoMenorOIgual(catalogoPorCosto, oro, Item::getPeso);
        return new ArrayList<>(catalogoPorCosto.subList(0, ultimo + 1));
    }
}
