# Informe de complejidades (hito 10)

V = cantidad de vértices · E = cantidad de aristas · n = cantidad de items
W = capacidad de la mochila · k = cantidad de contenedores

| Algoritmo | Técnica | Recurrencia | Tiempo | Espacio | Estructura que lo sostiene |
|---|---|---|---|---|---|
| DFS | Recorrido | — | O(V + E) | O(V) | Lista de adyacencia + `boolean[]` + pila de llamadas |
| BFS | Recorrido | — | O(V + E) | O(V) | Lista de adyacencia + `boolean[]` + cola FIFO |
| Dijkstra | Greedy | — | O((V + E) log V) | O(V + E) | `PriorityQueue` (heap binario) + `previo[]` |
| UCS | Greedy | — | O((V + E) log V) | O(V + E) | Igual que Dijkstra, con parada temprana |
| Prim | Greedy | — | O(E log V) | O(V + E) | `PriorityQueue` + `enMst[]` |
| Kruskal | Greedy | — | O(E log E) | O(V + E) | `PriorityQueue` + Union-Find (`padre[]`, `rango[]`) |
| QuickSort | Divide y vencerás | T(n) = 2T(n/2) + O(n) *(caso promedio)* | O(n log n) prom. / O(n²) peor | O(log n) + O(n) por la copia | Arreglo in situ + partición de Lomuto |
| MergeSort | Divide y vencerás | T(n) = 2T(n/2) + O(n) | O(n log n) siempre | O(n) | Listas auxiliares para la mezcla |
| Selección greedy | Greedy | — | O(n log n) | O(n) | MergeSort propio + acumuladores |
| Mochila 0/1 | Programación dinámica | dp[i][j] = max(dp[i−1][j], v_i + dp[i−1][j−p_i]) | O(n·W) | O(n·W) | Matriz `double[n+1][W+1]` |
| Floyd-Warshall | Programación dinámica | d_k(i,j) = min(d_{k−1}(i,j), d_{k−1}(i,k) + d_{k−1}(k,j)) | O(V³) | O(V²) | Matriz de adyacencia |
| Backtracking de rutas | Backtracking | T(n) = (n−1)·T(n−1) + O(1) | O(V!) peor caso | O(V) | Recursión + `boolean[] visitado` + camino parcial |
| Branch & Bound | Ramificación y poda | — | O(k^n) peor caso | O(n + k) | Recursión + `carga[]` + cota inferior |

---

## Justificaciones

**DFS y BFS — O(V + E).** Cada vértice se marca visitado exactamente una vez, lo
que aporta V. Desde cada vértice se recorre su lista de adyacencia completa una
sola vez; sumadas sobre todos los vértices, esas listas contienen E entradas en
total. Con matriz de adyacencia en lugar de listas sería O(V²), porque habría
que revisar V posiciones por vértice aunque estén vacías.

**Dijkstra — O((V + E) log V).** Cada arista puede provocar a lo sumo un `push`
al heap, y cada `push` cuesta log de la cantidad de elementos, acotada por V:
eso da E log V. Cada vértice se extrae y se cierra una vez: V log V. La
implementación usa *lazy deletion* (se pushea una entrada nueva en vez de
decrementar la clave) y descarta las obsoletas al extraerlas.

> Si en lugar de un heap se usara búsqueda lineal del mínimo, la complejidad
> real sería O(V² + E). Declarar O((V+E)·log V) con búsqueda lineal es
> exactamente el ejemplo de "implementación parcial" que da el enunciado y vale
> 50 % del punto.

**Prim — O(E log V).** Mismo argumento que Dijkstra: la cola contiene aristas
candidatas y cada una entra a lo sumo una vez. La variante con arreglos y
búsqueda lineal del mínimo es O(V²), preferible solo si el grafo es denso
(E ≈ V²), porque ahí V² < E log V.

**Kruskal — O(E log E).** Dominada por el ordenamiento de las E aristas. Las
operaciones de Union-Find con compresión de caminos y unión por rango tienen
costo amortizado O(α(V)), la inversa de Ackermann, que para cualquier V real es
menor que 5 y se puede tratar como constante.

**QuickSort — O(n log n) promedio, O(n²) peor caso.** Con particiones
equilibradas la recurrencia es T(n) = 2T(n/2) + O(n), que da n log n. El peor
caso aparece cuando el pivote es sistemáticamente el mínimo o el máximo: la
recurrencia degenera a T(n) = T(n−1) + O(n) = O(n²). La elección de pivote por
mediana de tres evita ese caso en entradas ordenadas o casi ordenadas, que son
las más frecuentes en la práctica.

**MergeSort — O(n log n) siempre.** T(n) = 2T(n/2) + O(n). Con la regla práctica
(a = 2, b = 2, k = 1): como a = b^k, o sea 2 = 2¹, resulta
T(n) = O(n^k · log n) = O(n log n). No tiene peor caso degradado porque la
división es siempre por la mitad y no depende de los datos. El precio es O(n) de
espacio auxiliar, frente al ordenamiento in situ de QuickSort.

**Mochila 0/1 — O(n·W), pseudopolinómica.** Se llenan n·W celdas y cada una
cuesta O(1). Es pseudopolinómica porque W es un **valor** de la entrada, no su
tamaño: escribir W en binario ocupa log(W) bits, así que respecto del tamaño
real de la entrada el costo es exponencial. Con W = 1.000.000 la tabla es
inmanejable aunque haya solo 5 items.

**Floyd-Warshall — O(V³).** Tres bucles anidados sobre V. El del vértice
intermedio k va siempre por fuera: el invariante es que al terminar la
iteración k, la matriz contiene los mínimos usando como intermedios solo
vértices de índice ≤ k. Con k adentro se mezclan subproblemas de distinto nivel
y la matriz queda mal aunque los números parezcan razonables.

> **Floyd contra correr Dijkstra V veces:** Dijkstra V veces cuesta
> O(V·(V + E)·log V). En grafos **ralos** (E ≈ V) eso es ≈ O(V² log V), mejor
> que O(V³). En grafos **densos** (E ≈ V²) queda O(V³ log V), peor que Floyd.
> Además Floyd tolera pesos negativos mientras no haya ciclos negativos, y
> Dijkstra no.

**Backtracking — O(V!) en el peor caso.** En un grafo completo, desde cada
vértice se puede continuar hacia cualquiera de los no visitados: la cantidad de
caminos simples crece factorialmente. La poda por costo no cambia la cota
teórica, solo el comportamiento práctico. Medido sobre el grafo semilla con
costoMáximo = 25: 26 nodos explorados con poda contra 38 sin poda, un 31,58 %
menos, encontrando las mismas 6 rutas.

**Branch & Bound — O(k^n) en el peor caso.** Cada uno de los n items puede ir a
cualquiera de los k contenedores. Con la cota inferior
`max(carga máxima actual, suma total / k)` más la poda por simetría entre
contenedores idénticos, la exploración real es órdenes de magnitud menor:
sobre los 5 items semilla con k = 3 se exploran 4 nodos y se podan 6.
