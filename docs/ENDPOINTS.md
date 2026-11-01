# Documentación de endpoints

Todos los ejemplos están calculados sobre el **mapa del juego** (12
territorios, 17 rutas, costo = días de marcha) y el **catálogo de 6 unidades**
(peso = costo en oro, valor = poder) que carga `CargaInicial`. Las respuestas
son las reales; algunas se recortan con `...`.

Notación: V = territorios (12), E = rutas (17), n = unidades o items,
W = oro disponible, k = frentes / columnas, b = jugadas por estado, d = profundidad.

Errores manejados en todos los endpoints (`config/ManejadorDeErrores`): id
inexistente o dato inválido → `400`; operación imposible en el estado actual
(sin camino, grafo no conexo, ciclo negativo, fase equivocada, partida
terminada) → `422`. Nunca se devuelve un stack trace.

- [Grafo del mapa](#grafo-del-mapa): resumen, BFS, DFS, alcance, componentes, Dijkstra, Floyd, comparación Floyd/Dijkstra, UCS, Prim, Kruskal, rutas, poda
- [Catálogo de unidades](#catálogo-de-unidades): greedy, mochila, QuickSort, MergeSort, búsqueda binaria, Branch & Bound, Huffman
- [Juego](#juego): partida, acciones, y una consulta por algoritmo (Dijkstra, mochila, greedy, AVL, BFS, DFS+MST, B&B, ordenamiento, MIN-MAX/alfa-beta, Huffman, backtracking)

---

## Grafo del mapa

El mapa es **no dirigido**: todos los endpoints lo cargan con `grafoService.cargar(false)`.

### GET /api/grafo/resumen

Metadatos del grafo cargado. Sirve para verificar la conexión a la base.

**Parámetros:** `dirigido` (opcional, por defecto `false`)

**Ejemplo:** `GET /api/grafo/resumen`

**Respuesta:**
```json
{ "vertices": 12, "aristas": 17, "dirigido": false,
  "ids": ["AZU","BRA","CON","COR","DOR","EBA","HEL","IGN","NIE","PIN","SEL","VAD"] }
```

**Complejidad:** O(V + E) para armar la lista de adyacencia.

---

### GET /api/grafo/dfs

Recorrido en profundidad desde un territorio.

**Parámetros:** `origen` (id)

**Ejemplo:** `GET /api/grafo/dfs?origen=AZU`

**Respuesta:**
```json
["AZU","PIN","CON","COR","IGN","VAD","SEL","BRA","DOR","EBA","NIE","HEL"]
```

**Complejidad:** O(V + E) — cada vértice se marca una sola vez y la lista de
adyacencia de cada uno se recorre una sola vez, lo que suma 2E entradas en un
grafo no dirigido.
**Espacio:** O(V) por el arreglo de visitados más la pila de llamadas, de
profundidad V en el peor caso.
**Estructura usada:** recursión (pila de llamadas implícita) + `boolean[] visitado`
+ lista de adyacencia.

---

### GET /api/grafo/bfs

Recorrido en anchura desde un territorio.

**Parámetros:** `origen` (id)

**Ejemplo:** `GET /api/grafo/bfs?origen=AZU`

**Respuesta:**
```json
["AZU","PIN","VAD","CON","NIE","IGN","COR","EBA","HEL","SEL","DOR","BRA"]
```

**Complejidad:** O(V + E) — mismo argumento que DFS.
**Espacio:** O(V) por el arreglo de visitados y la cola.
**Estructura usada:** cola FIFO (`ArrayDeque`) + `boolean[] visitado`.

**Nota:** se marca visitado **al encolar**, no al desencolar; si no, un mismo
vértice entra varias veces a la cola.

**Diferencia con Dijkstra:** BFS minimiza la cantidad de rutas, no los días. PIN→CON
es 1 ruta y cuesta 10 días; PIN→AZU→VAD→CON son 3 rutas y cuestan 9.

---

### GET /api/grafo/alcance

BFS **multi-fuente por niveles** con profundidad máxima k: a cuántos saltos
(en el juego, conquistas = turnos) está cada territorio de un conjunto de orígenes.

**Parámetros:** `origenes` (ids separados por coma), `k` (por defecto 2)

**Ejemplo:** `GET /api/grafo/alcance?origenes=AZU,PIN,VAD&k=2`

**Respuesta:**
```json
{"AZU":0,"PIN":0,"VAD":0,"CON":1,"NIE":1,"IGN":1,"COR":2,"EBA":2,"HEL":2}
```

**Complejidad:** O(V + E) — todos los orígenes entran a la cola en el nivel 0 y
después es un BFS común que no expande los vértices del nivel k.
**Espacio:** O(V).
**Estructura usada:** cola FIFO + `int[] nivel` (que además hace de visitado).

---

### GET /api/grafo/componentes

Componentes conexas con DFS repetido.

**Ejemplo:** `GET /api/grafo/componentes`

**Respuesta:** (el mapa completo es conexo)
```json
[["AZU","PIN","CON","COR","IGN","VAD","SEL","BRA","DOR","EBA","NIE","HEL"]]
```

**Complejidad:** O(V + E) — aunque se lancen varios DFS, cada vértice se visita
una sola vez en total y cada lista de adyacencia se recorre una vez.
**Espacio:** O(V).
**Estructura usada:** `boolean[] visitado` compartido entre los DFS + recursión.

**En el juego** se corre sobre el subgrafo de territorios de un jugador: si hay
más de una componente, el imperio quedó partido (ver `/api/juego/{id}/imperio`).

---

### GET /api/grafo/dijkstra

Camino de menos días de marcha entre dos territorios, con el camino reconstruido.

**Parámetros:** `origen` (id), `destino` (id)

**Ejemplo:** `GET /api/grafo/dijkstra?origen=AZU&destino=BRA`

**Respuesta:**
```json
{ "vertices": ["AZU","VAD","CON","EBA","DOR","BRA"], "costoTotal": 15.0 }
```

**Ejemplo de verificación:** `GET /api/grafo/dijkstra?origen=PIN&destino=CON`
devuelve **9** por `PIN→AZU→VAD→CON`, no 10 por el paso de montaña directo. Si
devuelve 10, la relajación está mal.

**Complejidad:** O((V + E) log V) — cada arista provoca a lo sumo un `push` al
heap (E log V) y cada vértice se cierra una vez (V log V). Se usa *lazy
deletion*: en vez de decrementar la clave se pushea una entrada nueva y se
descartan las obsoletas al sacarlas.
**Espacio:** O(V) para `dist[]`, `previo[]` y `listo[]`, más O(E) de cola.
**Estructura usada:** `PriorityQueue` (heap binario) + arreglo de predecesores.

**Errores:** arista negativa → `400`; destino inalcanzable → `422`.

---

### GET /api/grafo/floyd

Días de marcha mínimos entre **todos** los pares.

**Ejemplo:** `GET /api/grafo/floyd`

**Respuesta (144 pares, recortada):**
```json
{ "AZU->BRA": 15.0, "PIN->CON": 9.0, "NIE->COR": 11.0, "HEL->IGN": 19.0, ... }
```

**Recurrencia (PD):** `d_k(i,j) = min( d_{k-1}(i,j) , d_{k-1}(i,k) + d_{k-1}(k,j) )`.
El bucle del intermedio `k` va **siempre afuera**: al terminar la iteración k,
la matriz tiene los mínimos usando como intermedios solo vértices ≤ k.

**Complejidad:** O(V³) tiempo — tres bucles anidados de V. O(V²) espacio.
**Estructura usada:** matriz de adyacencia `double[V][V]`.

**Ciclos negativos:** se detectan revisando la diagonal al final: si algún
`m[i][i] < 0` → `422`. Los pares inalcanzables se omiten (`Infinity` no es JSON).

---

### GET /api/grafo/floyd/comparacion

**Hito 7:** Floyd-Warshall contra correr Dijkstra desde cada vértice.

**Ejemplo:** `GET /api/grafo/floyd/comparacion`

**Respuesta:**
```json
{ "vertices": 12, "aristas": 17,
  "floydCombinacionesEvaluadas": 1728, "floydMejoras": 158,
  "dijkstraVerticesExpandidos": 144, "dijkstraAristasRelajadas": 408,
  "dijkstraInsercionesEnCola": 169, "mismosResultados": true,
  "conclusion": "Grafo ralo: Dijkstra V veces hace menos trabajo que las V^3 combinaciones de Floyd." }
```

**Lectura:** Floyd evalúa V³ = 1.728 combinaciones (k, i, j) sin importar
cuántas aristas haya. Dijkstra × V expande cada vértice una vez por origen
(12 × 12 = 144) y relaja cada entrada de adyacencia una vez por origen
(12 × 34 = 408). Las inserciones al heap (169) dependen de cómo se desempatan
distancias iguales.

**Complejidad:** O(V³) de Floyd + O(V · (V + E) log V) de los V Dijkstra.
En grafos ralos (E ≈ V) Dijkstra × V es ≈ O(V² log V), mejor que V³; en grafos
densos (E ≈ V²) queda O(V³ log V), peor que Floyd.
**Estructura usada:** matriz + `PriorityQueue` por cada origen.

---

### GET /api/grafo/ucs

Dijkstra con parada temprana al **sacar** el destino de la cola.

**Parámetros:** `origen`, `destino`

**Ejemplo:** `GET /api/grafo/ucs?origen=AZU&destino=CON`

**Respuesta:**
```json
{ "camino": {"vertices":["AZU","VAD","CON"],"costoTotal":6.0},
  "expandidosUcs": 4, "expandidosDijkstraCompleto": 12 }
```

**Complejidad:** la misma cota O((V + E) log V); en la práctica expande menos
(acá 4 de 12) porque corta apenas cierra el destino.

---

### GET /api/grafo/prim

Árbol de recubrimiento mínimo (la red de caminos más barata que une todo el mapa).

**Parámetros:** `origen` (territorio desde el que crece el árbol)

**Ejemplo:** `GET /api/grafo/prim?origen=AZU`

**Respuesta:**
```json
{ "aristas": [
    {"origen":"AZU","destino":"VAD","costo":2.0}, {"origen":"AZU","destino":"PIN","costo":3.0},
    {"origen":"PIN","destino":"NIE","costo":3.0}, {"origen":"VAD","destino":"CON","costo":4.0},
    {"origen":"CON","destino":"EBA","costo":3.0}, {"origen":"CON","destino":"COR","costo":4.0},
    {"origen":"COR","destino":"SEL","costo":3.0}, {"origen":"SEL","destino":"BRA","costo":3.0},
    {"origen":"BRA","destino":"DOR","costo":2.0}, {"origen":"NIE","destino":"HEL","costo":5.0},
    {"origen":"COR","destino":"IGN","costo":5.0} ],
  "costoTotal": 37.0 }
```

**Complejidad:** O(E log V) con cola de prioridad: cada arista entra a la cola a
lo sumo una vez. La variante con búsqueda lineal del mínimo es O(V²) y solo
conviene en grafos densos.
**Espacio:** O(V + E).
**Estructura usada:** `PriorityQueue` de aristas candidatas + `boolean[] enMst`.

**Invariante (propiedad del corte):** la arista mínima que cruza el corte entre
lo que está en el árbol y lo que no pertenece a algún MST.

**Errores:** grafo no conexo → `422`.

---

### GET /api/grafo/kruskal

MST por Kruskal con Union-Find propio.

**Ejemplo:** `GET /api/grafo/kruskal`

**Respuesta:**
```json
{ "aristas": [
    {"origen":"AZU","destino":"VAD","costo":2.0}, {"origen":"BRA","destino":"DOR","costo":2.0},
    {"origen":"AZU","destino":"PIN","costo":3.0}, {"origen":"BRA","destino":"SEL","costo":3.0},
    {"origen":"NIE","destino":"PIN","costo":3.0}, {"origen":"COR","destino":"SEL","costo":3.0},
    {"origen":"CON","destino":"EBA","costo":3.0}, {"origen":"DOR","destino":"EBA","costo":4.0},
    {"origen":"EBA","destino":"NIE","costo":4.0}, {"origen":"COR","destino":"IGN","costo":5.0},
    {"origen":"HEL","destino":"NIE","costo":5.0} ],
  "costoTotal": 37.0 }
```

**Complejidad:** O(E log E), dominada por ordenar las aristas (con un heap, sin
`Collections.sort`). `find` y `union` con compresión de caminos y unión por
rango cuestan O(α(V)) amortizado, prácticamente constante.
**Espacio:** O(V + E).
**Estructura usada:** `PriorityQueue` de aristas + Union-Find (`padre[]`, `rango[]`).

**Prim y Kruskal dan el mismo costo (37) con aristas distintas**: hay empates en
los costos 3 y 4, así que el mapa tiene más de un MST.

---

### GET /api/grafo/rutas

Backtracking: todas las rutas simples (sin repetir territorios) con un máximo
de días y de saltos. En el juego son las **rutas de invasión**.

**Parámetros:** `origen`, `destino`, `costoMaximo` (por defecto 1000),
`saltosMaximos` (por defecto 10)

**Ejemplo:** `GET /api/grafo/rutas?origen=AZU&destino=BRA&costoMaximo=22&saltosMaximos=6`

**Respuesta (7 rutas):**
```json
[ {"vertices":["AZU","PIN","CON","EBA","DOR","BRA"],"costoTotal":22.0},
  {"vertices":["AZU","PIN","NIE","EBA","DOR","BRA"],"costoTotal":16.0},
  {"vertices":["AZU","PIN","NIE","HEL","DOR","BRA"],"costoTotal":19.0},
  {"vertices":["AZU","VAD","CON","COR","SEL","BRA"],"costoTotal":16.0},
  {"vertices":["AZU","VAD","CON","EBA","DOR","BRA"],"costoTotal":15.0},
  {"vertices":["AZU","VAD","CON","EBA","SEL","BRA"],"costoTotal":22.0},
  {"vertices":["AZU","VAD","IGN","COR","SEL","BRA"],"costoTotal":19.0} ]
```

Sin límites hay **38** rutas simples de AZU a BRA; la más barata (15) coincide con Dijkstra.

**Complejidad:** O(V!) en el peor caso: en un grafo completo, desde cada vértice
se puede seguir a cualquiera de los no visitados. La poda no cambia la cota
teórica, solo el comportamiento práctico.
**Recurrencia:** T(n) = (n − 1) · T(n − 1) + O(1).
**Espacio:** O(V) por el camino parcial y `visitado[]` (más las rutas encontradas).
**Estructura usada:** recursión + `boolean[] visitado` + lista del camino parcial.

**Los tres elementos del backtracking:** construcción incremental (agregar un
territorio), poda por viabilidad (cortar si el costo parcial ya se pasó) y
**retroceso** (desmarcar y sacar el territorio al volver).

---

### GET /api/grafo/rutas/poda

**Hito 8:** la misma búsqueda con poda y sin poda.

**Parámetros:** los mismos que `/rutas`

**Ejemplo:** `GET /api/grafo/rutas/poda?origen=AZU&destino=BRA&costoMaximo=22&saltosMaximos=6`

**Respuesta:**
```json
{ "exploradosConPoda": 50, "exploradosSinPoda": 93, "rutasEncontradas": 7, "porcentajeAhorrado": 46.24 }
```

Con `costoMaximo=20&saltosMaximos=5`: 43 contra 65 nodos, 5 rutas. Las dos
versiones encuentran las mismas rutas: con costos positivos, un camino parcial
que ya superó el máximo no puede volver a bajar.

**Complejidad:** dos backtrackings, O(V!) peor caso cada uno.

---

## Catálogo de unidades

Los `Item` son las unidades del mercado: `peso` = costo en oro, `valor` = poder.

### GET /api/seleccion/greedy

Compra greedy por ratio poder/costo.

**Parámetros:** `capacidad` (oro disponible)

**Ejemplo:** `GET /api/seleccion/greedy?capacidad=6`

**Respuesta:**
```json
{ "elegidos": [ {"id":"U2","nombre":"Arqueros","peso":3.0,"valor":5.0}, {"id":"U1","nombre":"Milicia","peso":1.0,"valor":1.0} ],
  "valorTotal": 6.0, "pesoTotal": 4.0, "tecnica": "GREEDY (ratio valor/peso)" }
```

**Función objetivo:** maximizar el poder total sin pasarse del oro.
**Criterio:** ratio poder/costo descendente.
**Complejidad:** O(n log n), dominada por el MergeSort propio; el barrido es O(n).
**Estructura usada:** MergeSort + acumuladores.

**CONTRAEJEMPLO — greedy NO es óptimo (hito 6):** con 6 de oro greedy compra
Arqueros (ratio 1,67) y después solo le entra la Milicia: **poder 6**, sobran 2
de oro. La mochila llega a **9** (Milicia + Caballería, o la Catapulta sola).
Greedy pierde porque su decisión es irrevocable. En la mochila *fraccional*
(unidades divisibles) greedy sí sería óptimo.

---

### GET /api/seleccion/dinamica

Mochila 0/1 con programación dinámica y recuperación de la solución.

**Parámetros:** `capacidad` (entero)

**Ejemplo:** `GET /api/seleccion/dinamica?capacidad=6`

**Respuesta:**
```json
{ "elegidos": [ {"id":"U1",...}, {"id":"U4","nombre":"Caballeria","peso":5.0,"valor":8.0} ],
  "valorTotal": 9.0, "pesoTotal": 6.0, "tecnica": "PROGRAMACION DINAMICA (mochila 0/1)" }
```

**Definición:** `dp[i][j]` = máximo poder con las primeras i unidades y j de oro.
**Recurrencia:**
```
dp[i][j] = dp[i-1][j]                                    si costo_i > j
dp[i][j] = max( dp[i-1][j] , poder_i + dp[i-1][j-costo_i] ) si entra
```
**Recuperación:** desde `dp[n][W]` hacia atrás; si `dp[i][j] != dp[i-1][j]`, la
unidad i se compró. La tabla completa del ejemplo está en el README (hito 6).

**Complejidad:** O(n·W) en tiempo y espacio. **Pseudopolinómica:** W es un
*valor* de la entrada, no su tamaño (ocupa log W bits). En el juego W es el oro
del turno (decenas), así que la IA la resuelve en cada nodo del árbol.
**Estructura usada:** matriz `double[n+1][W+1]`.

**Errores:** pesos no enteros → `400`.

---

### GET /api/seleccion/quicksort

QuickSort propio (genérico) sobre el catálogo.

**Parámetros:** `criterio` — `poder` | `costo` | `ratio` (también `valor` | `peso`)

**Ejemplo:** `GET /api/seleccion/quicksort?criterio=ratio`

**Respuesta (ids, ascendente):** `["U1","U5","U3","U4","U6","U2"]`
(U5 y U3 empatan en 1,5: QuickSort **no** es estable y los intercambia)

**Pivote:** mediana de tres. **Partición:** Lomuto.
**Complejidad:** O(n log n) promedio; O(n²) peor caso, cuando el pivote es
sistemáticamente el mínimo o el máximo: `T(n) = T(n−1) + O(n)`.
**Espacio:** O(log n) de recursión + O(n) de la copia (no modifica la entrada).
**Estructura usada:** lista in situ.

---

### GET /api/seleccion/mergesort

MergeSort propio (genérico, **estable**) sobre el catálogo.

**Parámetros:** `criterio` (igual que QuickSort)

**Ejemplo:** `GET /api/seleccion/mergesort?criterio=costo`

**Respuesta (ids):** `["U1","U2","U3","U4","U5","U6"]`
(con `criterio=ratio`: `["U1","U3","U5","U4","U6","U2"]`: U3 antes que U5, como en la entrada)

**Recurrencia:** `T(n) = 2T(n/2) + O(n)` → a = 2, b = 2, k = 1, a = b^k →
**O(n log n) siempre**.
**Espacio:** O(n) por las listas auxiliares de la mezcla.
**Estabilidad:** en la mezcla se toma del lado izquierdo cuando comparan igual.

---

### GET /api/seleccion/binaria

Búsqueda binaria de la última unidad que se puede pagar.

**Parámetros:** `oro`

**Ejemplo:** `GET /api/seleccion/binaria?oro=6`

**Respuesta (recortada):**
```json
{ "oro": 6.0, "ultimoAsequible": 4,
  "asequibles": [ {"id":"U1",...}, {"id":"U2",...}, {"id":"U3",...}, {"id":"U4",...}, {"id":"U5",...} ],
  "catalogoPorCosto": [ ... ] }
```

**Precondición:** la lista está ordenada por costo (MergeSort; en el juego se ordena una sola vez al crear la partida).
**Recurrencia:** `T(n) = T(n/2) + O(1)` → a = 1, b = 2, k = 0 → **O(log n)**.
**Espacio:** O(1) (versión iterativa).
**Invariante:** a la izquierda de `desde` todo cuesta ≤ oro; a la derecha de `hasta`, todo > oro.

---

### GET /api/seleccion/repartir

Branch & Bound: repartir las unidades en k columnas **maximizando la columna más débil**.

**Parámetros:** `frentes` (k, por defecto 2)

**Ejemplo:** `GET /api/seleccion/repartir?frentes=2`

**Respuesta (recortada):**
```json
{ "porFrente": { "columna-0": [ {"id":"U6"}, {"id":"U4"} ],
                 "columna-1": [ {"id":"U5"}, {"id":"U3"}, {"id":"U2"}, {"id":"U1"} ] },
  "margenFinal": { "columna-0": 21.0, "columna-1": 21.0 },
  "margenMinimo": 21.0, "nodosExplorados": 12, "nodosPodados": 4,
  "podadosPorSimetria": 1, "solucionInicial": 20.0, "cotaRaiz": 21.0 }
```

**CUÁL ES LA COTA Y POR QUÉ ES OPTIMISTA (hito 11).** Se **maximiza**, así que la
cota es **superior**:
```
cota = min( (Σ márgenes actuales + poder restante) / k ,  margen del frente más débil + poder restante )
```
- el mínimo de k números nunca supera su promedio, y la suma final de los
  márgenes es fija (lo que hay más todo lo que falta repartir);
- el frente más débil hoy, aun recibiendo todo lo que falta, no puede terminar
  más alto que su margen + el poder restante, y el mínimo final no supera ese valor.

Las dos son cotas superiores verdaderas: nunca subestiman lo que la rama puede
dar. Si `cota ≤ mejor`, ninguna hoja de la rama mejora la mejor solución y
**podar es seguro**. Una cota pesimista podría cortar la rama del óptimo sin avisar.

**Solución inicial:** greedy "cada unidad al frente más débil" → 20. La cota en
la raíz es 21 y el B&B la alcanza ({13, 8} contra {9, 6, 5, 1}).
**Poda por simetría:** frentes con el mismo margen actual son intercambiables;
se prueba solo el primero.
**Nodos:** 12 explorados, 4 podados por cota, 1 por simetría. Con `frentes=3` la
solución greedy ya es 14 = cota de la raíz: 1 nodo explorado, 1 podado.

**Complejidad:** O(k^n) en el peor caso; mucho menor con la poda.
**Espacio:** O(n + k) más la pila de recursión, de profundidad n.
**Diferencia con backtracking:** allá se poda por **viabilidad**; acá por **optimalidad**.

---

### GET /api/seleccion/huffman

Código de Huffman sobre un texto cualquiera.

**Parámetros:** `texto`

**Ejemplo:** `GET /api/seleccion/huffman?texto=ABRACADABRA`

**Respuesta:**
```json
{ "codigos": {"A":"0","C":"100","D":"101","B":"110","R":"111"},
  "bits": "01101110100010101101110", "bitsOriginales": 88, "bitsComprimidos": 23,
  "tasaCompresion": 0.2614, "decodificado": "ABRACADABRA" }
```

**Greedy óptimo:** junta siempre los dos árboles de menor frecuencia.
**Complejidad:** O(n + s log s), con n el largo del texto y s los símbolos distintos.
**Estructura usada:** `PriorityQueue` de nodos del árbol.
**Propiedad prefijo:** los símbolos están solo en las hojas, así que ningún código
es prefijo de otro y se decodifica sin separadores.

---

## Juego

El humano juega con **AZUL**; la IA con **ROJO**. Al terminar el turno del
humano, la IA juega el suyo en la misma request.

### POST /api/juego

Crea una partida. El mapa y el catálogo se leen de Neo4j **una vez**; desde
ahí todo corre en memoria.

**Parámetros:** `dificultad` = `FACIL` | `NORMAL` | `DIFICIL` (por defecto
`NORMAL`); `escenario` = `CLASICO` | `ALEATORIO`; `semilla` (opcional, para
reproducir un escenario aleatorio)

**Ejemplo:** `POST /api/juego?dificultad=DIFICIL&escenario=CLASICO`

**Respuesta (la vista de la partida, recortada):**
```json
{ "id": "3f9a1c2e", "dificultad": "DIFICIL", "ronda": 1, "limiteRondas": 30,
  "turno": "AZUL", "fase": "RECLUTAMIENTO", "ganador": null, "puntosDeMarcha": 8,
  "territorios": [ {"id":"AZU","nombre":"Aguazul","terreno":"Ciudad","produccion":3,"x":110.0,"y":110.0,
                    "duenio":"AZUL","tropas":6,"defensa":9,"esCapital":true,"abastecido":true}, ... ],
  "rutas": [ {"origen":"AZU","destino":"PIN","costo":3}, ... ],
  "jugadores": { "AZUL": {"oro":6,"capital":"AZU","territorios":3,"tropas":12,"produccion":7,
                          "mantenimiento":1,"ingresoNeto":6,"costoRed":5.0,
                          "red":[{"origen":"AZU","destino":"VAD","costo":2.0},{"origen":"AZU","destino":"PIN","costo":3.0}],
                          "aislados":[],"componentes":1,"puntaje":82}, "ROJO": {...} },
  "catalogo": [ ... ], "eventos": [ "Nueva partida (DIFICIL). AZUL arranca en Aguazul (AZU) ...", ... ],
  "ultimaJugadaIa": null,
  "escenario": {"capitalAzul":"AZU","regionAzul":["AZU","PIN","VAD"],"capitalRoja":"BRA",
                "regionRoja":["BRA","DOR","SEL"],"produccionAzul":7,"produccionRoja":7,
                "nodosExplorados":7,"nodosPodados":0} }
```

**Complejidad:** O(V³) por Floyd (una vez por partida) + el backtracking del
escenario + O(n log n) para ordenar el catálogo.

---

### GET /api/juego/escenario

Backtracking (tipo 1): un arranque válido sin crear partida.

**Parámetros:** `semilla` (opcional; sin semilla = escenario clásico)

**Ejemplo:** `GET /api/juego/escenario?semilla=42`

**Respuesta:**
```json
{ "capitalAzul": "NIE", "regionAzul": ["NIE","PIN","CON"], "capitalRoja": "BRA",
  "regionRoja": ["BRA","DOR","SEL"], "produccionAzul": 6, "produccionRoja": 7,
  "nodosExplorados": 8, "nodosPodados": 3 }
```

**Restricciones:** capitales que sean ciudades a ≥ 10 días (distancia de Floyd);
regiones de 3 territorios **conexas** (se crece siempre por un vecino); que no se
toquen; producción con diferencia ≤ tolerancia (0 en el clásico, 1 en el aleatorio).
**Podas:** capitales demasiado cerca; candidatos que tocan al rival; producción
roja que ya se pasó (la producción solo crece) o que ni con los mejores
territorios llega.
**Complejidad:** exponencial en el peor caso, O(C² · V^(2m)) con C ciudades y
regiones de m territorios; con la poda son pocos nodos.
**Estructura usada:** recursión + `int[] duenio` + listas de las regiones (con retroceso).

---

### GET /api/juego/{id}

La vista de la partida (mismo formato que la creación).

---

### POST /api/juego/{id}/comprar

Fase 2: comprar unidades (una de cada tipo por turno) y ubicarlas en
territorios propios **abastecidos**.

**Cuerpo:** `{"compras":[{"unidad":"U1","destino":"VAD"},{"unidad":"U4","destino":"VAD"}]}`

**Respuesta:** la vista; en `eventos`: `"AZUL recluta Milicia en VAD, Caballeria en VAD (costo 6, poder +9)."`

**Errores:** unidad repetida, oro insuficiente, destino ajeno o aislado → `400`;
fase ya pasada → `422`.
**Complejidad:** O(V + E) para verificar el abastecimiento (DFS + Prim) + O(n).

---

### POST /api/juego/{id}/marchar

Fase 3: mover tropas por el camino de menos días **por territorio propio**
(Dijkstra sobre el subgrafo propio). Tiene que costar ≤ 8 días.

**Cuerpo:** `{"origen":"AZU","destino":"VAD","tropas":3}`

**Respuesta:** la vista; en `eventos`: `"AZUL marcha con 3 tropas por AZU→VAD (2 dias)."`

**Errores:** más de 8 días o tropas inválidas (tiene que quedar 1) → `400`; sin
camino por territorio propio → `422`.
**Complejidad:** O(V + E) para armar el subgrafo propio + O((V + E) log V) de Dijkstra.

---

### POST /api/juego/{id}/atacar

Fase 4: atacar un territorio vecino. Combate determinista.

**Cuerpo:** `{"origen":"VAD","destino":"CON","tropas":14}`

**Respuesta:** la vista; en `eventos`:
`"AZUL ataca CON desde VAD con 14 contra defensa 4: ¡CONQUISTADO! Quedan 10 tropas."`

**Errores:** no vecinos, territorio propio, tropas inválidas → `400`; ya atacó → `422`.
**Complejidad:** O(grado) para verificar la adyacencia.

---

### POST /api/juego/{id}/terminar

Termina el turno de AZUL. ROJO cobra, **piensa con su técnica** y juega su turno
completo; después AZUL cobra el suyo.

**Respuesta:** la vista; `ultimaJugadaIa` dice qué decidió la IA y cuánto le costó:
```json
{ "jugador": "ROJO", "tecnica": "ALFA-BETA",
  "plan": { "compras": [{"unidad":"U1","destino":"BRA"},{"unidad":"U4","destino":"BRA"}],
            "marcha": null, "ataque": null, "descripcion": "fortifica la capital", "ratioGreedy": -1.0 },
  "valor": -3.9, "nodosVisitados": 70, "cortes": 4, "profundidad": 2, "jugadasEnLaRaiz": 9, "milisegundos": 1 }
```

**Complejidad:** la de la IA: O(b^d) peor caso para alfa-beta (ver `/ia/comparacion`).

---

### GET /api/juego/{id}/marcha

**Dijkstra** por territorio propio: vista previa de una marcha.

**Parámetros:** `origen`, `destino`

**Ejemplo:** `GET /api/juego/{id}/marcha?origen=PIN&destino=VAD`

**Respuesta:**
```json
{ "vertices": ["PIN","AZU","VAD"], "dias": 5.0, "puntosDeMarcha": 8, "alcanza": true }
```

**Complejidad:** O(V + E) del subgrafo + O((V + E) log V) de Dijkstra.

---

### GET /api/juego/{id}/mercado

**Búsqueda binaria + greedy + mochila 0/1** sobre el oro del jugador que tiene el turno.

**Ejemplo:** `GET /api/juego/{id}/mercado` (primer turno, 6 de oro)

**Respuesta (recortada):**
```json
{ "jugador": "AZUL", "oro": 6, "ultimoAsequible": 4,
  "asequibles": [ U1, U2, U3, U4, U5 ],
  "greedy":  { "elegidos": [U2, U1], "valorTotal": 6.0, "pesoTotal": 4.0, ... },
  "mochila": { "elegidos": [U1, U4], "valorTotal": 9.0, "pesoTotal": 6.0, ... },
  "poderQuePierdeGreedy": 3.0 }
```

**Complejidad:** O(log n) la búsqueda binaria + O(n log n) greedy + O(n·W) mochila.

---

### GET /api/juego/{id}/sugerencias

**Greedy ganancia/riesgo:** los ataques que ganan hoy, de mejor a peor ratio.
`ganancia = 2·producción + 3 si es del rival + 1000 si es su capital`;
`riesgo = defensa + 1 + lo que queda expuesto tras conquistar`.

**Ejemplo** (después de comprar Milicia + Caballería en VAD):

**Respuesta:**
```json
[ {"origen":"VAD","destino":"CON","nombreDestino":"Paso del Condor","tropasDisponibles":11,
   "defensa":4,"ganancia":2.0,"riesgo":5.0,"ratio":0.4},
  {"origen":"VAD","destino":"IGN","nombreDestino":"Cumbres Igneas","tropasDisponibles":11,
   "defensa":4,"ganancia":2.0,"riesgo":5.0,"ratio":0.4} ]
```

**Complejidad:** O(V + E) para generar los candidatos + O(c log c) para ordenarlos (MergeSort, estable).
**No es óptimo:** es miope; no mira la respuesta del rival (eso lo hace MIN-MAX).

---

### GET /api/juego/{id}/objetivos

**AVL con consulta por rango:** los territorios ajenos con defensa en
[`min`, `max`], y los `k` de mayor producción.

**Parámetros:** `min` (por defecto 0), `max` (por defecto: lo máximo que se puede
mandar desde un territorio − 1, o sea lo que se puede conquistar hoy), `k` (por defecto 3)

**Ejemplo:** `GET /api/juego/{id}/objetivos?k=3` (primer turno)

**Respuesta (recortada):**
```json
{ "defensaMinima": 0.0, "defensaMaxima": 4.0, "k": 3, "territoriosEnElArbol": 9,
  "alturaDelArbol": 4, "rotaciones": 5,
  "enRango": [ {"id":"DOR","duenio":"ROJO","defensa":3,"produccion":2}, {"id":"CON",...,"defensa":4}, ... ],
  "masValiosos": [ {"id":"DOR",...}, {"id":"SEL",...}, {"id":"EBA",...} ] }
```

**Complejidad:** inserción O(log n) cada una (n inserciones: O(n log n));
consulta por rango **O(log n + m)** con m resultados (se poda el subárbol que
queda fuera del rango); top-k con un heap de mínimos de tamaño k: O(m log k).
**Estructura usada:** árbol AVL (rotaciones simples y dobles) + `PriorityQueue`.
**Por qué AVL:** si las claves llegan ordenadas, un ABB común degenera en lista
y la consulta pasa a O(n); el AVL garantiza altura O(log n).

---

### GET /api/juego/{id}/alcance

**BFS por niveles** desde todos los territorios propios: a cuántas conquistas
(turnos) está cada territorio.

**Parámetros:** `turnos` (k, por defecto 2)

**Ejemplo:** `GET /api/juego/{id}/alcance?turnos=2`

**Respuesta:**
```json
{"AZU":0,"PIN":0,"VAD":0,"CON":1,"NIE":1,"IGN":1,"COR":2,"EBA":2,"HEL":2}
```

**Complejidad:** O(V + E).

---

### GET /api/juego/{id}/imperio

**DFS (componentes) + Prim + Kruskal** sobre el imperio de un jugador: ¿quedó
partido? ¿cuánto cuesta su red de abastecimiento?

**Parámetros:** `jugador` = `AZUL` | `ROJO` (por defecto el que tiene el turno)

**Ejemplo:** `GET /api/juego/{id}/imperio?jugador=AZUL`

**Respuesta:**
```json
{ "jugador": "AZUL", "componentes": [["AZU","PIN","VAD"]], "partido": false,
  "abastecidos": ["AZU","PIN","VAD"], "aislados": [],
  "prim":    {"aristas":[{"origen":"AZU","destino":"VAD","costo":2.0},{"origen":"AZU","destino":"PIN","costo":3.0}],"costoTotal":5.0},
  "kruskal": {"aristas":[...],"costoTotal":5.0},
  "mismoCosto": true, "mantenimiento": 1 }
```

**Complejidad:** O(V + E) del subgrafo y del DFS + O(E log V) de Prim + O(E log E) de Kruskal.

---

### GET /api/juego/{id}/reparto

**Branch & Bound:** reparte la compra óptima de este turno entre los frentes
del jugador (territorios abastecidos que lindan con algo ajeno), maximizando el
margen del frente más débil. `margen = defensa − amenaza directa del rival`.

**Ejemplo:** `GET /api/juego/{id}/reparto` (primer turno)

**Respuesta (recortada):**
```json
{ "unidades": [U1, U4], "frentes": [ {"id":"PIN","margen":4.0}, {"id":"VAD","margen":3.0} ],
  "reparto": { "porFrente": {"PIN":[U1], "VAD":[U4]}, "margenFinal": {"PIN":5.0,"VAD":11.0},
               "margenMinimo": 5.0, "nodosExplorados": 3, "nodosPodados": 2, "podadosPorSimetria": 0,
               "solucionInicial": 5.0, "cotaRaiz": 8.0 },
  "comoCompra": [ {"unidad":"U1","destino":"PIN"}, {"unidad":"U4","destino":"VAD"} ] }
```

`comoCompra` se puede mandar tal cual a `POST /comprar`.

**Complejidad:** la de `/api/seleccion/repartir` (O(k^n) peor caso) + la de la mochila.

---

### GET /api/juego/{id}/ranking

**QuickSort / MergeSort** sobre territorios.

**Parámetros:** `criterio` = `valor` (QuickSort de todos por valor estratégico:
2·producción + bonus de terreno + cantidad de rutas + 5 si es capital) |
`amenaza` (MergeSort estable de los propios por amenaza directa − defensa)

**Ejemplo:** `GET /api/juego/{id}/ranking?criterio=valor`

**Respuesta (recortada):**
```json
[ {"id":"AZU","nombre":"Aguazul","duenio":"AZUL","puntaje":14.0},
  {"id":"BRA","nombre":"Brasaroja","duenio":"ROJO","puntaje":14.0},
  {"id":"NIE",...,"puntaje":10.0}, {"id":"COR",...,"puntaje":10.0}, ... ,
  {"id":"HEL","nombre":"Picos Helados","duenio":"NEUTRAL","puntaje":6.0} ]
```

**Complejidad:** O(V log V) promedio (QuickSort) / siempre (MergeSort) + O(V + E) para puntuar.

---

### GET /api/juego/{id}/ia/comparacion

**MIN-MAX contra alfa-beta** sobre el estado actual, para el jugador que tiene
el turno, a la misma profundidad.

**Parámetros:** `profundidad` (1 a 4, por defecto 3)

**Ejemplo:** `GET /api/juego/{id}/ia/comparacion?profundidad=4` (estado inicial)

**Respuesta (recortada):**
```json
{ "profundidad": 4, "jugadasEnLaRaiz": 9,
  "valorMinimax": 4.8, "nodosMinimax": 7408, "msMinimax": 92,
  "valorAlfaBeta": 4.8, "nodosAlfaBeta": 567, "cortesAlfaBeta": 149, "msAlfaBeta": 8,
  "mismoValor": true, "porcentajeAhorrado": 92.35,
  "jugadaMinimax":  {"descripcion":"ataca NIE desde PIN con 11", ...},
  "jugadaAlfaBeta": {"descripcion":"ataca NIE desde PIN con 11", ...} }
```

Con profundidad 3: 834 contra 192 nodos (77 % menos); con 2: 91 contra 35.

**MIN-MAX:** `T(d) = b · T(d − 1) + O(b · costo de aplicar)` → **O(b^d)** tiempo,
O(b·d) espacio (d niveles de recursión con su lista de b jugadas).
**Alfa-beta:** O(b^d) en el peor caso; con el mejor orden **O(b^(d/2))**. Por eso
las jugadas llegan ordenadas por la heurística greedy.
**Estructura usada:** recursión; el estado es un puñado de `int[]` que se copia
en O(V) por hijo (sin retroceso explícito). La evaluación lee la matriz de Floyd en O(1).

---

### GET /api/juego/{id}/consejo

"¿Qué haría la IA en mi lugar?": alfa-beta a profundidad 2 para AZUL, sin aplicar nada.
Se pide al principio del turno (fase de reclutamiento).

**Respuesta:**
```json
{ "jugador": "AZUL", "tecnica": "ALFA-BETA",
  "plan": {"compras":[{"unidad":"U1","destino":"AZU"},{"unidad":"U4","destino":"AZU"}],
           "marcha":null,"ataque":null,"descripcion":"fortifica la capital","ratioGreedy":-1.0},
  "valor": 3.6, "nodosVisitados": 35, "cortes": 7, "profundidad": 2, "jugadasEnLaRaiz": 9, "milisegundos": 1 }
```

**Complejidad:** O(b^d) peor caso con d = 2.

---

### GET /api/juego/{id}/replay

**Huffman** sobre el replay compacto de los turnos jugados.

Formato de cada turno: `<ronda><A|R>:<acciones>;` con `C<unidad>@<destino>`
(compra), `M<origen>><destino>x<tropas>` (marcha) y
`A<origen>><destino>x<tropas><+|->` (ataque ganado o rechazado).

**Respuesta (después de una ronda):**
```json
{ "texto": "1A:CU1@VAD,CU4@VAD,MAZU>VADx3,AVAD>CONx14+;1R:CU1@BRA,CU4@BRA;",
  "caracteres": 62, "simbolosDistintos": 21,
  "codigos": {"U":"000","4":"0010","R":"0011",",":"0100", ... ,"A":"110","1":"1110","C":"1111"},
  "bitsOriginales": 496, "bitsComprimidos": 256, "tasaCompresion": 0.5161,
  "ahorroPorcentaje": 48.39, "decodificaIgual": true, "primerosBits": "1110110100..." }
```

**Complejidad:** O(n + s log s) comprimir, O(b) descomprimir (b = bits).
**Errores:** sin turnos terminados → `422`.

---

### GET /api/juego/{id}/invasion

**Backtracking:** rutas de invasión desde la capital del jugador hasta la
capital rival, con la más barata por Dijkstra y la comparación con y sin poda.

**Parámetros:** `costoMaximo` (por defecto 22), `saltosMaximos` (por defecto 6)

**Ejemplo:** `GET /api/juego/{id}/invasion` (escenario clásico)

**Respuesta (recortada):**
```json
{ "origen": "AZU", "destino": "BRA",
  "masBarataPorDijkstra": {"vertices":["AZU","VAD","CON","EBA","DOR","BRA"],"costoTotal":15.0},
  "rutas": [ ... las 7 de /api/grafo/rutas ... ],
  "poda": {"exploradosConPoda":50,"exploradosSinPoda":93,"rutasEncontradas":7,"porcentajeAhorrado":46.24} }
```

**Complejidad:** O(V!) peor caso (backtracking) + O((V + E) log V) (Dijkstra).
