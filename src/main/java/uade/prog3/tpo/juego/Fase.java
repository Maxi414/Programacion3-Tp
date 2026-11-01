package uade.prog3.tpo.juego;

/**
 * Fases de un turno, en orden. La fase 1 (cobro) es automatica: ocurre al
 * empezar el turno, asi que el jugador arranca siempre en RECLUTAMIENTO.
 *
 *   1. COBRO          (automatica)  produccion abastecida - mantenimiento de la red (MST)
 *   2. RECLUTAMIENTO  comprar unidades con el oro          -> mochila 0/1
 *   3. MARCHA         mover tropas por territorio propio   -> Dijkstra
 *   4. ATAQUE         atacar un territorio adyacente
 *   CIERRE            ya ataco: solo puede terminar el turno
 *
 * Se puede saltear fases, pero no volver atras: si un jugador marcha, ya no
 * puede comprar; si ataca, ya no puede marchar.
 */
public enum Fase {
    RECLUTAMIENTO,
    MARCHA,
    ATAQUE,
    CIERRE
}
