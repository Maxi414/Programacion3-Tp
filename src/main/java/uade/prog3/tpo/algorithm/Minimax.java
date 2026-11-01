package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * UNIDAD: Juegos de dos jugadores (clase 11) y poda alfa-beta (clase 13).
 *
 * USO EN EL JUEGO: es la IA rival. En cada turno de la maquina se arma el
 * arbol de juego desde el estado actual (MAX = la maquina, MIN = el humano),
 * se explora hasta una profundidad fija y se elige la jugada de mayor valor.
 *
 * Las dos versiones devuelven SIEMPRE EL MISMO VALOR. Alfa-beta solo deja de
 * mirar ramas que no pueden cambiar la decision. Eso se verifica en los tests
 * y se puede ver en vivo con el endpoint de analisis de la IA.
 */
@Component
public class Minimax {

    /**
     * Resultado de una busqueda.
     *
     * @param jugada           la jugada elegida en la raiz (null si no habia ninguna)
     * @param valor            valor MIN-MAX de la raiz, desde el punto de vista de MAX
     * @param nodosVisitados   estados evaluados o expandidos (incluye la raiz)
     * @param cortes           cantidad de "break" por beta <= alfa (0 en MIN-MAX puro)
     * @param profundidad      plies explorados (1 ply = el turno de un jugador)
     * @param tecnica          "MIN-MAX" o "ALFA-BETA"
     */
    public record Decision<J>(J jugada, double valor, long nodosVisitados, long cortes,
                              int profundidad, String tecnica) { }

    /** Valor para "gana MAX": se le resta la profundidad para preferir victorias rapidas. */
    public static final double VICTORIA = 1_000_000.0;

    private static final class Contador {
        long nodos;
        long cortes;
    }

    // ------------------------------------------------------------------
    // MIN-MAX puro
    // ------------------------------------------------------------------

    /**
     * MIN-MAX con profundidad limitada (clase 11, "MIN-MAX con corte y heuristica").
     *
     *   minimax(e, prof):
     *     si e es terminal      -> evaluar(e)   (certeza)
     *     si prof == 0          -> evaluar(e)   (estimacion: efecto horizonte)
     *     si juega MAX          -> max de los hijos
     *     si juega MIN          -> min de los hijos
     *
     * Es backtracking de tipo 2 (un bucle de hijos con una llamada recursiva
     * por hijo). El valor sube desde las hojas: se evalua en POSTORDEN.
     *
     * Complejidad: O(b^d) tiempo, con b la ramificacion (jugadas por estado)
     * y d la profundidad. Espacio O(b*d): la pila de recursion tiene d niveles
     * y en cada uno se guarda la lista de b jugadas.
     * Recurrencia: T(d) = b * T(d-1) + O(b * costo de aplicar).
     */
    public <E, J> Decision<J> minimax(JuegoDeDosJugadores<E, J> juego, E raiz, int profundidad) {
        validar(juego, raiz, profundidad);
        Contador c = new Contador();
        c.nodos++; // la raiz

        J mejorJugada = null;
        double mejorValor = Double.NEGATIVE_INFINITY;
        for (J jugada : juego.jugadas(raiz)) {
            double v = minimaxRec(juego, juego.aplicar(raiz, jugada), profundidad - 1, 1, c);
            if (v > mejorValor) { // estricto: a igual valor se queda la primera (la mejor ordenada)
                mejorValor = v;
                mejorJugada = jugada;
            }
        }
        if (mejorJugada == null) {
            mejorValor = juego.evaluar(raiz);
        }
        return new Decision<>(mejorJugada, mejorValor, c.nodos, 0, profundidad, "MIN-MAX");
    }

    private <E, J> double minimaxRec(JuegoDeDosJugadores<E, J> juego, E e, int prof, int nivel, Contador c) {
        c.nodos++;
        if (juego.esTerminal(e)) {
            return ajustarPorNivel(juego.evaluar(e), nivel);
        }
        if (prof == 0) {
            return juego.evaluar(e); // corte por profundidad: estimacion, no certeza
        }
        List<J> hijos = juego.jugadas(e);
        if (hijos.isEmpty()) {
            return juego.evaluar(e);
        }
        if (juego.turnoDeMax(e)) {
            double mejor = Double.NEGATIVE_INFINITY;
            for (J j : hijos) {
                mejor = Math.max(mejor, minimaxRec(juego, juego.aplicar(e, j), prof - 1, nivel + 1, c));
            }
            return mejor;
        }
        double mejor = Double.POSITIVE_INFINITY;
        for (J j : hijos) {
            mejor = Math.min(mejor, minimaxRec(juego, juego.aplicar(e, j), prof - 1, nivel + 1, c));
        }
        return mejor;
    }

    // ------------------------------------------------------------------
    // Poda alfa-beta
    // ------------------------------------------------------------------

    /**
     * MIN-MAX + dos parametros + un break (clase 13).
     *
     *   alfa: lo minimo que MAX ya tiene asegurado por otra jugada. Arranca en
     *         -infinito y solo sube.
     *   beta: lo maximo que MIN va a permitir, porque tiene otra jugada mejor.
     *         Arranca en +infinito y solo baja.
     *   si beta <= alfa: ninguno de los dos va a dejar que la partida pase por
     *         aca, y los hermanos que faltan NO se miran.
     *
     * alfa y beta viajan POR VALOR: lo que cambia un hijo no vuelve al padre.
     *
     * Es la misma idea que la cota de Branch & Bound: alfa es "la mejor
     * solucion encontrada" de MAX. Si una rama no puede superarla, se poda.
     *
     * Complejidad: O(b^d) en el peor caso (orden de jugadas pesimo, no poda
     * nada). Con el MEJOR orden posible O(b^(d/2)): con el mismo tiempo se
     * llega al doble de profundidad. Por eso las jugadas vienen ordenadas por
     * la heuristica greedy del juego.
     */
    public <E, J> Decision<J> alfaBeta(JuegoDeDosJugadores<E, J> juego, E raiz, int profundidad) {
        validar(juego, raiz, profundidad);
        Contador c = new Contador();
        c.nodos++;

        double alfa = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;
        J mejorJugada = null;
        double mejorValor = Double.NEGATIVE_INFINITY;
        for (J jugada : juego.jugadas(raiz)) {
            double v = alfaBetaRec(juego, juego.aplicar(raiz, jugada), profundidad - 1, 1, alfa, beta, c);
            if (v > mejorValor) {
                mejorValor = v;
                mejorJugada = jugada;
            }
            alfa = Math.max(alfa, mejorValor); // en la raiz no hay beta que cortar
        }
        if (mejorJugada == null) {
            mejorValor = juego.evaluar(raiz);
        }
        return new Decision<>(mejorJugada, mejorValor, c.nodos, c.cortes, profundidad, "ALFA-BETA");
    }

    private <E, J> double alfaBetaRec(JuegoDeDosJugadores<E, J> juego, E e, int prof, int nivel,
                                      double alfa, double beta, Contador c) {
        c.nodos++;
        if (juego.esTerminal(e)) {
            return ajustarPorNivel(juego.evaluar(e), nivel);
        }
        if (prof == 0) {
            return juego.evaluar(e);
        }
        List<J> hijos = juego.jugadas(e);
        if (hijos.isEmpty()) {
            return juego.evaluar(e);
        }
        if (juego.turnoDeMax(e)) {
            double mejor = Double.NEGATIVE_INFINITY;
            for (J j : hijos) {
                mejor = Math.max(mejor, alfaBetaRec(juego, juego.aplicar(e, j), prof - 1, nivel + 1, alfa, beta, c));
                alfa = Math.max(alfa, mejor);
                if (beta <= alfa) {
                    c.cortes++;
                    break; // poda beta: MIN nunca va a dejar que se llegue aca
                }
            }
            return mejor;
        }
        double mejor = Double.POSITIVE_INFINITY;
        for (J j : hijos) {
            mejor = Math.min(mejor, alfaBetaRec(juego, juego.aplicar(e, j), prof - 1, nivel + 1, alfa, beta, c));
            beta = Math.min(beta, mejor);
            if (beta <= alfa) {
                c.cortes++;
                break; // poda alfa: MAX ya tiene algo mejor en otra rama
            }
        }
        return mejor;
    }

    /**
     * Preferir victorias RAPIDAS (clase 11, "mejorar la jugada elegida"): una
     * victoria a 1 jugada vale mas que una a 3, y una derrota lejana es menos
     * mala que una inmediata. Asi la IA remata en cuanto puede y resiste lo
     * mas posible cuando va perdiendo. Depende de QUIEN GANA, no de quien juega.
     *
     * Solo se ajustan los valores de victoria o derrota (del orden de
     * VICTORIA). Un terminal con un valor "chico" (un empate, un puntaje) se
     * deja como esta.
     */
    private double ajustarPorNivel(double valorTerminal, int nivel) {
        if (valorTerminal >= VICTORIA / 2) {
            return valorTerminal - nivel;
        }
        if (valorTerminal <= -VICTORIA / 2) {
            return valorTerminal + nivel;
        }
        return valorTerminal;
    }

    private <E, J> void validar(JuegoDeDosJugadores<E, J> juego, E raiz, int profundidad) {
        if (profundidad < 1) {
            throw new IllegalArgumentException("La profundidad debe ser al menos 1: " + profundidad);
        }
        if (!juego.turnoDeMax(raiz)) {
            throw new IllegalArgumentException("La raiz del arbol tiene que ser un turno de MAX.");
        }
    }
}
