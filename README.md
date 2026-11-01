# Programación III · TPO 2026 — *Conquista*

Trabajo Práctico Obligatorio de Programación III (3.4.077) — UADE, comisión
Miércoles Mañana. Basado en el [scaffold de la materia](https://github.com/Mancaceresuade/tpo-scaffold-2026).

**Conquista** es un juego de estrategia por turnos para dos jugadores (vos
contra la máquina) sobre un mapa de territorios. Cada fase del turno es un
algoritmo de la materia, y la IA rival es un MIN-MAX con poda alfa-beta.

## Integrantes

- Máximo Bonarrico — [@Maxi414](https://github.com/Maxi414)
- Valentín Díaz Imbernón — [@ValentinoDiaz0509](https://github.com/ValentinoDiaz0509)

## Dominio

**Conquista de territorios por turnos.** Dos reinos (AZUL, el humano, y ROJO,
la IA) se disputan un mapa de 12 territorios unidos por rutas.

| Concepto del scaffold | En el juego | Unidad |
|---|---|---|
| **Nodo** | un **territorio**. `tipo` = terreno (`LLANURA`, `BOSQUE`, `CIUDAD`, `MONTANA`), `valor` = **producción** | oro por turno |
| **Arista** (relación `CONECTA`) | una **ruta** entre dos territorios vecinos. Se recorre en los dos sentidos: el grafo es **no dirigido** | — |
| **Costo de la arista** | lo que tarda un ejército en cruzar la ruta | **días de marcha** |
| **Item** | una **unidad** del mercado. `peso` = **costo**, `valor` = **poder** de combate | oro / tropas |

La prueba de los 30 segundos de la guía:

1. *"La conexión entre PIN y CON cuesta 10 días de marcha"* — el paso de montaña.
2. *¿Camino más barato de A a B?* Sí: es la **marcha** del turno. Cruzar el paso
   PIN→CON directo cuesta 10, pero bordear por el valle PIN→AZU→VAD→CON cuesta 9.
3. *¿Algo que seleccionar bajo capacidad?* Sí: qué unidades comprar con el oro
   del turno (mochila 0/1) y cómo repartirlas entre los frentes (Branch & Bound).

### El mapa

```
 AZU ───3─── PIN ───3─── NIE ───5─── HEL          AZU Aguazul       (ciudad,  prod 3)  capital AZUL
  │           │           │           │           PIN Pinar Hondo   (bosque,  prod 2)
  2          10           4           6           NIE Puerto Niebla (ciudad,  prod 3)
  │           │           │           │           HEL Picos Helados (montaña, prod 1)
 VAD ───4─── CON ───3─── EBA ───4─── DOR          VAD Vado Real     (llanura, prod 2)
  │           │           │           │           CON Paso del Cóndor (montaña, prod 1)
  6           4          10           2           EBA Ruinas de Ébano (bosque, prod 2)
  │           │           │           │           DOR Llano Dorado  (llanura, prod 2)
 IGN ───5─── COR ───3─── SEL ───3─── BRA          IGN Cumbres Ígneas (montaña, prod 1)
                                                  COR Puerto Coral  (ciudad,  prod 3)
   números = días de marcha de cada ruta           SEL Selva Ámbar   (bosque,  prod 2)
                                                  BRA Brasaroja     (ciudad,  prod 3)  capital ROJA
```

12 territorios y 17 rutas. El mapa es simétrico respecto del centro (AZU↔BRA,
PIN↔SEL, NIE↔COR, HEL↔IGN, VAD↔DOR, CON↔EBA, con los mismos costos), así el
escenario clásico es parejo. Es **ralo** (cada territorio linda con 2 a 4),
por eso se representa con **lista de adyacencia**.

Los datos están en `seed/MapaInicial.java`: de ahí los carga `CargaInicial` a
Neo4j y de ahí los arman los tests, así lo que se verifica es lo que está en la
base.

### Catálogo de unidades (el mercado)

| Id | Unidad | Costo (oro) | Poder | Poder / oro |
|---|---|---|---|---|
| U1 | Milicia | 1 | 1 | 1,00 |
| U2 | Arqueros | 3 | 5 | 1,67 |
| U3 | Lanceros | 4 | 6 | 1,50 |
| U4 | Caballería | 5 | 8 | 1,60 |
| U5 | Catapulta | 6 | 9 | 1,50 |
| U6 | Guardia | 8 | 13 | 1,63 |

## Reglas

Cada turno tiene cuatro fases, y **cada fase es un algoritmo**:

| Fase | Qué pasa | Algoritmo |
|---|---|---|
| 1. Cobro (automático) | Se cobra la producción de los territorios **abastecidos** menos el **mantenimiento de la red**. | **DFS** (componentes) + **Prim** (red de abastecimiento) |
| 2. Reclutamiento | Se compran unidades del mercado (una de cada tipo por turno) y se ubican en territorios abastecidos. | **Búsqueda binaria** + **mochila 0/1** (sugerencia óptima) contra **greedy** |
| 3. Marcha | Se mueven tropas entre dos territorios propios por el camino más barato **por territorio propio**, si cuesta ≤ 8 días. | **Dijkstra** |
| 4. Ataque | Se ataca un territorio vecino. | combate determinista |

- **Red de abastecimiento.** Los territorios conectados a la capital por
  territorio propio están abastecidos (DFS sobre el subgrafo propio). Los que
  quedan del otro lado de un corte están **aislados**: no producen ni reciben
  refuerzos. La red que los une es el **árbol de recubrimiento mínimo** (Prim
  desde la capital) y mantenerla cuesta `costoMST / 4` de oro por turno. Por
  eso expandirse por rutas caras sale caro, y cortar al rival por la mitad es
  una jugada fuerte. El MST es una decisión del jugador, no un cálculo pegado.
- **Combate determinista** (sin dados, para que MIN-MAX aplique). Defensa =
  tropas + bonus de terreno (llanura 0, bosque 1, ciudad 1, montaña 2) + 2 si es
  capital. Si las tropas enviadas superan la defensa, se conquista y quedan
  `enviadas − defensa`. Si no, el atacante las pierde y el defensor pierde la mitad.
- Se puede saltear fases, pero no volver atrás. Un ataque por turno.
- **Gana** quien toma la capital rival. Si a las 30 rondas no cayó ninguna, gana
  el de mayor puntaje (10 × producción + tropas).
- **Escenario.** Lo arma un backtracking (`GeneradorDeEscenario`): capitales que
  sean ciudades a ≥ 10 días de marcha, regiones de 3 territorios conexas, que no
  se toquen y con la misma producción. El *clásico* es AZU+PIN+VAD contra
  BRA+DOR+SEL (producción 7 cada uno); el *aleatorio* mezcla el orden con una semilla.

### La IA

| Dificultad | Técnica |
|---|---|
| FÁCIL | greedy puro: el ataque de mejor ratio ganancia/riesgo, sin mirar la respuesta |
| NORMAL | alfa-beta a profundidad 2 (su turno + tu respuesta) |
| DIFÍCIL | alfa-beta a profundidad 4 (dos turnos de cada uno) |

Una jugada del árbol es **un turno completo** (compra con la mochila + marcha
opcional + ataque opcional). La evaluación usa la **matriz de Floyd**
precalculada para medir en O(1) qué tan cerca están los ejércitos de cada
capital. El estado es un puñado de arreglos de `int` y se copia en O(V), así
MIN-MAX genera hijos sin retroceso explícito.

## Dónde está cada algoritmo

| Algoritmo | Archivo | Uso en el juego | Puntos |
|---|---|---|---|
| BFS y DFS | `algorithm/Recorridos.java` | BFS por niveles: territorios alcanzables en k turnos. DFS: componentes conexas (imperio partido) | 2 |
| Dijkstra, Floyd, UCS | `algorithm/CaminosMinimos.java` | marcha por territorio propio; matriz de distancias de la IA | 1 + PD |
| Prim y Kruskal (Union-Find) | `algorithm/ArbolRecubrimiento.java` | red de abastecimiento y su mantenimiento | 2 |
| QuickSort y MergeSort (genéricos) | `algorithm/Ordenamiento.java` | territorios por valor estratégico y por amenaza; catálogo; jugadas de la IA | 1 |
| Greedy | `algorithm/Seleccion.java`, `algorithm/HeuristicaGreedy.java` | compra por ratio (contraejemplo); "a quién ataco" por ganancia/riesgo; IA fácil; orden de jugadas de alfa-beta | 1 |
| Mochila 0/1 con PD | `algorithm/Seleccion.java` | compra óptima del turno | 1 |
| Backtracking | `algorithm/Backtracking.java`, `algorithm/GeneradorDeEscenario.java` | rutas de invasión con límite de días y saltos; escenario inicial | 1 |
| Branch & Bound | `algorithm/RamificacionYPoda.java` | repartir los refuerzos entre los frentes | 1 |
| MIN-MAX y alfa-beta | `algorithm/Minimax.java` + `juego/IaConquista.java` | la IA rival | — |
| Búsqueda binaria | `algorithm/BusquedaBinaria.java` | qué unidades se pueden pagar | — |
| AVL con consulta por rango | `algorithm/ArbolAVL.java` | los k territorios más valiosos dentro de un rango de defensa | — |
| Huffman | `algorithm/Huffman.java` | comprimir el replay de la partida | — |

Las reglas del juego están en `juego/MotorDeJuego.java` y la orquestación en
`service/JuegoService.java`. Los controllers no tienen lógica. Detalle de cada
endpoint, con ejemplos y complejidad justificada, en `docs/ENDPOINTS.md`;
informe de complejidades en `docs/COMPLEJIDADES.md`.

## Hitos

Todos los valores de esta sección están calculados a mano y verificados con
asserts en `src/test/java/uade/prog3/tpo/AlgoritmosTest.java` (clases
anidadas `Hito5Test`, `Hito7Test`, etc.).

### Hito 3 — grupo y dominio

Esta página: dominio, qué es un nodo, qué es una arista, qué representa el
costo (días de marcha) y el diagrama con 12 territorios y sus pesos.

### Hito 4 — dominio cargado y recorridos

`CargaInicial` carga el mapa en Neo4j reemplazando la semilla A..H (y el dominio
anterior del grupo, si estaba). `GrafoService` lee los nodos en **orden
canónico** (por id, y las rutas por id de destino), porque Neo4j no garantiza
el orden y de ese orden dependen BFS, DFS y el escenario.

- `bfs(AZU)` = AZU, PIN, VAD, CON, NIE, IGN, COR, EBA, HEL, SEL, DOR, BRA
  (niveles: 0 | 1: PIN VAD | 2: CON NIE IGN | 3: COR EBA HEL | 4: SEL DOR | 5: BRA)
- `dfs(AZU)` = AZU, PIN, CON, COR, IGN, VAD, SEL, BRA, DOR, EBA, NIE, HEL
- BFS por niveles desde la región azul (AZU, PIN, VAD) con k = 2: CON, NIE e
  IGN a 1 turno; COR, EBA y HEL a 2.

### Hito 5 — Dijkstra, Prim y Kruskal verificados a mano

**Dijkstra desde AZU** (cada fila es el vértice que se cierra y lo que relaja):

| Cierra | dist | Relaja |
|---|---|---|
| AZU | 0 | PIN = 3, VAD = 2 |
| VAD | 2 | CON = 6, IGN = 8 |
| PIN | 3 | NIE = 6 (CON por PIN costaría 13: no mejora 6) |
| CON | 6 | COR = 10, EBA = 9 |
| NIE | 6 | HEL = 11 (EBA por NIE costaría 10: no mejora 9) |
| IGN | 8 | — (COR por IGN costaría 13) |
| EBA | 9 | DOR = 13, SEL = 19 |
| COR | 10 | SEL = 13 (mejora 19) |
| HEL | 11 | — |
| DOR | 13 | BRA = 15 |
| SEL | 13 | — (BRA por SEL costaría 16) |
| BRA | 15 | — |

- `dijkstra(AZU, BRA)` = **15** por AZU→VAD→CON→EBA→DOR→BRA.
- `dijkstra(PIN, CON)` = **9** por PIN→AZU→VAD→CON, **no 10** por el paso directo:
  es el caso que prueba la relajación.

**MST** (Kruskal a mano, aristas por costo):
`AZU-VAD 2, BRA-DOR 2` → los cinco de 3 (`AZU-PIN, BRA-SEL, CON-EBA, COR-SEL,
NIE-PIN`) entran todos → de los de 4 entran `CON-COR` y `CON-VAD`; `DOR-EBA` y
`EBA-NIE` ya cerrarían ciclo → `COR-IGN 5, HEL-NIE 5`. **Costo 37, 11 aristas**.
Hay empates en 3 y 4, así que existe más de un MST: Prim(AZU) y Kruskal
devuelven conjuntos distintos, los dos de costo **37**. Ninguna ruta de 6 o más
entra al árbol (los pasos de montaña PIN-CON y EBA-SEL de 10 quedan afuera).

### Hito 6 — greedy contra programación dinámica

Comprar con **6 de oro** (el primer turno del escenario clásico):

- **Greedy** por poder/oro: Arqueros (1,67) → ya no entra nada hasta la Milicia.
  Compra **{U2, U1}**: gasta 4, **poder 6**, sobran 2 de oro.
- **Mochila 0/1** (tabla dp, filas = catálogo por costo, columnas = oro):

| i \ oro | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|---|
| ∅ | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| U1 (1,1) | 0 | 1 | 1 | 1 | 1 | 1 | 1 |
| U2 (3,5) | 0 | 1 | 1 | 5 | 6 | 6 | 6 |
| U3 (4,6) | 0 | 1 | 1 | 5 | 6 | 7 | 7 |
| U4 (5,8) | 0 | 1 | 1 | 5 | 6 | 8 | 9 |
| U5 (6,9) | 0 | 1 | 1 | 5 | 6 | 8 | 9 |
| U6 (8,13) | 0 | 1 | 1 | 5 | 6 | 8 | **9** |

  Recuperación desde dp[6][6]: U6 y U5 no cambian el valor; U4 sí (9 ≠ 7) →
  queda oro 1 → U1 sí. **{U1, U4}: gasta 6, poder 9** (la Catapulta sola empata).
- Greedy pierde **3 de poder (un tercio)** porque la mejor unidad "por moneda"
  bloquea la compra que usa todo el presupuesto. Otros presupuestos: con 10 de
  oro greedy 14 / PD 15; con 14, 19 / 22; con 19, 27 / 30. Con 8 greedy acierta
  (13): no siempre falla, pero no está garantizado.

### Hito 7 — Floyd-Warshall contra Dijkstra V veces

Sobre el mapa (V = 12, E = 17), endpoint `GET /api/grafo/floyd/comparacion`:

| | Floyd-Warshall | Dijkstra × 12 |
|---|---|---|
| Trabajo | **1.728** combinaciones (k, i, j) = V³ | **144** vértices expandidos + **408** aristas relajadas (+ 169 inserciones al heap) |
| Mejoras / relajaciones exitosas | 158 | — |
| Resultados | idénticos en los 144 pares | |

El mapa es **ralo**: Dijkstra V veces, O(V·(V+E)·log V), hace menos trabajo que
las V³ combinaciones de Floyd. Floyd conviene en grafos densos y admite pesos
negativos sin ciclos negativos (los detecta revisando la diagonal: test
`floydDetectaCicloNegativo`). En el juego Floyd se calcula **una sola vez** por
partida y la IA lo consulta en O(1) miles de veces por jugada.

### Hito 8 — exploración con poda

Rutas de invasión simples de AZU a BRA (endpoint `GET /api/grafo/rutas/poda`):

| Restricción | Rutas | Nodos con poda | Nodos sin poda | Ahorro |
|---|---|---|---|---|
| sin límite | 38 | 195 | 195 | 0 % |
| ≤ 22 días, ≤ 6 saltos | 7 | **50** | **93** | 46,24 % |
| ≤ 20 días, ≤ 5 saltos | 5 | 43 | 65 | 33,85 % |

Las dos versiones encuentran las mismas rutas: con costos positivos un camino
parcial que ya se pasó del máximo no puede volver a bajar. La más barata (15)
coincide con Dijkstra.

### Hito 9 — tests y documentación

`./mvnw test` corre 89 tests sin Neo4j: uno o más por algoritmo
(`AlgoritmosTest`), las reglas e IA (`juego/JuegoTest`) y una partida completa
por el servicio (`service/JuegoServiceTest`). Fichas de endpoints en
`docs/ENDPOINTS.md`.

### Hito 10 — informe de complejidades

`docs/COMPLEJIDADES.md`.

### Hito 11 — Branch & Bound de reparto

Repartir las unidades compradas entre los frentes **maximizando el margen del
frente más débil** (margen = defensa − amenaza enemiga). Cota **superior**
optimista: `min( (Σ márgenes + poder restante) / k , frente más débil + poder restante )`.
Con el catálogo completo (poderes 13, 9, 8, 6, 5, 1) y dos columnas: el greedy
inicial da 20, la cota de la raíz es 21, y el B&B encuentra **21** ({13, 8}
contra {9, 6, 5, 1}) explorando 12 nodos con **4 podas por cota** y 1 por
simetría. Verificado contra fuerza bruta en 40 instancias aleatorias.

### Hito 12 — entrega final

Tag `entrega-final`.

### MIN-MAX y alfa-beta (clases 11 y 13)

Verificados con los fósforos de la clase 11 (se pierde exactamente cuando
n ≡ 1 mod 3) y con el árbol de la clase 13 (raíz 3, el 4 y el 6 no se miran:
11 nodos contra 13). Sobre el estado inicial del juego
(`GET /api/juego/{id}/ia/comparacion`), mismo valor y misma jugada:

| Profundidad | MIN-MAX | Alfa-beta | Ahorro |
|---|---|---|---|
| 2 | 91 nodos | 35 | 61,5 % |
| 3 | 834 | 192 | 77,0 % |
| 4 | 7.408 | 567 | 92,4 % |

## Arranque rápido

```bash
# 1. Neo4j local (por ejemplo con Docker) y credenciales
docker run -d --name neo4j-tpo -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/cambiame neo4j:5
cp .env.example .env          # completar NEO4J_PASSWORD
export $(cat .env | xargs)

# 2. Compilar y ejecutar (la primera vez carga el mapa)
./mvnw spring-boot:run

# 3. Jugar (cómo se juega: docs/Manual_de_Usuario.pdf)
#    http://localhost:8080          <- el juego en el navegador
curl "http://localhost:8080/api/grafo/resumen"
# -> {"vertices":12,"aristas":17,"dirigido":false,"ids":["AZU","BRA",...]}
```

Tests: `./mvnw test` (no necesitan Neo4j). Ejemplos de requests en `docs/api.http`.

## Estructura

```
src/main/java/uade/prog3/tpo/
    algorithm/      los algoritmos (sin Spring ni base: reciben estructuras en memoria)
    juego/          reglas (MotorDeJuego), estado clonable, mapa, IA (IaConquista, Estratega)
    model/          Nodo (territorio), Conexion (ruta), Item (unidad)
    repository/     acceso a Neo4j
    service/        Grafo (lista de adyacencia), GrafoService, JuegoService
    controller/     endpoints — sin lógica algorítmica
    seed/           MapaInicial (datos del dominio) y CargaInicial
src/main/resources/static/   el juego en el navegador (HTML + JS + SVG)
src/test/java/uade/prog3/tpo/
docs/
    Documento_Maestro_TPO.pdf   documento maestro del trabajo, en formato APA 7
    Manual_de_Usuario.pdf       manual de usuario del juego, con capturas
    ENDPOINTS.md        ficha de cada endpoint con complejidad justificada
    COMPLEJIDADES.md    informe de complejidades (hito 10)
    api.http            requests de ejemplo
```

## Uso de IA

Se utilizó Claude (Anthropic, Claude Code) como asistente durante el desarrollo, para:

- diseñar las reglas del juego de modo que cada fase use un algoritmo de la materia
- implementar los algoritmos del paquete `algorithm` y el motor de juego
- redactar los tests y la documentación de `docs/`
- calcular de forma independiente los valores de verificación (Dijkstra, MST,
  rutas, Floyd) y contrastarlos con los tests

La comprensión de los algoritmos no fue delegada: cada integrante puede
explicar el funcionamiento, la complejidad y las estructuras de datos de
todos los algoritmos entregados.

## Documento maestro

`docs/Documento_Maestro_TPO.pdf` reúne todo el trabajo en formato APA 7:
marco teórico, arquitectura, dominio y reglas, algoritmos con su
complejidad, la IA, la verificación de cada hito, el cumplimiento de la
rúbrica, la declaración de uso de IA y las referencias.

## Consignas

Ver `GUIA-TPO.md` y las consignas y rúbrica completas en
`TPO 2026 - Consignas y rubrica.pdf`.
