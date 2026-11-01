package uade.prog3.tpo.algorithm;

import java.util.List;

/**
 * Las tres piezas de un algoritmo de juego (clase 11), como contrato:
 *
 *   1. generador de movimientos  -> jugadas(estado) + aplicar(estado, jugada)
 *   2. test de terminal          -> esTerminal(estado)
 *   3. funcion de evaluacion     -> evaluar(estado)
 *
 * MIN-MAX y alfa-beta (clase Minimax) solo hablan con esta interfaz: no saben
 * nada del juego concreto. Lo mismo resuelven los fosforos de la clase 11 (en
 * los tests) que la conquista de territorios.
 *
 * @param <E> el estado del juego
 * @param <J> una jugada
 */
public interface JuegoDeDosJugadores<E, J> {

    /**
     * Jugadas legales desde el estado, YA ORDENADAS de la que parece mejor a la
     * que parece peor. El orden no cambia el resultado de MIN-MAX, pero en
     * alfa-beta mirar primero las buenas sube alfa antes y poda mucho mas.
     */
    List<J> jugadas(E estado);

    /**
     * Devuelve un estado NUEVO con la jugada aplicada; el original no se toca.
     * Por eso el minimax no necesita retroceso explicito (como en los fosforos
     * de la clase 11): cada hijo es una copia. Para que sea barato, el estado
     * tiene que ser chico y clonable (arreglos de primitivos, no objetos con
     * referencias cruzadas).
     */
    E aplicar(E estado, J jugada);

    /** Caso base de la recursion: la partida termino. */
    boolean esTerminal(E estado);

    /**
     * Valor del estado desde el punto de vista de MAX. En un terminal es la
     * certeza (gana MAX / gana MIN / empate); en un estado intermedio, cuando
     * se corta por profundidad, es una ESTIMACION heuristica.
     */
    double evaluar(E estado);

    /** true si en este estado le toca mover a MAX. */
    boolean turnoDeMax(E estado);
}
