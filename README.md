# Programación III · TPO 2026

Trabajo Práctico Obligatorio de Programación III (3.4.077) — UADE, comisión
Miércoles Mañana. Basado en el [scaffold de la materia](https://github.com/Mancaceresuade/tpo-scaffold-2026).

## Integrantes

<!-- Completar: nombre completo y usuario de GitHub de cada integrante -->

- Nombre Apellido — [@usuario-github](https://github.com/usuario-github)

## Dominio

<!--
Hito 3 (obligatorio, 19/08): completar esta sección antes de reemplazar la
semilla del scaffold.
- ¿Qué es un nodo?
- ¿Qué es una arista y qué representa su costo? ¿En qué unidad?
- ¿Qué es un Item y qué restricción de capacidad tiene sentido sobre él?
- Diagrama con al menos 8 nodos y sus pesos.
-->

_Pendiente — el grafo semilla del scaffold sigue en uso mientras se define el
dominio propio del grupo (Hito 3)._

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
