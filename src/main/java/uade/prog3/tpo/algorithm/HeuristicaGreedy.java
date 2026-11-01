package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * UNIDAD: Greedy (clase 3).
 *
 * USO EN EL JUEGO: "que territorio ataco ahora". Para cada ataque posible se
 * calcula
 *
 *     ratio = ganancia / riesgo
 *
 * (ganancia = lo que aporta el territorio conquistado; riesgo = las tropas
 * que hay que comprometer para ganarlo) y se elige el de mayor ratio. Es el
 * mismo criterio que la mochila fraccional: valor por unidad de recurso
 * consumido.
 *
 * Se usa en tres lugares:
 *  - el boton "sugerir ataque" del jugador
 *  - la IA en dificultad FACIL (juega solo con esto, sin mirar adelante)
 *  - el ORDEN de las jugadas que recibe alfa-beta: mirar primero las jugadas
 *    que el greedy cree mejores sube alfa antes y aumenta las podas
 *
 * POR QUE NO ES OPTIMO: la decision es miope e irrevocable. Atacar hoy el
 * territorio de mejor ratio puede dejar sin tropas al frente por donde el
 * rival entra al turno siguiente, o gastar el ejercito en un objetivo barato
 * cuando juntando un turno mas se tomaba la capital. Eso es exactamente lo que
 * MIN-MAX mira y el greedy no.
 */
@Component
public class HeuristicaGreedy {

    private final Ordenamiento ordenamiento;

    public HeuristicaGreedy(Ordenamiento ordenamiento) {
        this.ordenamiento = ordenamiento;
    }

    /**
     * Ordena los candidatos por ratio ganancia/riesgo DESCENDENTE.
     *
     * Complejidad: O(n log n) por el MergeSort propio (estable: a igual ratio
     * se respeta el orden de entrada, y la IA queda determinista).
     * Espacio: O(n).
     */
    public <T> List<T> rankingPorRatio(List<T> candidatos, ToDoubleFunction<T> ganancia,
                                       ToDoubleFunction<T> riesgo) {
        List<Puntuado<T>> puntuados = new ArrayList<>(candidatos.size());
        for (T c : candidatos) {
            puntuados.add(new Puntuado<>(c, ratio(ganancia.applyAsDouble(c), riesgo.applyAsDouble(c))));
        }
        List<Puntuado<T>> ordenados = ordenamiento.mergeSort(puntuados,
                (a, b) -> Double.compare(b.ratio(), a.ratio()));
        List<T> salida = new ArrayList<>(ordenados.size());
        for (Puntuado<T> p : ordenados) {
            salida.add(p.candidato());
        }
        return salida;
    }

    /** La eleccion voraz: el primero del ranking, o null si no hay candidatos. */
    public <T> T elegir(List<T> candidatos, ToDoubleFunction<T> ganancia, ToDoubleFunction<T> riesgo) {
        List<T> ranking = rankingPorRatio(candidatos, ganancia, riesgo);
        return ranking.isEmpty() ? null : ranking.get(0);
    }

    /** ganancia / riesgo, con riesgo 0 tratado como "gratis" (ratio maximo). */
    public double ratio(double ganancia, double riesgo) {
        return riesgo <= 0 ? Double.MAX_VALUE : ganancia / riesgo;
    }

    private record Puntuado<T>(T candidato, double ratio) { }
}
