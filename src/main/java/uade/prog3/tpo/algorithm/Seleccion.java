package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;
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
 *
 * EN EL JUEGO es la fase de COMPRA: el mercado ofrece una unidad de cada tipo
 * por turno (0/1: se compra o no se compra, no hay media catapulta). Cada
 * Item es una unidad: peso = costo en oro, valor = poder de combate. Con el
 * oro del turno como capacidad, hay que elegir que unidades comprar para
 * maximizar el poder total.
 */
@Component
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
     * CONTRAEJEMPLO con el catalogo del juego y 6 de oro (el oro del primer
     * turno del escenario clasico):
     *   U2 Arqueros   (costo 3, poder  5, ratio 1.667)
     *   U6 Guardia    (costo 8, poder 13, ratio 1.625)
     *   U4 Caballería (costo 5, poder  8, ratio 1.600)
     *   U3 Lanceros   (costo 4, poder  6, ratio 1.500)
     *   U5 Catapulta  (costo 6, poder  9, ratio 1.500)
     *   U1 Milicia    (costo 1, poder  1, ratio 1.000)
     * Greedy toma Arqueros (3 de oro), despues ya no entra nada hasta la
     * Milicia: gasta 4, poder 6, y le sobran 2 de oro.
     * El optimo real vale 9 y gasta los 6: la PD devuelve Milicia + Caballería
     * (la Catapulta sola empata en 9). Greedy pierde 3 de poder (un tercio)
     * porque la mejor unidad "por moneda" bloquea la compra que aprovecha todo
     * el presupuesto.
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
     * aunque haya solo 6 unidades. En el juego W es el oro del turno (decenas),
     * asi que la tabla es chica y la IA puede resolverla en cada nodo del arbol.
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
