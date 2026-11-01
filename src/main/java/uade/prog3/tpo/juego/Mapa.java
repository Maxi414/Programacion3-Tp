package uade.prog3.tpo.juego;

import uade.prog3.tpo.algorithm.CaminosMinimos;
import uade.prog3.tpo.algorithm.Ordenamiento;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.service.Grafo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parte ESTATICA de una partida: territorios, rutas, la matriz de distancias
 * y el catalogo de unidades. No cambia durante la partida, asi que se arma
 * una sola vez (al crearla) y la comparten todos los estados.
 *
 * El indice de cada territorio en 'territorios' es el MISMO que su indice en
 * el Grafo y en los arreglos de Estado. Eso permite trabajar con int en los
 * bucles calientes de la IA en vez de buscar por id.
 */
public final class Mapa {

    /** Un vertice del mapa: lo que en Neo4j es un Nodo. */
    public record Territorio(String id, String nombre, Terreno terreno, int produccion, double x, double y) { }

    /** Una arista del mapa: lo que en Neo4j es una relacion CONECTA. costo = dias de marcha. */
    public record Ruta(String origen, String destino, int costo) { }

    private final List<Territorio> territorios;
    private final List<Ruta> rutas;
    private final Grafo grafo;
    private final double[][] distancias;
    private final List<Item> catalogo;
    private final Map<String, Item> unidadPorId = new HashMap<>();

    private Mapa(List<Territorio> territorios, List<Ruta> rutas, Grafo grafo,
                 double[][] distancias, List<Item> catalogo) {
        this.territorios = Collections.unmodifiableList(territorios);
        this.rutas = Collections.unmodifiableList(rutas);
        this.grafo = grafo;
        this.distancias = distancias;
        this.catalogo = Collections.unmodifiableList(catalogo);
        for (Item u : catalogo) {
            unidadPorId.put(u.getId(), u);
        }
    }

    /**
     * Arma el mapa:
     *   - grafo NO dirigido (una ruta se recorre en los dos sentidos)
     *   - matriz de distancias con Floyd-Warshall, UNA vez: O(V^3) al crear
     *     la partida y despues cada consulta de la IA es O(1)
     *   - catalogo ordenado por costo con MergeSort, para poder buscar con
     *     busqueda binaria que unidades se pueden pagar
     */
    public static Mapa construir(List<Territorio> territorios, List<Ruta> rutas, List<Item> catalogo,
                                 CaminosMinimos caminos, Ordenamiento ordenamiento) {
        if (territorios.isEmpty()) {
            throw new IllegalStateException("El mapa no tiene territorios. ¿Está cargada la base?");
        }
        Grafo g = new Grafo(false);
        for (Territorio t : territorios) {
            g.agregarVertice(t.id());
        }
        for (Ruta r : rutas) {
            if (r.costo() <= 0) {
                throw new IllegalStateException("La ruta " + r.origen() + "-" + r.destino()
                        + " tiene costo " + r.costo() + ": los días de marcha tienen que ser positivos.");
            }
            g.agregarArista(r.origen(), r.destino(), r.costo());
        }
        if (g.cantidadVertices() != territorios.size()) {
            throw new IllegalStateException("Hay rutas hacia territorios que no existen.");
        }
        double[][] dist = caminos.floydMatriz(g);
        Comparator<Item> porCosto = Comparator.comparingDouble(Item::getPeso);
        List<Item> catalogoPorCosto = ordenamiento.mergeSort(catalogo, porCosto);
        return new Mapa(new ArrayList<>(territorios), new ArrayList<>(rutas), g, dist, catalogoPorCosto);
    }

    public List<Territorio> territorios() { return territorios; }
    public List<Ruta> rutas() { return rutas; }
    public Grafo grafo() { return grafo; }
    public int cantidad() { return territorios.size(); }
    public Territorio territorio(int i) { return territorios.get(i); }
    public int indice(String id) { return grafo.indiceDe(id); }
    public String id(int i) { return territorios.get(i).id(); }

    /** Distancia minima en dias de marcha por el mapa completo (Floyd). O(1). */
    public double distancia(int desde, int hasta) { return distancias[desde][hasta]; }

    public double[][] matrizDeDistancias() { return distancias; }

    /** El catalogo, ordenado por costo ascendente. */
    public List<Item> catalogo() { return catalogo; }

    public Item unidad(String id) {
        Item u = unidadPorId.get(id);
        if (u == null) {
            throw new IllegalArgumentException("No existe la unidad " + id);
        }
        return u;
    }

    public boolean sonAdyacentes(int a, int b) {
        for (Grafo.Arista ar : grafo.vecinos(a)) {
            if (ar.destino() == b) {
                return true;
            }
        }
        return false;
    }
}
