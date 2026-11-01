package uade.prog3.tpo.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

/**
 * UNIDAD: Divide y Conquista (clase 2) — arboles binarios de busqueda,
 * en su version AUTOBALANCEADA (AVL).
 *
 * USO EN EL JUEGO: "los k territorios mas valiosos dentro de un rango de
 * defensa". Se cargan los territorios que no son del jugador con clave =
 * defensa efectiva (tropas + bonus de terreno). La consulta por rango
 * [0, tropas que puedo mandar - 1] devuelve los que se pueden conquistar, y de
 * esos se eligen los k de mayor produccion.
 *
 * POR QUE AVL Y NO UN ABB COMUN: si los territorios se insertan ya ordenados
 * por defensa (algo muy comun), un ABB comun degenera en una lista y la
 * consulta pasa a ser O(n). El AVL mantiene |altura(izq) - altura(der)| <= 1
 * en cada nodo con rotaciones, y eso garantiza altura O(log n).
 *
 * Claves repetidas: se permiten (dos territorios pueden tener la misma
 * defensa). Al insertar, los iguales van a la derecha; despues de rotar el
 * invariante que vale es izq <= nodo <= der, y la consulta por rango esta
 * escrita con <= de los dos lados para respetarlo.
 *
 * No es un @Component porque es una ESTRUCTURA de datos: se crea una por
 * consulta, con los datos del momento.
 */
public class ArbolAVL<T> {

    private final class NodoAvl {
        final double clave;
        final T valor;
        int altura = 1;
        NodoAvl izq;
        NodoAvl der;

        NodoAvl(double clave, T valor) {
            this.clave = clave;
            this.valor = valor;
        }
    }

    private NodoAvl raiz;
    private int tamanio;
    private int rotaciones;

    /**
     * Inserta y rebalancea en el camino de vuelta de la recursion.
     * Complejidad: O(log n) — se baja por un camino de altura O(log n) y en
     * cada nivel se hace O(1) trabajo (a lo sumo dos rotaciones en total).
     */
    public void insertar(double clave, T valor) {
        raiz = insertar(raiz, clave, valor);
        tamanio++;
    }

    private NodoAvl insertar(NodoAvl n, double clave, T valor) {
        if (n == null) {
            return new NodoAvl(clave, valor);
        }
        if (clave < n.clave) {
            n.izq = insertar(n.izq, clave, valor);
        } else {
            n.der = insertar(n.der, clave, valor); // iguales a la derecha
        }
        actualizarAltura(n);
        return rebalancear(n);
    }

    /**
     * Los cuatro casos de desbalance:
     *   izq-izq  -> rotacion simple a derecha
     *   izq-der  -> rotacion a izquierda del hijo + rotacion a derecha
     *   der-der  -> rotacion simple a izquierda
     *   der-izq  -> rotacion a derecha del hijo + rotacion a izquierda
     */
    private NodoAvl rebalancear(NodoAvl n) {
        int balance = altura(n.izq) - altura(n.der);
        if (balance > 1) {                                 // cargado a la izquierda
            if (altura(n.izq.izq) < altura(n.izq.der)) {
                n.izq = rotarIzquierda(n.izq);             // caso izq-der
            }
            return rotarDerecha(n);
        }
        if (balance < -1) {                                // cargado a la derecha
            if (altura(n.der.der) < altura(n.der.izq)) {
                n.der = rotarDerecha(n.der);               // caso der-izq
            }
            return rotarIzquierda(n);
        }
        return n;
    }

    private NodoAvl rotarDerecha(NodoAvl y) {
        NodoAvl x = y.izq;
        y.izq = x.der;
        x.der = y;
        actualizarAltura(y);
        actualizarAltura(x);
        rotaciones++;
        return x;
    }

    private NodoAvl rotarIzquierda(NodoAvl x) {
        NodoAvl y = x.der;
        x.der = y.izq;
        y.izq = x;
        actualizarAltura(x);
        actualizarAltura(y);
        rotaciones++;
        return y;
    }

    private int altura(NodoAvl n) {
        return n == null ? 0 : n.altura;
    }

    private void actualizarAltura(NodoAvl n) {
        n.altura = 1 + Math.max(altura(n.izq), altura(n.der));
    }

    /**
     * CONSULTA POR RANGO: todos los valores con clave en [desde, hasta], en
     * orden de clave.
     *
     * Se recorre in-order pero PODANDO: si la clave del nodo es menor que
     * 'desde', todo su subarbol izquierdo tambien lo es y no se baja; lo mismo
     * con 'hasta' y el subarbol derecho.
     *
     * Complejidad: O(log n + m), con m la cantidad de resultados. El log n es
     * el camino hasta el borde del rango; el resto es proporcional a lo que se
     * devuelve.
     */
    public List<T> rango(double desde, double hasta) {
        List<T> salida = new ArrayList<>();
        rango(raiz, desde, hasta, salida);
        return salida;
    }

    private void rango(NodoAvl n, double desde, double hasta, List<T> salida) {
        if (n == null) {
            return;
        }
        if (desde <= n.clave) {
            rango(n.izq, desde, hasta, salida);   // puede haber claves en rango a la izquierda
        }
        if (desde <= n.clave && n.clave <= hasta) {
            salida.add(n.valor);
        }
        if (n.clave <= hasta) {
            rango(n.der, desde, hasta, salida);   // puede haber claves en rango a la derecha
        }
    }

    /**
     * Los k MEJORES (mayor 'valor') dentro del rango de claves [desde, hasta].
     *
     * Consulta por rango + un heap de MINIMOS de tamano k: se recorren los m
     * resultados y el heap se queda siempre con los k mayores vistos (si llega
     * uno mejor que el peor del heap, se saca el peor). Al final se vacia el
     * heap y se invierte para devolverlos de mayor a menor.
     *
     * Complejidad: O(log n + m log k). Con k chico es practicamente lineal en m.
     */
    public List<T> kMejoresEnRango(double desde, double hasta, int k, ToDoubleFunction<T> valor) {
        if (k <= 0) {
            return new ArrayList<>();
        }
        PriorityQueue<T> heap = new PriorityQueue<>(
                (a, b) -> Double.compare(valor.applyAsDouble(a), valor.applyAsDouble(b)));
        for (T t : rango(desde, hasta)) {
            if (heap.size() < k) {
                heap.add(t);
            } else if (valor.applyAsDouble(t) > valor.applyAsDouble(heap.peek())) {
                heap.poll();
                heap.add(t);
            }
        }
        List<T> deMenorAMayor = new ArrayList<>();
        while (!heap.isEmpty()) {
            deMenorAMayor.add(heap.poll());
        }
        List<T> salida = new ArrayList<>();
        for (int i = deMenorAMayor.size() - 1; i >= 0; i--) {
            salida.add(deMenorAMayor.get(i));
        }
        return salida;
    }

    /** Recorrido in-order completo: devuelve los valores ordenados por clave. */
    public List<T> enOrden() {
        List<T> salida = new ArrayList<>();
        enOrden(raiz, salida);
        return salida;
    }

    private void enOrden(NodoAvl n, List<T> salida) {
        if (n != null) {
            enOrden(n.izq, salida);
            salida.add(n.valor);
            enOrden(n.der, salida);
        }
    }

    /** Verifica el invariante AVL en todo el arbol. Lo usan los tests. */
    public boolean estaBalanceado() {
        return balanceado(raiz);
    }

    private boolean balanceado(NodoAvl n) {
        if (n == null) {
            return true;
        }
        return Math.abs(altura(n.izq) - altura(n.der)) <= 1 && balanceado(n.izq) && balanceado(n.der);
    }

    public int altura() {
        return altura(raiz);
    }

    public int tamanio() {
        return tamanio;
    }

    public int rotaciones() {
        return rotaciones;
    }
}
