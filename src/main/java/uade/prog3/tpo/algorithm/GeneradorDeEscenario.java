package uade.prog3.tpo.algorithm;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * UNIDAD: Backtracking (clases 9 y 10) — backtracking de TIPO 1: se busca UNA
 * solucion valida y se corta apenas aparece.
 *
 * USO EN EL JUEGO: generar el arranque de una partida sobre el mapa. Hay que
 * elegir una capital y una region inicial para cada jugador que cumplan:
 *
 *   R1. las capitales son ciudades y estan a >= distanciaMinima dias de marcha
 *       una de la otra (distancia de Floyd, no cantidad de aristas)
 *   R2. cada region tiene exactamente 'tamanioRegion' territorios y es CONEXA
 *       (se arma creciendo siempre por un vecino de lo que ya se tiene)
 *   R3. las regiones no se tocan: ningun territorio azul linda con uno rojo,
 *       asi nadie puede atacar al rival en el primer turno
 *   R4. la produccion de las dos regiones difiere en a lo sumo 'tolerancia'
 *       (partida balanceada)
 *
 * Con semilla == null el orden es determinista (escenario CLASICO: para la
 * capital roja se prueban primero las mas lejanas). Con semilla, el orden de
 * los candidatos se mezcla y cada partida arranca distinto.
 */
@Component
public class GeneradorDeEscenario {

    public static final int AZUL = 1;
    public static final int ROJO = 2;

    /** Un arranque valido. Las regiones incluyen a la capital como primer elemento. */
    public record Escenario(String capitalAzul, List<String> regionAzul,
                            String capitalRoja, List<String> regionRoja,
                            int produccionAzul, int produccionRoja,
                            int nodosExplorados, int nodosPodados) { }

    private final Ordenamiento ordenamiento;

    public GeneradorDeEscenario(Ordenamiento ordenamiento) {
        this.ordenamiento = ordenamiento;
    }

    /** Estado de la busqueda: lo que se va construyendo y se deshace al volver. */
    private static final class Busqueda {
        Grafo grafo;
        int[] produccion;
        boolean[][] adyacente;
        int tamanio;
        int tolerancia;
        int maxProduccion;
        Random rnd;
        int[] duenio;                                   // 0 libre, 1 azul, 2 rojo
        List<List<Integer>> region = new ArrayList<>(); // indice 1 y 2
        int[] prod = new int[3];
        int explorados;
        int podados;
    }

    /**
     * ESQUEMA DE BACKTRACKING:
     *   - DECISION: que territorio se agrega a la region que se esta armando
     *     (primero las capitales, despues la region azul, despues la roja)
     *   - PODA POR VIABILIDAD:
     *       * capitales a menos de distanciaMinima: no se sigue (R1)
     *       * un candidato que toca la region rival se descarta (R3)
     *       * si con el candidato la produccion roja ya supera la azul + tolerancia,
     *         se descarta: la produccion solo puede crecer (R4)
     *       * si ni sumando los territorios mas productivos la roja llega a la
     *         azul - tolerancia, se descarta (R4)
     *   - RETROCESO: al volver se libera el territorio y se resta su produccion
     *
     * Complejidad: exponencial en el peor caso. Hay O(C^2) pares de capitales
     * y, para cada uno, el crecimiento de cada region prueba O(V) candidatos
     * en cada uno de sus m pasos: O(C^2 * V^(2m)). Con m = 3 y V = 12 la poda
     * lo deja en unos pocos cientos de nodos.
     * Espacio: O(V) por duenio[] y las regiones, mas la pila de profundidad 2m.
     */
    public Escenario generar(Grafo grafo, int[] produccion, boolean[] puedeSerCapital,
                             double[][] distancias, int tamanioRegion, double distanciaMinima,
                             int tolerancia, Long semilla) {
        if (grafo.esDirigido()) {
            throw new IllegalArgumentException("El generador trabaja sobre el mapa NO dirigido.");
        }
        int n = grafo.cantidadVertices();
        if (tamanioRegion < 1 || 2 * tamanioRegion > n) {
            throw new IllegalArgumentException(
                    "Con " + n + " territorios no se pueden armar dos regiones de " + tamanioRegion);
        }

        Busqueda b = new Busqueda();
        b.grafo = grafo;
        b.produccion = produccion;
        b.tamanio = tamanioRegion;
        b.tolerancia = tolerancia;
        b.rnd = semilla == null ? null : new Random(semilla);
        b.duenio = new int[n];
        b.region.add(null);
        b.region.add(new ArrayList<>());
        b.region.add(new ArrayList<>());
        b.adyacente = new boolean[n][n];
        for (int u = 0; u < n; u++) {
            b.maxProduccion = Math.max(b.maxProduccion, produccion[u]);
            for (Grafo.Arista a : grafo.vecinos(u)) {
                b.adyacente[u][a.destino()] = true;
            }
        }

        List<Integer> capitales = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (puedeSerCapital[v]) {
                capitales.add(v);
            }
        }
        mezclar(capitales, b.rnd);

        for (int azul : capitales) {
            List<Integer> rivales = new ArrayList<>();
            for (int r : capitales) {
                if (r != azul) {
                    rivales.add(r);
                }
            }
            if (b.rnd == null) {
                // escenario clasico: probar primero las capitales mas alejadas
                rivales = ordenamiento.mergeSort(rivales,
                        (x, y) -> Double.compare(distancias[azul][y], distancias[azul][x]));
            } else {
                mezclar(rivales, b.rnd);
            }
            for (int roja : rivales) {
                b.explorados++;
                if (distancias[azul][roja] < distanciaMinima || b.adyacente[azul][roja]) {
                    b.podados++;
                    continue; // R1: capitales demasiado cerca
                }
                asignar(b, azul, AZUL);
                asignar(b, roja, ROJO);
                if (crecer(b, AZUL)) {
                    return armar(b);
                }
                desasignar(b, roja, ROJO);  // retroceso de la eleccion de capitales
                desasignar(b, azul, AZUL);
            }
        }
        throw new IllegalStateException("No existe un escenario que cumpla las restricciones: "
                + "regiones de " + tamanioRegion + ", capitales a " + distanciaMinima
                + " dias o mas y diferencia de produccion <= " + tolerancia + ".");
    }

    private boolean crecer(Busqueda b, int jugador) {
        b.explorados++;
        List<Integer> region = b.region.get(jugador);
        if (region.size() == b.tamanio) {
            if (jugador == AZUL) {
                return crecer(b, ROJO); // la region azul esta completa: ahora la roja
            }
            return Math.abs(b.prod[AZUL] - b.prod[ROJO]) <= b.tolerancia;
        }

        int otro = jugador == AZUL ? ROJO : AZUL;
        int faltan = b.tamanio - region.size();
        for (int v : candidatos(b, jugador, otro)) {
            if (jugador == ROJO) {
                int nueva = b.prod[ROJO] + b.produccion[v];
                if (nueva > b.prod[AZUL] + b.tolerancia) {
                    b.podados++;
                    continue; // R4: ya se paso, y la produccion no baja
                }
                if (nueva + (faltan - 1) * b.maxProduccion < b.prod[AZUL] - b.tolerancia) {
                    b.podados++;
                    continue; // R4: ni con los mejores territorios llega
                }
            }
            asignar(b, v, jugador);              // decision
            if (crecer(b, jugador)) {
                return true;                     // tipo 1: la primera solucion alcanza
            }
            desasignar(b, v, jugador);           // RETROCESO
        }
        return false;
    }

    /** Vecinos libres de la region que no tocan la region del otro jugador (R2 y R3). */
    private List<Integer> candidatos(Busqueda b, int jugador, int otro) {
        boolean[] yaAgregado = new boolean[b.duenio.length];
        List<Integer> salida = new ArrayList<>();
        for (int u : b.region.get(jugador)) {
            for (Grafo.Arista a : b.grafo.vecinos(u)) {
                int v = a.destino();
                if (b.duenio[v] != 0 || yaAgregado[v]) {
                    continue;
                }
                yaAgregado[v] = true;
                if (tocaA(b, v, otro)) {
                    b.podados++;
                    continue; // R3: lindaria con el rival desde el turno 1
                }
                salida.add(v);
            }
        }
        mezclar(salida, b.rnd);
        return salida;
    }

    private boolean tocaA(Busqueda b, int v, int jugador) {
        for (int u : b.region.get(jugador)) {
            if (b.adyacente[v][u]) {
                return true;
            }
        }
        return false;
    }

    private void asignar(Busqueda b, int v, int jugador) {
        b.duenio[v] = jugador;
        b.region.get(jugador).add(v);
        b.prod[jugador] += b.produccion[v];
    }

    private void desasignar(Busqueda b, int v, int jugador) {
        b.duenio[v] = 0;
        List<Integer> region = b.region.get(jugador);
        region.remove(region.size() - 1);
        b.prod[jugador] -= b.produccion[v];
    }

    /** Fisher-Yates. Sin semilla no mezcla (orden determinista). */
    private void mezclar(List<Integer> lista, Random rnd) {
        if (rnd == null) {
            return;
        }
        for (int i = lista.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, tmp);
        }
    }

    private Escenario armar(Busqueda b) {
        List<String> azul = new ArrayList<>();
        for (int v : b.region.get(AZUL)) {
            azul.add(b.grafo.idDe(v));
        }
        List<String> roja = new ArrayList<>();
        for (int v : b.region.get(ROJO)) {
            roja.add(b.grafo.idDe(v));
        }
        return new Escenario(azul.get(0), azul, roja.get(0), roja,
                b.prod[AZUL], b.prod[ROJO], b.explorados, b.podados);
    }
}
