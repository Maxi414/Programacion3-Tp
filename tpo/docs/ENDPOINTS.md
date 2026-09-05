# Documentación de endpoints

Todos los ejemplos de esta ficha están calculados sobre el **grafo semilla**
del scaffold (8 vértices, 12 aristas) y sus 5 items. Cuando el grupo cargue su
propio dominio hay que regenerar los valores de las respuestas, no la estructura
de las fichas.

Grafo de referencia: `A-B(4) A-C(9) B-C(3) B-D(7) C-D(2) C-E(11) D-E(5) D-F(8) E-F(6) E-G(10) F-H(3) G-H(4)`
Items de referencia: `I1(5,10) I2(4,40) I3(6,30) I4(3,50) I5(7,55)` como `(peso, valor)`

---

### GET /api/grafo/resumen

Metadatos del grafo cargado. Sirve para verificar la conexión a la base.

**Parámetros:** `dirigido` (opcional, por defecto `true`)

**Ejemplo:** `GET /api/grafo/resumen`

**Respuesta:**
```json
{ "vertices": 8, "aristas": 12, "dirigido": true, "ids": ["A","B","C","D","E","F","G","H"] }
```

---

### GET /api/grafo/dfs

Recorrido en profundidad desde un nodo.

**Parámetros:** `origen` (id del nodo de partida)

**Ejemplo:** `GET /api/grafo/dfs?origen=A`

**Respuesta:**
```json
["A","B","C","D","E","F","H","G"]
```

**Complejidad:** O(V + E) — cada vértice se marca una sola vez y la lista de
adyacencia de cada uno se recorre una sola vez, lo que suma E en total.
**Espacio:** O(V) por el arreglo de visitados más la pila de llamadas, de
profundidad V en el peor caso.
**Estructura usada:** recursión (pila de llamadas implícita) + `boolean[] visitado`.

---

### GET /api/grafo/bfs

Recorrido en anchura desde un nodo.

**Parámetros:** `origen` (id del nodo de partida)

**Ejemplo:** `GET /api/grafo/bfs?origen=A`

**Respuesta:**
```json
["A","B","C","D","E","F","G","H"]
```

**Complejidad:** O(V + E) — mismo argumento que DFS.
**Espacio:** O(V) por el arreglo de visitados y la cola.
**Estructura usada:** cola FIFO (`ArrayDeque`) + `boolean[] visitado`.

**Nota:** se marca visitado **al encolar**, no al desencolar. Marcando al
desencolar, un mismo vértice entra varias veces a la cola y aparecen duplicados
en el orden de visita.

**Diferencia con Dijkstra:** BFS minimiza la cantidad de aristas, no el costo.
En este grafo `A→C` es 1 arista y cuesta 9, mientras que `A→B→C` son 2 aristas y
cuesta 7.

---

### GET /api/grafo/dijkstra

Camino de costo mínimo entre dos nodos, con el camino reconstruido.

**Parámetros:** `origen` (id), `destino` (id)

**Ejemplo:** `GET /api/grafo/dijkstra?origen=A&destino=H`

**Respuesta:**
```json
{ "vertices": ["A","B","C","D","F","H"], "costoTotal": 20.0 }
```

**Ejemplo de verificación:** `GET /api/grafo/dijkstra?origen=A&destino=C` devuelve
costo **7** por `A→B→C`, no 9 por el arco directo. Si devuelve 9, la relajación
de aristas está mal.

**Complejidad:** O((V + E) log V) — cada arista provoca a lo sumo un `push` al
heap (E log V) y cada vértice se cierra una vez (V log V).
**Espacio:** O(V) para `dist[]`, `previo[]` y `listo[]`, más O(E) de cola.
**Estructura usada:** `PriorityQueue` (heap binario) + arreglo de predecesores.

**Errores manejados:**
- arista de costo negativo → `400` (Dijkstra no es aplicable)
- destino inalcanzable → `422`
- id inexistente → `400`

---

### GET /api/grafo/floyd

Costo mínimo entre todos los pares de vértices.

**Parámetros:** ninguno

**Ejemplo:** `GET /api/grafo/floyd`

**Respuesta (35 pares alcanzables, recortada):**
```json
{ "A->B": 4.0, "A->C": 7.0, "A->H": 20.0, "B->H": 16.0, "C->G": 17.0 }
```

**Complejidad:** O(V³) en tiempo, O(V²) en espacio.
**Estructura usada:** matriz de adyacencia (`grafo.matrizDeAdyacencia()`).

**Recurrencia:** `d_k(i,j) = min( d_{k-1}(i,j) , d_{k-1}(i,k) + d_{k-1}(k,j) )`
El bucle del vértice intermedio `k` va **siempre por fuera** de los de `i` y `j`:
el invariante es que al terminar la iteración `k`, la matriz tiene los mínimos
usando como intermedios solo vértices de índice ≤ k.

**Ciclos negativos:** se detectan revisando la diagonal al terminar. Si algún
`m[i][i] < 0`, se pudo dar la vuelta abaratando y el camino mínimo no está
definido → `422`.

**Pares inalcanzables:** se omiten del mapa, porque `Infinity` no es JSON válido.

---

### GET /api/grafo/ucs

Igual que Dijkstra pero con parada temprana al cerrar el destino.

**Parámetros:** `origen` (id), `destino` (id)

**Ejemplo:** `GET /api/grafo/ucs?origen=A&destino=C`

**Respuesta:**
```json
{ "vertices": ["A","B","C"], "costoTotal": 7.0 }
```

**Complejidad:** la misma cota O((V + E) log V), pero expande menos vértices.
**Evidencia medida sobre el grafo semilla:** para `A→C`, UCS cierra **3**
vértices; Dijkstra recorriendo todo el grafo desde A cierra **8**.

---

### GET /api/grafo/prim

Árbol de recubrimiento mínimo por Prim. El grafo se carga **no dirigido**.

**Parámetros:** `origen` (id del vértice desde el que crece el árbol)

**Ejemplo:** `GET /api/grafo/prim?origen=A`

**Respuesta:**
```json
{ "aristas": [
    {"origen":"A","destino":"B","costo":4.0}, {"origen":"B","destino":"C","costo":3.0},
    {"origen":"C","destino":"D","costo":2.0}, {"origen":"D","destino":"E","costo":5.0},
    {"origen":"E","destino":"F","costo":6.0}, {"origen":"F","destino":"H","costo":3.0},
    {"origen":"H","destino":"G","costo":4.0} ],
  "costoTotal": 27.0 }
```

**Complejidad:** O(E log V) con cola de prioridad. La variante con búsqueda
lineal del mínimo es O(V²) y solo conviene en grafos densos (E ≈ V²).
Acá está implementada la versión con heap.
**Espacio:** O(V + E).
**Estructura usada:** `PriorityQueue` + arreglo `enMst[]`.

**Invariante (propiedad del corte):** la arista de costo mínimo que cruza el
corte entre lo que ya está en el árbol y lo que está afuera pertenece a algún
MST, así que tomarla nunca se lamenta.

**Errores manejados:** grafo dirigido → `400`; grafo no conexo → `422`.

---

### GET /api/grafo/kruskal

Árbol de recubrimiento mínimo por Kruskal, con Union-Find propio.

**Parámetros:** ninguno

**Ejemplo:** `GET /api/grafo/kruskal`

**Respuesta:**
```json
{ "aristas": [
    {"origen":"C","destino":"D","costo":2.0}, {"origen":"B","destino":"C","costo":3.0},
    {"origen":"F","destino":"H","costo":3.0}, {"origen":"A","destino":"B","costo":4.0},
    {"origen":"G","destino":"H","costo":4.0}, {"origen":"D","destino":"E","costo":5.0},
    {"origen":"E","destino":"F","costo":6.0} ],
  "costoTotal": 27.0 }
```

**Complejidad:** O(E log E), dominada por el orden de las aristas. Las
operaciones de Union-Find con compresión de caminos y unión por rango son
O(α(V)), inversa de Ackermann, menor que 5 para cualquier V real.
**Espacio:** O(V + E).
**Estructura usada:** `PriorityQueue` sobre las aristas + Union-Find propio
(`padre[]` y `rango[]`). Se usa un heap en lugar de `Collections.sort` porque el
enunciado prohíbe apoyarse en librerías de ordenamiento.

**Detección de ciclo:** `find(u) == find(v)`.

**Prim contra Kruskal:** Prim conviene en grafos **densos**, porque hace crecer
un solo árbol y solo mira la frontera. Kruskal conviene en grafos **ralos**,
donde ordenar pocas aristas es barato.

**Nota:** Prim y Kruskal dan el mismo costo total (27) pero pueden devolver
conjuntos de aristas distintos, porque hay pesos repetidos (dos aristas de costo
3 y dos de costo 4).

---

### GET /api/seleccion/greedy

Selección de items por ratio valor/peso bajo restricción de capacidad.

**Parámetros:** `capacidad` (número)

**Ejemplo:** `GET /api/seleccion/greedy?capacidad=10`

**Respuesta:**
```json
{ "elegidos": [ {"id":"I4",...}, {"id":"I2",...} ],
  "valorTotal": 90.0, "pesoTotal": 7.0, "tecnica": "GREEDY (ratio valor/peso)" }
```

**Función objetivo:** maximizar la suma de valores, sujeto a que la suma de
pesos no supere la capacidad.
**Criterio:** ratio valor/peso descendente, no valor a secas. Ordenar por valor
elegiría primero un item caro y pesado que bloquea la capacidad.
**Complejidad:** O(n log n), dominada por el ordenamiento; el barrido es O(n).
**Estructura usada:** MergeSort propio + acumuladores.

**CONTRAEJEMPLO — greedy no da el óptimo (hito 6):**

| Item | Peso | Valor | Ratio |
|---|---|---|---|
| I4 | 3 | 50 | 16.67 |
| I2 | 4 | 40 | 10.00 |
| I5 | 7 | 55 | 7.86 |
| I3 | 6 | 30 | 5.00 |
| I1 | 5 | 10 | 2.00 |

Con capacidad 10, greedy toma **I4** y después **I2**: peso 7, valor **90**, y
deja 3 de capacidad sin usar. El óptimo real es **{I4, I5}**: peso 10, valor
**105**. Greedy pierde **15** porque su decisión es irrevocable: no revisa si
dejar I2 afuera habría permitido entrar I5.

Sobre items **divisibles** (mochila fraccional) greedy sí es óptimo, porque el
último item se puede partir y la capacidad nunca queda desperdiciada.

---

### GET /api/seleccion/dinamica

Mochila 0/1 resuelta con programación dinámica, con recuperación del camino.

**Parámetros:** `capacidad` (entero)

**Ejemplo:** `GET /api/seleccion/dinamica?capacidad=10`

**Respuesta:**
```json
{ "elegidos": [ {"id":"I4",...}, {"id":"I5",...} ],
  "valorTotal": 105.0, "pesoTotal": 10.0,
  "tecnica": "PROGRAMACION DINAMICA (mochila 0/1)" }
```

**Definición:** `dp[i][j]` es el máximo valor alcanzable considerando los
primeros `i` items con capacidad `j` disponible.

**Recurrencia:**
```
dp[i][j] = dp[i-1][j]                                    si peso_i > j
dp[i][j] = max( dp[i-1][j] , valor_i + dp[i-1][j-peso_i] ) si entra
```

**Tabla dp del ejemplo** (filas = items en orden I1..I5, columnas = capacidad 0..10):

| i \ j | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ∅ | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| I1 (5,10) | 0 | 0 | 0 | 0 | 0 | 10 | 10 | 10 | 10 | 10 | 10 |
| I2 (4,40) | 0 | 0 | 0 | 0 | 40 | 40 | 40 | 40 | 40 | 50 | 50 |
| I3 (6,30) | 0 | 0 | 0 | 0 | 40 | 40 | 40 | 40 | 40 | 50 | 70 |
| I4 (3,50) | 0 | 0 | 0 | 50 | 50 | 50 | 50 | 90 | 90 | 90 | 90 |
| I5 (7,55) | 0 | 0 | 0 | 50 | 50 | 50 | 50 | 90 | 90 | 90 | **105** |

**Recuperación del camino:** se recorre la tabla hacia atrás desde `dp[n][W]`.
Si `dp[i][j] != dp[i-1][j]`, el item `i` fue necesario: se lo agrega y se
descuenta su peso.

**Complejidad:** O(n·W) en tiempo y espacio.
**Por qué es pseudopolinómica:** W es un **valor** de la entrada, no su tamaño.
Escribir W en binario ocupa log(W) bits, así que el costo es exponencial
respecto del tamaño real de la entrada: con W = 1.000.000 la tabla es
inmanejable aunque haya solo 5 items.

**Errores manejados:** pesos no enteros → `400` con indicación de discretizar.

---

### GET /api/seleccion/quicksort

QuickSort propio sobre los items.

**Parámetros:** `criterio` — `valor` | `peso` | `ratio` (por defecto `ratio`)

**Ejemplo:** `GET /api/seleccion/quicksort?criterio=ratio`

**Respuesta:** `["I1","I3","I5","I2","I4"]` (ascendente por ratio)

**Estrategia de pivote:** mediana de tres (primero, medio, último). Con pivote
fijo, una lista ya ordenada produce particiones de tamaño 0 y n−1, que es
exactamente el peor caso.
**Partición:** Lomuto.
**Complejidad:** O(n log n) promedio, O(n²) peor caso. El peor caso ocurre
cuando el pivote resulta ser sistemáticamente el mínimo o el máximo:
`T(n) = T(n−1) + O(n) = O(n²)`.
**Espacio:** O(log n) promedio por la recursión, más O(n) por la copia (no se
modifica la lista de entrada).

---

### GET /api/seleccion/mergesort

MergeSort propio sobre los items.

**Parámetros:** `criterio` — `valor` | `peso` | `ratio` (por defecto `ratio`)

**Ejemplo:** `GET /api/seleccion/mergesort?criterio=peso`

**Respuesta:** `["I4","I2","I1","I3","I5"]`

**Complejidad:** O(n log n) **siempre**, sin peor caso degradado, porque la
división es siempre por la mitad y no depende de los datos.
**Recurrencia:** `T(n) = 2·T(n/2) + O(n)`.
Con la regla práctica (a=2, b=2, k=1): `a = b^k` (2 = 2¹), entonces
`T(n) = O(n^k · log n) = O(n log n)`.
**Espacio:** O(n) por los arreglos auxiliares de la mezcla. Esa es la desventaja
frente a QuickSort, que ordena in situ.
**Estabilidad:** en la mezcla se toma del lado izquierdo cuando los elementos
comparan igual (`compare <= 0`), lo que preserva el orden relativo original.

---

### GET /api/grafo/rutas

Backtracking: todos los caminos simples bajo restricción de costo y saltos.

**Parámetros:** `origen`, `destino`, `costoMaximo` (por defecto 1000),
`saltosMaximos` (por defecto 10)

**Ejemplo:** `GET /api/grafo/rutas?origen=A&destino=H&costoMaximo=25&saltosMaximos=10`

**Respuesta (6 rutas):**
```json
[ {"vertices":["A","B","C","D","E","F","H"],"costoTotal":23.0},
  {"vertices":["A","B","C","D","F","H"],"costoTotal":20.0},
  {"vertices":["A","B","D","E","F","H"],"costoTotal":25.0},
  {"vertices":["A","B","D","F","H"],"costoTotal":22.0},
  {"vertices":["A","C","D","E","F","H"],"costoTotal":25.0},
  {"vertices":["A","C","D","F","H"],"costoTotal":22.0} ]
```

Sin restricción de costo hay **13** caminos simples de A a H. La más barata de
las 13 es `A→B→C→D→F→H` con costo 20, que coincide con lo que devuelve Dijkstra.

**NODOS EXPLORADOS CON Y SIN PODA (hito 8):**

| | Nodos explorados |
|---|---|
| Con poda por costo | **26** |
| Sin poda | **38** |
| Ahorro | **31,58 %** |

Las dos corridas encuentran las mismas 6 rutas: la poda no descarta soluciones
válidas, porque con costos positivos un camino parcial que ya se pasó del máximo
no puede volver a bajar.

**Complejidad:** O(V!) en el peor caso. En un grafo completo, desde cada vértice
se puede seguir a cualquiera de los no visitados y la cantidad de caminos
simples crece factorialmente. La poda no cambia esa cota teórica, solo el
comportamiento práctico.
**Espacio:** O(V) por el camino parcial y el arreglo de visitados.
**Estructura usada:** recursión + `boolean[] visitado` + lista del camino parcial.

**Los tres elementos del backtracking:** construcción incremental (agregar un
vértice), poda por viabilidad (cortar si el costo parcial ya se pasó), y
**retroceso** (desmarcar el visitado y sacar el vértice al volver). Sin el
retroceso, el vértice queda bloqueado para las ramas hermanas y se pierden
soluciones.

---

### GET /api/seleccion/repartir

Branch & Bound: repartir items entre k contenedores minimizando la carga del
contenedor más cargado (makespan, P||Cmax).

**Parámetros:** `contenedores` (entero ≥ 1)

**Ejemplo:** `GET /api/seleccion/repartir?contenedores=3`

**Respuesta:**
```json
{ "porContenedor": {
    "contenedor-0": [ {"id":"I5"} ],
    "contenedor-1": [ {"id":"I3"}, {"id":"I4"} ],
    "contenedor-2": [ {"id":"I1"}, {"id":"I2"} ] },
  "cargaMaxima": 9.0, "nodosExplorados": 4, "nodosPodados": 6, "cotaInicial": 9.0 }
```

**Verificación:** los pesos 5, 4, 6, 3, 7 suman 25. Con 3 contenedores el
makespan mínimo posible es **9**: con un máximo de 8 la capacidad total sería
24 < 25, imposible.

**CUÁL ES LA COTA Y POR QUÉ ES OPTIMISTA:**
```
cota = max( carga máxima actual , suma de todos los pesos / k )
```
Los dos términos son cotas **inferiores** verdaderas del makespan final:
- la carga máxima actual ya está puesta y solo puede crecer al agregar items
- ni el reparto perfecto puede bajar de la media, porque el trabajo total es
  fijo y hay k contenedores

Al ser una cota inferior real, si ya es ≥ al mejor makespan conocido, **ninguna**
hoja de esa rama puede mejorarlo, y podar es seguro. Si la cota fuera pesimista
(sobrestimara el potencial de la rama) se podarían ramas que contienen el óptimo
y el resultado dejaría de ser óptimo.

**Cota inicial:** se arranca con una solución LPT (Longest Processing Time:
items de mayor a menor, cada uno al contenedor menos cargado), que da 9 de
entrada. Así la poda funciona desde el primer nodo en vez de tener que descubrir
una solución primero.

**Poda por simetría:** los contenedores son idénticos, así que poner el primer
item en el contenedor 0 o en el 2 da soluciones equivalentes. Se permite usar un
contenedor vacío solo si es el primero vacío. Sin esto se exploran k! copias del
mismo reparto.

**Nodos explorados: 4. Nodos podados: 6.**

**Complejidad:** O(k^n) en el peor caso, mucho menor con la poda.
**Espacio:** O(n + k) más la pila de recursión, de profundidad n.

**Diferencia con Backtracking:** allá se poda por **viabilidad** (esta rama
viola una restricción); acá se poda por **optimalidad** (esta rama no puede
mejorar lo que ya tengo).
