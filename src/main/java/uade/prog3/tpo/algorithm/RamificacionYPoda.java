package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.model.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UNIDAD: Branch and Bound (clase 12)       PUNTAJE: 1 punto
 *
 * Es backtracking mas una cota. La diferencia con la clase Backtracking es el
 * criterio de poda: alli se podaba por VIABILIDAD (esta rama viola una
 * restriccion), aca se poda por OPTIMALIDAD (esta rama no puede mejorar lo que
 * ya tengo).
 *
 * PROBLEMA DE REPARTO DEL JUEGO: al terminar la fase de compra hay que
 * repartir las unidades nuevas entre los frentes del imperio. Un imperio cae
 * por su frente mas debil, asi que el objetivo es MAXIMIZAR EL MARGEN DEL
 * FRENTE MAS DEBIL (problema max-min, el "gemelo" del makespan):
 *
 *     maximizar  min_j ( margen_j + suma del poder asignado al frente j )
 *
 * donde margen_j = defensa actual del frente j - amenaza enemiga sobre el.
 */
@Component
public class RamificacionYPoda {

    /** Un frente que recibe refuerzos: su id y su margen antes de reforzar. */
    public record Frente(String id, double margen) { }

    /**
     * Resultado del reparto.
     *
     * @param porFrente         unidades asignadas a cada frente
     * @param margenFinal       margen de cada frente despues de reforzar
     * @param margenMinimo      el valor optimo: margen del frente mas debil
     * @param nodosExplorados   nodos del arbol que se visitaron
     * @param nodosPodados      ramas cortadas por la COTA (poda por optimalidad)
     * @param podadosPorSimetria ramas descartadas por ser simetricas a otra ya explorada
     * @param solucionInicial   valor de la solucion greedy con la que se arranca
     * @param cotaRaiz          cota superior optimista en la raiz del arbol
     */
    public record Reparto(Map<String, List<Item>> porFrente, Map<String, Double> margenFinal,
                          double margenMinimo, int nodosExplorados, int nodosPodados,
                          int podadosPorSimetria, double solucionInicial, double cotaRaiz) { }

    private final Ordenamiento ordenamiento;

    public RamificacionYPoda(Ordenamiento ordenamiento) {
        this.ordenamiento = ordenamiento;
    }

    /** Estado mutable de la busqueda, para no arrastrar diez parametros. */
    private static final class Busqueda {
        double[] carga;          // margen actual de cada frente
        int[] asignacion;        // asignacion[i] = frente de la unidad i
        int[] mejorAsignacion;
        double mejor;            // mejor margen minimo encontrado
        double[] restanteDesde;  // restanteDesde[i] = poder total de las unidades i..n-1
        int explorados;
        int podados;
        int simetricos;
    }

    /**
     * Version del scaffold: repartir los items entre k columnas identicas que
     * arrancan con margen 0. Es el caso "partir el ejercito en k columnas lo
     * mas parejas posible".
     */
    public Reparto repartir(List<Item> unidades, int cantidadFrentes) {
        if (cantidadFrentes < 1) {
            throw new IllegalArgumentException(
                    "La cantidad de frentes debe ser al menos 1: " + cantidadFrentes);
        }
        List<Frente> frentes = new ArrayList<>();
        for (int j = 0; j < cantidadFrentes; j++) {
            frentes.add(new Frente("columna-" + j, 0.0));
        }
        return repartir(unidades, frentes);
    }

    /**
     * Branch & Bound del reparto de refuerzos.
     *
     * RAMIFICAR: cada unidad puede ir a cualquiera de los k frentes, asi que el
     * arbol tiene k^n hojas. Se ramifica unidad por unidad, en orden de poder
     * DECRECIENTE (MergeSort propio): decidir primero las unidades grandes hace
     * que las cargas se separen rapido y la cota empiece a cortar antes.
     *
     * ACOTAR: se MAXIMIZA, asi que la cota tiene que ser SUPERIOR (optimista:
     * nunca puede decir que la rama da menos de lo que realmente puede dar).
     *     cota = min( (suma de margenes actuales + poder restante) / k ,
     *                 margen del frente mas debil hoy + poder restante )
     * Los dos terminos son cotas superiores verdaderas del minimo final:
     *   - el minimo de k numeros nunca supera su promedio, y la suma final de
     *     los margenes es fija: lo que hay hoy mas todo lo que falta repartir
     *   - el frente mas debil de hoy, aun recibiendo TODAS las unidades que
     *     faltan, no puede terminar mas alto que su margen + el poder restante;
     *     y el minimo final es menor o igual que el valor final de ese frente
     * Como la cota nunca subestima, si ya es <= a la mejor solucion conocida
     * ninguna hoja de esa rama puede mejorarla estrictamente: podar es seguro.
     * Una cota pesimista (que subestime) podaria ramas con el optimo adentro
     * y el resultado dejaria de serlo SIN AVISAR.
     *
     * PODAR: dos cortes.
     *   1. por cota, el descripto arriba
     *   2. por simetria: dos frentes con el MISMO margen actual son
     *      intercambiables (el objetivo solo depende del multiconjunto de
     *      margenes), asi que se prueba solo el primero de cada grupo de iguales
     *
     * SOLUCION INICIAL: greedy "cada unidad al frente mas debil del momento".
     * Da un valor razonable de entrada y la poda funciona desde el primer nodo.
     *
     * Complejidad: O(k^n) en el peor caso, muchisimo menor con la poda.
     * Espacio: O(n + k) mas la pila de recursion, de profundidad n.
     */
    public Reparto repartir(List<Item> unidades, List<Frente> frentes) {
        if (frentes.isEmpty()) {
            throw new IllegalArgumentException("Hace falta al menos un frente para repartir.");
        }
        for (Item u : unidades) {
            if (u.getValor() < 0) {
                throw new IllegalArgumentException(
                        "La unidad " + u.getId() + " tiene poder negativo: " + u.getValor());
            }
        }
        int k = frentes.size();

        Comparator<Item> porPoderDescendente = (a, b) -> Double.compare(b.getValor(), a.getValor());
        List<Item> ordenadas = ordenamiento.mergeSort(unidades, porPoderDescendente);
        int n = ordenadas.size();

        Busqueda b = new Busqueda();
        b.carga = new double[k];
        for (int j = 0; j < k; j++) {
            b.carga[j] = frentes.get(j).margen();
        }
        b.asignacion = new int[n];
        b.mejorAsignacion = new int[n];
        b.restanteDesde = new double[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            b.restanteDesde[i] = b.restanteDesde[i + 1] + ordenadas.get(i).getValor();
        }

        b.mejor = solucionInicialGreedy(ordenadas, frentes, b.mejorAsignacion);
        double solucionInicial = b.mejor;
        double cotaRaiz = cota(b, 0);

        ramificar(ordenadas, 0, b);

        Map<String, List<Item>> porFrente = new LinkedHashMap<>();
        Map<String, Double> margenFinal = new LinkedHashMap<>();
        double[] cargaFinal = new double[k];
        for (int j = 0; j < k; j++) {
            porFrente.put(frentes.get(j).id(), new ArrayList<>());
            cargaFinal[j] = frentes.get(j).margen();
        }
        for (int i = 0; i < n; i++) {
            int j = b.mejorAsignacion[i];
            porFrente.get(frentes.get(j).id()).add(ordenadas.get(i));
            cargaFinal[j] += ordenadas.get(i).getValor();
        }
        for (int j = 0; j < k; j++) {
            margenFinal.put(frentes.get(j).id(), cargaFinal[j]);
        }
        return new Reparto(porFrente, margenFinal, b.mejor, b.explorados, b.podados,
                b.simetricos, solucionInicial, cotaRaiz);
    }

    private void ramificar(List<Item> unidades, int indice, Busqueda b) {
        b.explorados++;

        if (indice == unidades.size()) { // hoja: todas las unidades asignadas
            double minimo = minimo(b.carga);
            if (minimo > b.mejor) {
                b.mejor = minimo;
                System.arraycopy(b.asignacion, 0, b.mejorAsignacion, 0, unidades.size());
            }
            return;
        }

        // COTA SUPERIOR optimista del margen minimo alcanzable desde aca
        if (cota(b, indice) <= b.mejor) {
            b.podados++;
            return; // ninguna hoja de esta rama puede mejorar lo que ya tengo
        }

        double poder = unidades.get(indice).getValor();
        for (int j = 0; j < b.carga.length; j++) {
            // PODA POR SIMETRIA: un frente con el mismo margen que otro anterior
            // genera exactamente el mismo subarbol
            if (yaHayUnFrenteAnteriorIgual(b.carga, j)) {
                b.simetricos++;
                continue;
            }
            b.carga[j] += poder;          // decision: la unidad va al frente j
            b.asignacion[indice] = j;

            ramificar(unidades, indice + 1, b);

            b.carga[j] -= poder;          // retroceso
        }
    }

    /** cota = min( promedio final de los margenes , frente mas debil + todo lo que falta ). */
    private double cota(Busqueda b, int indice) {
        double restante = b.restanteDesde[indice];
        double suma = 0.0;
        for (double c : b.carga) {
            suma += c;
        }
        double porPromedio = (suma + restante) / b.carga.length;
        double porFrenteMasDebil = minimo(b.carga) + restante;
        return Math.min(porPromedio, porFrenteMasDebil);
    }

    private boolean yaHayUnFrenteAnteriorIgual(double[] carga, int j) {
        for (int anterior = 0; anterior < j; anterior++) {
            if (carga[anterior] == carga[j]) {
                return true;
            }
        }
        return false;
    }

    private double minimo(double[] valores) {
        double m = Double.POSITIVE_INFINITY;
        for (double v : valores) {
            m = Math.min(m, v);
        }
        return m;
    }

    /**
     * Greedy: unidades de mayor a menor poder, cada una al frente MAS DEBIL del
     * momento. Es la misma idea que LPT en el makespan. No siempre es optima:
     * con poderes 13, 9, 8, 6, 5, 1 y dos frentes da 20, y el optimo es 21
     * ({13, 8} contra {9, 6, 5, 1}). Aca solo se usa como cota inicial.
     */
    private double solucionInicialGreedy(List<Item> ordenadas, List<Frente> frentes, int[] asignacionSalida) {
        double[] carga = new double[frentes.size()];
        for (int j = 0; j < carga.length; j++) {
            carga[j] = frentes.get(j).margen();
        }
        for (int i = 0; i < ordenadas.size(); i++) {
            int masDebil = 0;
            for (int j = 1; j < carga.length; j++) {
                if (carga[j] < carga[masDebil]) {
                    masDebil = j;
                }
            }
            carga[masDebil] += ordenadas.get(i).getValor();
            asignacionSalida[i] = masDebil;
        }
        return minimo(carga);
    }
}
