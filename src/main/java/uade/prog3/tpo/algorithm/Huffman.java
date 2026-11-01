package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * UNIDAD: Greedy (clase 3) — codigo de Huffman.
 *
 * USO EN EL JUEGO: comprimir el registro (log) de la partida para guardar el
 * replay. El log usa un alfabeto chico y muy desbalanceado (muchos digitos,
 * separadores y letras de los ids de territorio, casi nada de lo demas), que
 * es justo el caso donde Huffman gana mucho contra 8 bits fijos por caracter.
 *
 * POR QUE ES GREEDY: en cada paso junta los DOS arboles de menor frecuencia
 * en uno nuevo cuya frecuencia es la suma, y nunca revisa esa decision. Es
 * uno de los pocos greedy que SI da el optimo: el codigo resultante es el
 * codigo prefijo de longitud media minima (los simbolos raros quedan mas
 * profundos, con codigos mas largos, y los frecuentes quedan arriba).
 *
 * PROPIEDAD PREFIJO: ningun codigo es prefijo de otro, porque los simbolos
 * estan solo en las HOJAS. Por eso se puede decodificar sin separadores.
 */
@Component
public class Huffman {

    /** Nodo del arbol: las hojas tienen simbolo, los internos no. */
    public record NodoHuffman(Character simbolo, long frecuencia, NodoHuffman izq, NodoHuffman der,
                              int orden) {
        boolean esHoja() {
            return izq == null && der == null;
        }
    }

    /**
     * @param codigos         simbolo -> codigo binario
     * @param bits            el texto codificado como cadena de '0' y '1'
     * @param bitsOriginales  8 bits por caracter (codificacion de ancho fijo)
     * @param bitsComprimidos largo de 'bits'
     * @param tasaCompresion  bitsComprimidos / bitsOriginales (menos es mejor)
     */
    public record Compresion(Map<Character, String> codigos, String bits, NodoHuffman arbol,
                             long bitsOriginales, long bitsComprimidos, double tasaCompresion) { }

    /**
     * Construye el arbol de Huffman y codifica el texto.
     *
     * PASOS:
     *  1. contar frecuencias: O(n) sobre el texto
     *  2. una hoja por simbolo distinto en una cola de prioridad por frecuencia
     *  3. GREEDY: mientras quede mas de un arbol, sacar los dos de menor
     *     frecuencia y juntarlos (izquierda = '0', derecha = '1')
     *  4. recorrer el arbol para armar la tabla de codigos y codificar
     *
     * Complejidad: O(n + s log s), con n el largo del texto y s la cantidad de
     * simbolos distintos (s - 1 fusiones, cada una con operaciones de heap).
     * Espacio: O(s) para el arbol y la tabla, O(n) para la salida.
     * Estructura: PriorityQueue (heap binario) de nodos.
     *
     * Desempate determinista: a igual frecuencia sale primero el nodo creado
     * antes ('orden'). Asi el mismo log produce siempre el mismo codigo.
     */
    public Compresion comprimir(String texto) {
        if (texto == null || texto.isEmpty()) {
            throw new IllegalArgumentException("No hay nada para comprimir: el texto esta vacio.");
        }

        // 1. frecuencias (TreeMap: orden alfabetico estable para los desempates)
        Map<Character, Long> frecuencias = new TreeMap<>();
        for (int i = 0; i < texto.length(); i++) {
            frecuencias.merge(texto.charAt(i), 1L, Long::sum);
        }

        // 2. una hoja por simbolo
        PriorityQueue<NodoHuffman> cola = new PriorityQueue<>((a, b) -> {
            int c = Long.compare(a.frecuencia(), b.frecuencia());
            return c != 0 ? c : Integer.compare(a.orden(), b.orden());
        });
        int orden = 0;
        for (Map.Entry<Character, Long> e : frecuencias.entrySet()) {
            cola.add(new NodoHuffman(e.getKey(), e.getValue(), null, null, orden++));
        }

        // 3. eleccion voraz: fusionar siempre los dos menos frecuentes
        while (cola.size() > 1) {
            NodoHuffman a = cola.poll();
            NodoHuffman b = cola.poll();
            cola.add(new NodoHuffman(null, a.frecuencia() + b.frecuencia(), a, b, orden++));
        }
        NodoHuffman raiz = cola.poll();

        // 4. tabla de codigos y codificacion
        Map<Character, String> codigos = new LinkedHashMap<>();
        if (raiz.esHoja()) {
            codigos.put(raiz.simbolo(), "0"); // un solo simbolo: igual necesita 1 bit
        } else {
            armarCodigos(raiz, "", codigos);
        }
        StringBuilder bits = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            bits.append(codigos.get(texto.charAt(i)));
        }

        long originales = 8L * texto.length();
        long comprimidos = bits.length();
        double tasa = Math.round(10000.0 * comprimidos / originales) / 10000.0;
        return new Compresion(codigos, bits.toString(), raiz, originales, comprimidos, tasa);
    }

    private void armarCodigos(NodoHuffman n, String prefijo, Map<Character, String> codigos) {
        if (n.esHoja()) {
            codigos.put(n.simbolo(), prefijo);
            return;
        }
        armarCodigos(n.izq(), prefijo + "0", codigos);
        armarCodigos(n.der(), prefijo + "1", codigos);
    }

    /**
     * Decodifica bajando por el arbol: '0' a la izquierda, '1' a la derecha,
     * y al llegar a una hoja se emite su simbolo y se vuelve a la raiz.
     * Funciona sin separadores gracias a la propiedad prefijo.
     * Complejidad: O(b), con b la cantidad de bits.
     */
    public String descomprimir(String bits, NodoHuffman raiz) {
        StringBuilder salida = new StringBuilder();
        if (raiz.esHoja()) { // caso de un solo simbolo
            for (int i = 0; i < bits.length(); i++) {
                salida.append(raiz.simbolo());
            }
            return salida.toString();
        }
        NodoHuffman actual = raiz;
        for (int i = 0; i < bits.length(); i++) {
            char b = bits.charAt(i);
            if (b != '0' && b != '1') {
                throw new IllegalArgumentException("Bit invalido en la posicion " + i + ": " + b);
            }
            actual = (b == '0') ? actual.izq() : actual.der();
            if (actual.esHoja()) {
                salida.append(actual.simbolo());
                actual = raiz;
            }
        }
        if (actual != raiz) {
            throw new IllegalArgumentException("La secuencia de bits termina en la mitad de un codigo.");
        }
        return salida.toString();
    }
}
