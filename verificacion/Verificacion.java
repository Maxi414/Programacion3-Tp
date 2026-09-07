import uade.prog3.tpo.algorithm.*;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.service.Grafo;

import java.util.*;

/** Corre los 7 algoritmos contra el grafo semilla y contrasta con los valores
 *  que la GUIA-TPO.md del docente da calculados a mano. */
public class Verificacion {

    static int ok = 0, fallo = 0;

    static void check(String que, Object esperado, Object obtenido) {
        boolean pasa = String.valueOf(esperado).equals(String.valueOf(obtenido));
        System.out.printf("%s  %-46s esperado=%s  obtenido=%s%n",
                pasa ? "[OK]  " : "[FALLA]", que, esperado, obtenido);
        if (pasa) ok++; else fallo++;
    }

    /** El mismo grafo que carga CargaInicial. */
    static Grafo semilla(boolean dirigido) {
        Grafo g = new Grafo(dirigido);
        for (String id : List.of("A","B","C","D","E","F","G","H")) g.agregarVertice(id);
        g.agregarArista("A","B",4);  g.agregarArista("A","C",9);
        g.agregarArista("B","C",3);  g.agregarArista("B","D",7);
        g.agregarArista("C","D",2);  g.agregarArista("C","E",11);
        g.agregarArista("D","E",5);  g.agregarArista("D","F",8);
        g.agregarArista("E","F",6);  g.agregarArista("E","G",10);
        g.agregarArista("F","H",3);  g.agregarArista("G","H",4);
        return g;
    }

    /** Los mismos items que carga CargaInicial. */
    static List<Item> items() {
        return List.of(
            new Item("I1","Item 1",5,10,"A"), new Item("I2","Item 2",4,40,"B"),
            new Item("I3","Item 3",6,30,"C"), new Item("I4","Item 4",3,50,"D"),
            new Item("I5","Item 5",7,55,"E"));
    }

    static List<String> ids(List<Item> is) {
        List<String> r = new ArrayList<>();
        for (Item i : is) r.add(i.getId());
        return r;
    }

    public static void main(String[] args) {
        Grafo dir = semilla(true), noDir = semilla(false);

        System.out.println("\n=== ESTRUCTURA (hito 1) ===");
        check("vertices", 8, dir.cantidadVertices());
        check("aristas dirigido", 12, dir.cantidadAristas());
        check("aristas no dirigido", 12, noDir.cantidadAristas());

        System.out.println("\n=== RECORRIDOS (hito 4, 2 puntos) ===");
        Recorridos rec = new Recorridos();
        check("dfs(A)", "[A, B, C, D, E, F, H, G]", rec.dfs(dir,"A"));
        check("bfs(A)", "[A, B, C, D, E, F, G, H]", rec.bfs(dir,"A"));
        check("dfs recursivo == dfs iterativo", rec.dfs(dir,"A"), rec.dfsIterativo(dir,"A"));

        System.out.println("\n=== CAMINOS MINIMOS (hito 5) ===");
        CaminosMinimos cm = new CaminosMinimos();
        var ac = cm.dijkstra(dir,"A","C");
        check("dijkstra(A,C) costo  [no el directo 9]", 7.0, ac.costoTotal());
        check("dijkstra(A,C) camino", "[A, B, C]", ac.vertices());
        var ah = cm.dijkstra(dir,"A","H");
        check("dijkstra(A,H) costo", 20.0, ah.costoTotal());
        check("dijkstra(A,H) camino", "[A, B, C, D, F, H]", ah.vertices());
        var floyd = cm.floydWarshall(dir);
        check("floyd A->H", 20.0, floyd.get("A->H"));
        check("floyd A->C", 7.0, floyd.get("A->C"));
        check("floyd coincide con dijkstra en A->G", cm.dijkstra(dir,"A","G").costoTotal(), floyd.get("A->G"));
        check("ucs expande menos que dijkstra completo", true,
              cm.expandidosUcs(dir,"A","C") < cm.expandidosDijkstraCompleto(dir,"A"));

        System.out.println("\n=== MST (hito 5) ===");
        ArbolRecubrimiento ar = new ArbolRecubrimiento();
        var prim = ar.prim(noDir,"A");
        check("prim(A) costo total", 27.0, prim.costoTotal());
        check("prim(A) cantidad de aristas (V-1)", 7, prim.aristas().size());
        var krus = ar.kruskal(noDir);
        check("kruskal costo total", 27.0, krus.costoTotal());
        check("kruskal cantidad de aristas", 7, krus.aristas().size());
        check("prim y kruskal coinciden en costo", prim.costoTotal(), krus.costoTotal());

        System.out.println("\n=== ORDENAMIENTO (hito 2, 1 punto) ===");
        Ordenamiento ord = new Ordenamiento();
        Comparator<Item> porPeso = Comparator.comparingDouble(Item::getPeso);
        check("quickSort por peso", "[I4, I2, I1, I3, I5]", ids(ord.quickSort(items(), porPeso)));
        check("mergeSort por peso", "[I4, I2, I1, I3, I5]", ids(ord.mergeSort(items(), porPeso)));
        check("quickSort no modifica la entrada", "[I1, I2, I3, I4, I5]", ids(items()));

        System.out.println("\n=== GREEDY vs PD (hitos 3 y 6) ===");
        Seleccion sel = new Seleccion(ord);
        var g = sel.greedy(items(), 10);
        check("greedy(10) valor", 90.0, g.valorTotal());
        check("greedy(10) peso",   7.0, g.pesoTotal());
        check("greedy(10) elegidos", "[I4, I2]", ids(g.elegidos()));
        var pd = sel.programacionDinamica(items(), 10);
        check("PD(10) valor [OPTIMO]", 105.0, pd.valorTotal());
        check("PD(10) peso", 10.0, pd.pesoTotal());
        check("PD(10) elegidos", "[I4, I5]", ids(pd.elegidos()));
        check("greedy NO alcanza el optimo", true, g.valorTotal() < pd.valorTotal());

        System.out.println("\n=== BACKTRACKING (hito 8, 1 punto) ===");
        Backtracking bt = new Backtracking();
        var rutas = bt.rutasSimples(dir,"A","H",100,10);
        check("rutas A->H sin restriccion real", 13, rutas.size());
        boolean todasSimples = true, todasEmpiezanYTerminan = true;
        for (var r : rutas) {
            todasSimples &= new HashSet<>(r.vertices()).size() == r.vertices().size();
            todasEmpiezanYTerminan &= r.vertices().get(0).equals("A")
                    && r.vertices().get(r.vertices().size()-1).equals("H");
        }
        check("todas las rutas son simples", true, todasSimples);
        check("todas van de A a H", true, todasEmpiezanYTerminan);
        var rutas25 = bt.rutasSimples(dir,"A","H",25,10);
        check("con costoMaximo=25 quedan menos", true, rutas25.size() < rutas.size());
        var comp = bt.compararPoda(dir,"A","H",25,10);
        check("la poda explora menos nodos", true, comp.exploradosConPoda() < comp.exploradosSinPoda());
        System.out.printf("       -> con poda=%d  sin poda=%d  ahorro=%.2f%%  rutas=%d%n",
                comp.exploradosConPoda(), comp.exploradosSinPoda(),
                comp.porcentajeAhorrado(), comp.rutasEncontradas());

        System.out.println("\n=== BRANCH & BOUND (hito 11, 1 punto) ===");
        RamificacionYPoda byp = new RamificacionYPoda(ord);
        var asig = byp.repartir(items(), 3);
        check("repartir en 3: makespan optimo", 9.0, asig.cargaMaxima());
        double suma = 0; for (Item i : items()) suma += i.getPeso();
        check("ningun item se pierde ni se duplica", 5,
              asig.porContenedor().values().stream().mapToInt(List::size).sum());
        check("makespan >= media (cota valida)", true, asig.cargaMaxima() >= suma/3);
        check("la poda actuo", true, asig.nodosPodados() > 0);
        System.out.printf("       -> explorados=%d  podados=%d  cota inicial LPT=%.1f  optimo=%.1f%n",
                asig.nodosExplorados(), asig.nodosPodados(), asig.cotaInicial(), asig.cargaMaxima());
        var asig1 = byp.repartir(items(), 1);
        check("repartir en 1 contenedor = suma total", 25.0, asig1.cargaMaxima());

        System.out.println("\n=== VALIDACIONES DE ERROR ===");
        Grafo neg = new Grafo(true);
        neg.agregarArista("X","Y",-5);
        try { cm.dijkstra(neg,"X","Y"); check("dijkstra rechaza peso negativo", "excepcion", "no lanzo"); }
        catch (IllegalArgumentException e) { check("dijkstra rechaza peso negativo", "excepcion", "excepcion"); }
        try { ar.prim(dir,"A"); check("prim rechaza grafo dirigido", "excepcion", "no lanzo"); }
        catch (IllegalArgumentException e) { check("prim rechaza grafo dirigido", "excepcion", "excepcion"); }
        Grafo desconexo = new Grafo(false);
        desconexo.agregarArista("P","Q",1); desconexo.agregarVertice("Z");
        try { ar.kruskal(desconexo); check("kruskal detecta grafo no conexo", "excepcion", "no lanzo"); }
        catch (IllegalStateException e) { check("kruskal detecta grafo no conexo", "excepcion", "excepcion"); }
        try { cm.dijkstra(dir,"H","A"); check("dijkstra sin camino falla claro", "excepcion", "no lanzo"); }
        catch (IllegalStateException e) { check("dijkstra sin camino falla claro", "excepcion", "excepcion"); }

        System.out.println("\n========================================");
        System.out.printf("RESULTADO: %d OK, %d FALLAS%n", ok, fallo);
        System.out.println("========================================");
        if (fallo > 0) System.exit(1);
    }
}
