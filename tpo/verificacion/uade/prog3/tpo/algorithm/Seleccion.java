package uade.prog3.tpo.algorithm;

import uade.prog3.tpo.model.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * UNIDAD: Greedy (clase 3) y Programacion Dinamica (clase 6)
 * PUNTAJE: 1 punto greedy + 1 punto programacion dinamica
 *
 * Los dos metodos resuelven EL MISMO problema con tecnicas distintas.
 * Esa comparacion es lo que se evalua.
 */
public class Seleccion {

    /** Resultado de una seleccion de items bajo restriccion de capacidad. */
    public record Resultado(List<Item> elegidos, double valorTotal, double pesoTotal, String tecnica) { }

    private final Ordenamiento ordenamiento;

    /** Se reusa el MergeSort propio: el enunciado prohibe Collections.sort. */
    public Seleccion(Ordenamiento ordenamiento) {
        this.ordenamiento = ordenamiento;
    }

    /**
     * GREEDY: ordenar por ratio valor/peso descendente y tomar mientras entre.
     *
     * FUNCION OBJETIVO: maximizar la suma de valores de los items elegidos,
     * sujeto a que la suma de sus pesos no supere la capacidad.
     *
     * CRITERIO: ratio valor/peso, no valor a secas. Ordenar por valor elegiria
     * primero un item caro y pesado que bloquea la capacidad; el ratio mide
     * cuanto valor aporta cada unidad de capacidad consumida.
     *
     * Complejidad: O(n log n), dominada por el ordenamiento. El barrido
     * posterior es O(n). Espacio: O(n).
     *
     * POR QUE NO ES OPTIMO EN 0/1: la decision es irrevocable. Una vez que
     * greedy toma el item de mejor ratio, no revisa si dejarlo afuera habria
     * permitido meter dos items que juntos valen mas. Sobre items DIVISIBLES
     * (mochila fraccional) si es optimo, porque el ultimo item se puede partir
     * y la capacidad nunca queda desperdiciada.
     *
     * CONTRAEJEMPLO con los datos semilla y capacidad 10:
     *   I4 (peso 3, valor 50, ratio 16.67)
     *   I2 (peso 4, valor 40, ratio 10.00)
     *   I5 (peso 7, valor 55, ratio  7.86)
     *   I3 (peso 6, valor 30, ratio  5.00)
     *   I1 (peso 5, valor 10, ratio  2.00)
     * Greedy toma I4, despues I2, y ya no entra nada: peso 7, valor 90.
     * El optimo real es {I4, I5}: peso 10, valor 105.
     * Greedy pierde 15 porque gasta 4 de capacidad en I2 y deja 3 sin usar.
     */
    public Resultado greedy(List<Item> items, double capacidad) {
        if (capacidad < 0) {
            throw new IllegalArgumentException("La capacidad no puede ser negativa: " + capacidad);
        }
        Comparator<Item> porRatioDescendente =
                (a, b) -> Double.compare(b.ratioValorPeso(), a.ratioValorPeso());
        List<Item> ordenados = ordenamiento.mergeSort(items, porRatioDescendente);

        List<Item> elegidos = new ArrayList<>();
        double pesoTotal = 0.0;
        double valorTotal = 0.0;

        for (Item item : ordenados) {
            if (pesoTotal + item.getPeso() <= capacidad) { // entra entero o no entra
                elegidos.add(item);
                pesoTotal += item.getPeso();
                valorTotal += item.getValor();
            }
        }
        return new Resultado(elegidos, valorTotal, pesoTotal, "GREEDY (ratio valor/peso)");
    }

    /**
     * PROGRAMACION DINAMICA: mochila 0/1 con tabla dp, bottom-up.
     *
     * DEFINICION: dp[i][j] es el maximo valor alcanzable considerando los
     * primeros i items con capacidad exactamente j disponible.
     *
     * RECURRENCIA:
     *   dp[i][j] = dp[i-1][j]                                    si peso_i > j
     *   dp[i][j] = max( dp[i-1][j] , valor_i + dp[i-1][j-peso_i] ) si entra
     * O sea: para cada item se compara no llevarlo contra llevarlo.
     *
     * Es exacto justamente porque considera las dos ramas en vez de decidir de
     * una como greedy, y reusa subproblemas ya resueltos en vez de recalcularlos.
     *
     * RECUPERACION DEL CAMINO: se recorre la tabla hacia atras desde dp[n][W].
     * Si dp[i][j] es distinto de dp[i-1][j], entonces el item i fue necesario
     * para alcanzar ese valor: se lo agrega y se descuenta su peso.
     *
     * Complejidad: O(n * W) en tiempo y O(n * W) en espacio.
     * Es PSEUDOPOLINOMICA: W es un VALOR de la entrada, no su tamano. Escribir
     * W en binario ocupa log(W) bits, asi que el costo es exponencial respecto
     * del tamano real de la entrada. Con W = 1.000.000 la tabla es inmanejable
     * aunque haya solo 5 items.
     */
    public Resultado programacionDinamica(List<Item> items, int capacidad) {
        if (capacidad < 0) {
            throw new IllegalArgumentException("La capacidad no puede ser negativa: " + capacidad);
        }
        int n = items.size();
        int[] pesos = new int[n];
        for (int i = 0; i < n; i++) {
            double peso = items.get(i).getPeso();
            if (peso != Math.rint(peso)) {
                throw new IllegalArgumentException(
                        "La mochila 0/1 necesita pesos enteros para indexar la tabla dp. El item "
                                + items.get(i).getId() + " pesa " + peso
                                + ". Discretizar los pesos (por ejemplo, multiplicar por 10) antes de llamar.");
            }
            pesos[i] = (int) Math.rint(peso);
        }

        // dp[i][j] con una fila extra al principio: dp[0][j] = 0 (sin items, valor 0)
        double[][] dp = new double[n + 1][capacidad + 1];

        for (int i = 1; i <= n; i++) {
            int pesoItem = pesos[i - 1];
            double valorItem = items.get(i - 1).getValor();
            for (int j = 0; j <= capacidad; j++) {
                dp[i][j] = dp[i - 1][j];                       // no llevar el item i
                if (pesoItem <= j) {
                    double llevando = valorItem + dp[i - 1][j - pesoItem];
                    if (llevando > dp[i][j]) {
                        dp[i][j] = llevando;                   // llevarlo conviene
                    }
                }
            }
        }

        // Recuperacion del camino, de atras hacia adelante.
        List<Item> elegidos = new ArrayList<>();
        double pesoTotal = 0.0;
        int j = capacidad;
        for (int i = n; i > 0; i--) {
            if (dp[i][j] != dp[i - 1][j]) { // el item i cambio el resultado: fue elegido
                Item elegido = items.get(i - 1);
                elegidos.add(elegido);
                pesoTotal += elegido.getPeso();
                j -= pesos[i - 1];
            }
        }
        Collections.reverse(elegidos); // quedaron en orden inverso al recorrer hacia atras

        return new Resultado(elegidos, dp[n][capacidad], pesoTotal, "PROGRAMACION DINAMICA (mochila 0/1)");
    }
}
