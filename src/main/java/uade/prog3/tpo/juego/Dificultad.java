package uade.prog3.tpo.juego;

/**
 * Dificultad de la IA. Cambia la TECNICA, no las reglas:
 *   FACIL   -> greedy puro (ratio ganancia/riesgo), no mira la respuesta del rival
 *   NORMAL  -> alfa-beta a profundidad 2 (su turno + la respuesta del humano)
 *   DIFICIL -> alfa-beta a profundidad 4 (dos turnos de cada uno)
 */
public enum Dificultad {
    FACIL(0),
    NORMAL(2),
    DIFICIL(4);

    private final int profundidad;

    Dificultad(int profundidad) {
        this.profundidad = profundidad;
    }

    public int profundidad() {
        return profundidad;
    }
}
