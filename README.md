# Programación III · TPO 2026

Trabajo Práctico Obligatorio de Programación III (3.4.077) — UADE, comisión
Miércoles Mañana. Basado en el [scaffold de la materia](https://github.com/Mancaceresuade/tpo-scaffold-2026).

## Integrantes

- Máximo Bonarrico — [@Maxi414](https://github.com/Maxi414)
- Valentín Díaz Imbernón — [@ValentinoDiaz0509](https://github.com/ValentinoDiaz0509)
- Fiel Machado Alex Sandro — <!-- completar usuario de GitHub -->

## Dominio

**Red logística de última milla**: reparto de paquetes desde un depósito
central hacia puntos de entrega de una ciudad.

- **Nodo**: un depósito o un punto de entrega. El campo `tipo` distingue
  `DEPOSITO` (uno solo, el origen de todos los repartos) de `CLIENTE` (los
  puntos de entrega). El campo `valor` representa la **demanda diaria
  promedio en cantidad de paquetes** de ese punto (0 para el depósito).
- **Arista**: un tramo de ruta transitable entre dos puntos. El costo es la
  **distancia en kilómetros** entre ambos — es lo que Dijkstra minimiza para
  encontrar la ruta de reparto más corta, y lo que Prim/Kruskal usan para el
  tendido mínimo de rutas que conecta todos los puntos.
- **Item**: un paquete a transportar. `peso` es su **peso en kg** (lo que
  consume de la capacidad del vehículo) y `valor` es el **valor asegurado de
  la entrega, en pesos** (lo que se busca maximizar). Sobre esto corren
  greedy, mochila 0/1 y Branch & Bound: dado un vehículo con una capacidad de
  carga fija, decidir qué paquetes llevar en un viaje (o cómo repartirlos
  entre varios vehículos/contenedores) para maximizar el valor transportado.
- **Camino con restricciones**: preguntar "¿cuál es la ruta más barata del
  depósito a un cliente, sin superar tal costo o tal cantidad de saltos?"
  tiene sentido operativo real (rutas alternativas ante cortes o límites de
  autonomía) — es lo que resuelve el backtracking con poda.

### El grafo del grupo

```
CD = Depósito Central       AL = Almagro
PA = Palermo                BO = Boedo
BE = Belgrano                FL = Flores
RE = Recoleta                CB = Caballito
```

El árbol de expansión mínima (ver más abajo) resulta ser una cadena simple,
así que sirve como columna vertebral del diagrama:

```
   CD ---- PA ---- BE ---- RE ---- AL ---- BO ---- CB ---- FL
       5        4       3       6       7       4       5
```

Más las aristas "atajo" que completan el grafo (no están en el MST, pero sí
se cargan y son las que hacen falta para que Dijkstra tenga alternativas
entre las que elegir):

```
CD-BE(12)   PA-RE(8)   BE-AL(10)   RE-BO(9)   AL-FL(11)
```

**Aristas completas** (dirigidas en el sentido en que se cargan; para
Prim/Kruskal el grafo se recorre como no dirigido — ver
`GrafoService.cargar`):

```
CD→PA(5)  CD→BE(12)  PA→BE(4)  PA→RE(8)  BE→RE(3)  BE→AL(10)
RE→AL(6)  RE→BO(9)   AL→BO(7)  AL→FL(11) BO→CB(4)  FL→CB(5)
```

Elegido a propósito para que el camino directo **no** sea el más corto:
CD→BE cuesta 12 en línea recta, pero CD→PA→BE cuesta 5+4=9. Verificado a
mano:

- `dijkstra(CD, BE)` = 9 (por CD→PA→BE), no 12
- `dijkstra(CD, *)`: PA=5, BE=9, RE=12, AL=18, BO=21, FL=29, CB=25
- MST (Prim y Kruskal, mismo resultado por los 2 caminos): 34, con las 7
  aristas `BE-RE(3) PA-BE(4) BO-CB(4) CD-PA(5) FL-CB(5) RE-AL(6) AL-BO(7)`

**Paquetes** (para greedy / mochila 0-1 / Branch & Bound), con capacidad de
vehículo = 10 kg como caso de referencia:

| Id | Origen | Peso (kg) | Valor ($) |
|---|---|---|---|
| I1 | CD | 5 | 10 |
| I2 | PA | 4 | 40 |
| I3 | BE | 6 | 30 |
| I4 | RE | 3 | 50 |
| I5 | AL | 7 | 55 |

Con capacidad 10: el greedy por ratio valor/peso elige {I4, I2} = 90, pero el
óptimo real (mochila 0/1 con PD) es {I4, I5} = 105 — el contraejemplo que
pide documentar el Hito 6.

### Hito 4 — dominio cargado y recorridos

El grafo de arriba está cargado en Neo4j (`CargaInicial.java`), reemplazando
la semilla A..H del scaffold. BFS y DFS funcionan sobre él a través de
`GET /api/grafo/bfs` y `GET /api/grafo/dfs`.

### Hito 5 — caminos mínimos y MST, verificados sobre el grafo del grupo

Dijkstra (con reconstrucción de camino), Prim y Kruskal (con Union-Find) ya
estaban implementados en `CaminosMinimos.java` y `ArbolRecubrimiento.java`.
Lo que agrega este hito es la verificación contra el grafo propio: la clase
`Hito5DominioTest` en `AlgoritmosTest.java` (y su espejo en
`verificacion/Verificacion.java`) arma el grafo `dominio()` con las mismas 8
paradas y 12 tramos de `CargaInicial`, y comprueba con asserts los valores
calculados a mano más arriba:

- `dijkstra(CD, BE)` = 9 por `CD→PA→BE`, no 12 por el tramo directo.
- `dijkstra(CD, *)`: PA=5, BE=9, RE=12, AL=18, BO=21, FL=29, CB=25.
- `prim(CD)` y `kruskal()` dan costo total **34** con 7 tramos, mismo
  resultado por los dos caminos.

### Hito 6 — greedy contra programación dinámica, con el contraejemplo documentado

`Seleccion.java` ya resolvía la mochila 0/1 con tabla `dp` y recuperación del
camino (bottom-up, ver el método `programacionDinamica`). El contraejemplo
donde el greedy del Hito 3 **no** da el óptimo está documentado arriba, en la
tabla de paquetes, y ahora también verificado con test: `Hito6DominioTest`
corre greedy y PD sobre los paquetes tal como los carga `CargaInicial`
(nodoId = depósito/cliente real, no genéricos `A..E`) y confirma que:

- greedy elige `{I2, I4}` = 90 y deja 3 kg de capacidad sin usar.
- la PD encuentra el óptimo real `{I4, I5}` = 105, usando los 10 kg completos.
- la diferencia es exactamente 15: el costo de que greedy sea una decisión
  irrevocable en mochila 0/1.

### Próximo paso (Hito 7)

Falta Floyd-Warshall (ya implementado en `CaminosMinimos.java`, sin usar
todavía) comparado en cantidad de nodos expandidos contra correr Dijkstra V
veces sobre el grafo del grupo.

## Arranque rápido

```bash
# 1. Levantar Neo4j (local o Aura) y exportar las credenciales
cp .env.example .env          # completar NEO4J_PASSWORD
export $(cat .env | xargs)

# 2. Compilar y ejecutar
./mvnw spring-boot:run

# 3. Verificar que la base responde
curl "http://localhost:8080/api/grafo/resumen"
# -> {"vertices":8,"aristas":12,"dirigido":true,"ids":["A","B",...]}
```

Tests: `./mvnw test`.

Los algoritmos también se pueden verificar sin levantar Neo4j ni Maven, contra
los valores calculados a mano en `GUIA-TPO.md`:

```bash
cd verificacion
javac --release 17 -d classes $(find . -name "*.java")
java -cp classes Verificacion
```

## Estructura

```
src/main/java/uade/prog3/tpo/
    model/          Nodo, Conexion (arista con costo), Item
    repository/     acceso a Neo4j
    service/        Grafo (lista de adyacencia en memoria) y GrafoService
    algorithm/      los algoritmos del TPO
    controller/     endpoints — sin lógica algorítmica
    seed/           datos de ejemplo (semilla, hasta Hito 4)
src/test/java/uade/prog3/tpo/
docs/
    ENDPOINTS.md        ficha de cada endpoint con complejidad justificada
    COMPLEJIDADES.md    informe de complejidades (Hito 10)
verificacion/           chequeo standalone de los algoritmos, sin Spring/Neo4j
```

## Algoritmos implementados

| Algoritmo | Archivo | Puntos |
|---|---|---|
| BFS y DFS | `algorithm/Recorridos.java` | 2 |
| Dijkstra, Floyd-Warshall, UCS | `algorithm/CaminosMinimos.java` | 2 |
| Prim y Kruskal (Union-Find) | `algorithm/ArbolRecubrimiento.java` | 2 |
| QuickSort y MergeSort | `algorithm/Ordenamiento.java` | 1 |
| Greedy y mochila 0/1 con PD | `algorithm/Seleccion.java` | 2 |
| Backtracking con poda | `algorithm/Backtracking.java` | 1 |
| Branch & Bound de reparto | `algorithm/RamificacionYPoda.java` | 1 |

Detalle de entradas/salidas y complejidad de cada endpoint en `docs/ENDPOINTS.md`.

## Uso de IA

Se utilizó Claude (Anthropic, Claude Code) como asistente durante el desarrollo, para:

- implementar los algoritmos del paquete `algorithm` a partir de las firmas
  provistas por el scaffold
- redactar los tests de `AlgoritmosTest` y la documentación de `docs/`
- verificar los resultados contra los valores de referencia de `GUIA-TPO.md`

La comprensión de los algoritmos no fue delegada: cada integrante puede
explicar el funcionamiento, la complejidad y las estructuras de datos de
todos los algoritmos entregados.

## Estado de los hitos

Ver `GUIA-TPO.md` para el detalle de cada hito y las consignas y rúbrica
completas en `TPO 2026 - Consignas y rubrica.pdf`.
