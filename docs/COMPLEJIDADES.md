# Informe de complejidades (hito 10)

V = territorios · E = rutas · n = unidades / items / elementos a ordenar ·
W = oro disponible (capacidad de la mochila) · k = frentes · m = resultados de
una consulta · s = símbolos distintos · b = jugadas por estado · d = profundidad
del árbol de juego · C = ciudades candidatas a capital.

En el mapa del juego V = 12 y E = 17: el grafo es **ralo** (E ≈ 1,4·V), y por
eso se representa con **lista de adyacencia**.

| Algoritmo | Técnica | Recurrencia | Tiempo | Espacio | Estructura que lo sostiene |
|---|---|---|---|---|---|
| DFS | Recorrido | — | O(V + E) | O(V) | Lista de adyacencia + `boolean[]` + pila de llamadas |
| Componentes conexas | Recorrido (DFS repetido) | — | O(V + E) | O(V) | `boolean[]` compartido entre los DFS |
| BFS | Recorrido | — | O(V + E) | O(V) | Lista de adyacencia + `boolean[]` + cola FIFO |
| BFS por niveles (multi-fuente) | Recorrido | — | O(V + E) | O(V) | Cola FIFO + `int[] nivel` |
| Dijkstra | Greedy | — | O((V + E) log V) | O(V + E) | `PriorityQueue` (heap binario) + `previo[]` |
| UCS | Greedy | — | O((V + E) log V) | O(V + E) | Igual que Dijkstra, con parada temprana |
| Floyd-Warshall | Programación dinámica | d_k(i,j) = min(d_{k−1}(i,j), d_{k−1}(i,k) + d_{k−1}(k,j)) | O(V³) | O(V²) | Matriz `double[V][V]` |
| Dijkstra × V (comparación hito 7) | Greedy | — | O(V·(V + E) log V) | O(V + E) | `PriorityQueue` por cada origen |
| Prim | Greedy | — | O(E log V) | O(V + E) | `PriorityQueue` + `boolean[] enMst` |
| Kruskal | Greedy | — | O(E log E) | O(V + E) | `PriorityQueue` de aristas + Union-Find (`padre[]`, `rango[]`) |
| QuickSort | Divide y vencerás | T(n) = 2T(n/2) + O(n) promedio; T(n) = T(n−1) + O(n) peor | O(n log n) prom. / O(n²) peor | O(log n) + O(n) por la copia | Lista in situ, partición de Lomuto, pivote mediana de tres |
| MergeSort | Divide y vencerás | T(n) = 2T(n/2) + O(n) | O(n log n) siempre | O(n) | Listas auxiliares de la mezcla |
| Búsqueda binaria | Divide y vencerás | T(n) = T(n/2) + O(1) | O(log n) | O(1) | Lista ordenada por costo |
| AVL: inserción | Árbol balanceado | T(h) = T(h−1) + O(1), h = O(log n) | O(log n) | O(n) el árbol, O(log n) la recursión | Nodos con altura + rotaciones |
| AVL: consulta por rango + top-k | Árbol balanceado | — | O(log n + m log k) | O(m + k) | AVL + heap de mínimos de tamaño k |
| Greedy de compra (ratio poder/costo) | Greedy | — | O(n log n) | O(n) | MergeSort + acumuladores |
| Heurística de ataque (ganancia/riesgo) | Greedy | — | O(c log c), c candidatos | O(c) | MergeSort estable |
| Huffman | Greedy | — | O(n + s log s) | O(s) + O(n) de salida | `PriorityQueue` de nodos del árbol |
| Mochila 0/1 | Programación dinámica | dp[i][j] = max(dp[i−1][j], v_i + dp[i−1][j−p_i]) | O(n·W) | O(n·W) | Matriz `double[n+1][W+1]` |
| Rutas simples con poda | Backtracking | T(n) = (n−1)·T(n−1) + O(1) | O(V!) peor caso | O(V) | Recursión + `boolean[] visitado` + camino parcial |
| Generador de escenario | Backtracking (tipo 1) | — | O(C² · V^(2m)) peor caso | O(V) | Recursión + `int[] duenio` + regiones con retroceso |
| Reparto de refuerzos | Branch & Bound | T(i) = k·T(i+1) + O(k) | O(k^n) peor caso | O(n + k) | Recursión + `double[] carga` + cota superior |
| MIN-MAX | Árbol de juego (backtracking tipo 2) | T(d) = b·T(d−1) + O(b·A) | O(b^d) | O(b·d) | Recursión + estado clonable (`int[]`) |
| Alfa-beta | Árbol de juego con poda | T(d) = b·T(d−1) peor; ≈ b^(d/2) con orden óptimo | O(b^d) peor / O(b^(d/2)) mejor | O(b·d) | Ídem + (α, β) por valor |

A = costo de aplicar una jugada: copiar el estado O(V) + abastecimiento O(V + E
log V) + mochila O(n·W). Con V = 12 y n = 6 es una constante chica.

---

## Justificaciones

**DFS y BFS — O(V + E).** Cada vértice se marca visitado exactamente una vez
(V). Desde cada vértice se recorre su lista de adyacencia completa una sola vez;
sumadas, esas listas tienen 2E entradas en un grafo no dirigido. Con matriz de
adyacencia sería O(V²), porque habría que mirar V posiciones por vértice aunque
estén vacías. BFS marca **al encolar**; si marcara al desencolar un vértice
podría entrar varias veces a la cola y la cota dejaría de valer.

**Componentes conexas — O(V + E).** Se lanza un DFS desde cada vértice todavía
no visitado. Aunque haya varios DFS, el `boolean[]` es compartido: cada vértice
se visita una vez en total.

**BFS por niveles — O(V + E).** Es un BFS donde todos los orígenes entran en el
nivel 0. El corte en el nivel k solo reduce trabajo. El nivel de cada vértice es
su distancia en **cantidad de aristas** (conquistas), no en días: para días está Dijkstra.

**Dijkstra — O((V + E) log V).** Cada arista provoca a lo sumo un `push` al heap
(E log V) y cada vértice se cierra una vez (V log V). Se usa *lazy deletion*
(entrada nueva en lugar de decrementar la clave) y se descartan las obsoletas
al sacarlas. Con búsqueda lineal del mínimo sería O(V² + E): declarar
O((V+E) log V) con búsqueda lineal es el ejemplo de "implementación parcial" del
enunciado. Exige pesos no negativos (se valida): con un peso negativo podría
aparecer un camino más barato hacia un vértice ya cerrado.

**Floyd-Warshall — O(V³).** Tres bucles anidados sobre V; el del intermedio k
siempre afuera. Invariante: al terminar la iteración k la matriz tiene los
mínimos usando como intermedios solo vértices ≤ k. Los ciclos negativos aparecen
como `m[i][i] < 0` en la diagonal.

> **Floyd contra Dijkstra V veces (hito 7).** Dijkstra × V cuesta
> O(V·(V + E)·log V). En grafos **ralos** (E ≈ V) es ≈ O(V² log V), mejor que V³.
> En grafos **densos** (E ≈ V²) queda O(V³ log V), peor que Floyd. Medido en el
> mapa: Floyd evalúa 1.728 combinaciones; Dijkstra × 12 expande 144 vértices y
> relaja 408 aristas, con resultados idénticos. En el juego Floyd igual se usa,
> porque se calcula **una vez** por partida y la IA lo consulta en O(1) miles de veces.

**Prim — O(E log V).** Mismo argumento que Dijkstra: la cola guarda aristas
candidatas y cada una entra a lo sumo una vez. La variante con arreglos es O(V²)
y solo conviene si E ≈ V².

**Kruskal — O(E log E).** Dominada por ordenar las E aristas (con un heap). Las
operaciones de Union-Find con compresión de caminos y unión por rango cuestan
O(α(V)) amortizado, la inversa de Ackermann, menor que 5 para cualquier V real.
**Prim vs Kruskal:** Prim conviene en grafos densos (crece un solo árbol y mira
la frontera); Kruskal en ralos (ordenar pocas aristas es barato).

**QuickSort — O(n log n) promedio, O(n²) peor caso.** Con particiones
equilibradas, T(n) = 2T(n/2) + O(n) = O(n log n). Si el pivote es
sistemáticamente el mínimo o el máximo, T(n) = T(n−1) + O(n) = O(n²). La
mediana de tres lo evita en entradas ordenadas o casi ordenadas. No es estable.

**MergeSort — O(n log n) siempre.** T(n) = 2T(n/2) + O(n). Regla práctica:
a = 2, b = 2, k = 1 → a = b^k → O(n^k log n) = O(n log n). No depende de los
datos porque siempre parte por la mitad. Cuesta O(n) de espacio auxiliar. Es
**estable** (toma de la izquierda en los empates), y por eso es el que se usa
cuando el orden de los empatados importa (ranking de amenazas, jugadas de la IA).

**Búsqueda binaria — O(log n).** T(n) = T(n/2) + O(1): a = 1, b = 2, k = 0 →
a = b^k → O(log n). Precondición: lista ordenada por la clave. El catálogo se
ordena una vez al crear la partida (O(n log n)) y después cada turno se busca
en O(log n).

**AVL — O(log n) por inserción, O(log n + m) por rango.** El invariante
|altura(izq) − altura(der)| ≤ 1 en todo nodo garantiza altura ≤ 1,44·log₂(n+2)
(test: 1.000 inserciones ordenadas, el peor caso de un ABB común, quedan
balanceadas). La inserción baja por un camino de altura O(log n) y rebalancea
con a lo sumo dos rotaciones O(1). La consulta por rango no baja a los subárboles
que quedan fuera del rango: recorre el camino hasta los bordes (O(log n)) y los
m nodos que devuelve. El top-k con un heap de mínimos de tamaño k suma O(m log k).

**Greedy de compra — O(n log n).** Ordenar por ratio poder/costo domina; el
barrido es O(n). **No es óptimo en mochila 0/1:** con 6 de oro greedy logra 6 de
poder y la PD 9 (README, hito 6).

**Huffman — O(n + s log s).** Contar frecuencias es O(n). Hay s − 1 fusiones,
cada una con dos `poll` y un `add` sobre un heap de a lo sumo s elementos:
O(s log s). Codificar es O(n · largo del código). Decodificar es O(b) en la
cantidad de bits. Es un greedy que **sí** es óptimo: da el código prefijo de
largo medio mínimo.

**Mochila 0/1 — O(n·W), pseudopolinómica.** Se llenan (n+1)·(W+1) celdas de
O(1). W es un **valor** de la entrada, no su tamaño: escribirlo ocupa log W
bits, así que respecto del tamaño real de la entrada el costo es exponencial.
En el juego W es el oro de un turno (decenas) y la tabla es chica.

**Backtracking de rutas — O(V!) peor caso.** En un grafo completo, desde cada
vértice se puede seguir a cualquiera de los no visitados: T(n) = (n−1)·T(n−1)
+ O(1). La poda por costo no cambia la cota teórica, solo la práctica: 50 nodos
con poda contra 93 sin poda para AZU→BRA con ≤ 22 días y ≤ 6 saltos.

**Generador de escenario — exponencial.** O(C²) pares de capitales; para cada
par, cada región crece m pasos probando O(V) candidatos por paso:
O(C² · V^(2m)). Es de **tipo 1** (corta en la primera solución) y la poda
(capitales cerca, candidatos que tocan al rival, producción que ya se pasó o que
no llega) lo deja en una decena de nodos en el escenario clásico.

**Branch & Bound de reparto — O(k^n) peor caso.** Cada una de las n unidades
puede ir a cualquiera de los k frentes. La cota superior
`min(promedio final de los márgenes, frente más débil + poder restante)` se
calcula en O(k) por nodo, y junto con la poda por simetría (frentes con el mismo
margen son intercambiables) y la solución inicial greedy la exploración real es
mínima: 12 nodos con 4 podas para el catálogo en dos columnas.

**MIN-MAX — O(b^d).** Cada nivel multiplica por la ramificación b y hay d
niveles: T(d) = b·T(d−1) + O(b·A). El espacio es O(b·d): la recursión tiene d
niveles y en cada uno se guarda la lista de b jugadas. No hay retroceso
explícito porque cada hijo es una **copia** del estado (O(V)), que es lo que
hace que el estado chico y clonable sea una decisión de diseño importante.

**Alfa-beta — O(b^d) peor caso, O(b^(d/2)) con el mejor orden.** Si las mejores
jugadas se miran primero, α sube rápido y se cortan casi todos los hermanos: con
el mismo tiempo se llega al doble de profundidad. El orden lo da la heurística
greedy ganancia/riesgo. Medido en el estado inicial: a profundidad 4, 567 nodos
contra 7.408 de MIN-MAX (92 % menos) con el mismo valor y la misma jugada.
