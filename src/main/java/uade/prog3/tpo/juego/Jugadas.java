package uade.prog3.tpo.juego;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uade.prog3.tpo.algorithm.ArbolRecubrimiento;

import java.util.List;

/**
 * Los tipos de datos que entran y salen del motor de juego.
 * Son records inmutables: se pueden compartir entre estados del minimax.
 */
public final class Jugadas {

    private Jugadas() {
    }

    /** Comprar UNA unidad del catalogo y ubicarla en un territorio propio abastecido. */
    public record Compra(String unidad, String destino) { }

    /** Mover 'tropas' de origen a destino. Se usa para la marcha y para el ataque. */
    public record Movimiento(String origen, String destino, int tropas) { }

    /**
     * Un turno completo, tal como lo juega la IA: compra (mochila), una marcha
     * opcional (Dijkstra) y un ataque opcional. Es la "jugada" del arbol de
     * minimax: 1 ply = el turno entero de un jugador.
     */
    public record PlanDeTurno(List<Compra> compras, Movimiento marcha, Movimiento ataque,
                              String descripcion, double ratioGreedy) { }

    /** Resultado de resolver un ataque. El combate es determinista: no hay dados. */
    public record ResultadoAtaque(String origen, String destino, int enviadas, int defensa,
                                  boolean conquistado, int tropasQueQuedan, boolean capitalCapturada) { }

    /**
     * La red de abastecimiento de un jugador (fase 1 del turno).
     *
     * @param abastecidos territorios en la misma componente que la capital (DFS)
     * @param aislados    territorios propios desconectados de la capital: no producen
     *                    ni pueden recibir refuerzos comprados
     * @param componentes cantidad de componentes conexas del imperio (1 = entero)
     * @param red         aristas del arbol de recubrimiento minimo (Prim desde la capital)
     * @param costoRed    costo total del MST, en dias de marcha
     * @param produccion  oro que producen los territorios abastecidos
     * @param mantenimiento oro que cuesta mantener la red: costoRed / 4
     * @param ingresoNeto produccion - mantenimiento
     */
    public record Abastecimiento(int jugador, List<String> abastecidos, List<String> aislados,
                                 int componentes, List<ArbolRecubrimiento.AristaMst> red, double costoRed,
                                 int produccion, int mantenimiento, int ingresoNeto,
                                 @JsonIgnore boolean[] mascara) {

        /** true si el territorio de indice i esta abastecido. */
        public boolean abastecido(int i) {
            return mascara[i];
        }
    }
}
