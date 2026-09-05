# Qué hay en este paquete

Los siete algoritmos del TPO implementados contra las firmas reales del
scaffold, con tests y documentación. Los archivos van tal cual sobre el repo,
respetando las rutas.

```
src/main/java/uade/prog3/tpo/algorithm/
    Recorridos.java          BFS y DFS                        2 puntos
    CaminosMinimos.java      Dijkstra, Floyd-Warshall, UCS    1 + 1 puntos
    ArbolRecubrimiento.java  Prim y Kruskal (Union-Find)      2 puntos
    Ordenamiento.java        QuickSort y MergeSort            1 punto
    Seleccion.java           Greedy y mochila 0/1 con PD      2 puntos
    Backtracking.java        Rutas simples con poda           1 punto
    RamificacionYPoda.java   Branch & Bound de reparto        1 punto
src/test/java/uade/prog3/tpo/
    AlgoritmosTest.java      31 tests, al menos uno por algoritmo
docs/
    ENDPOINTS.md             ficha de cada endpoint con complejidad justificada
    COMPLEJIDADES.md         informe del hito 10
verificacion/
    Verificacion.java        corre los 43 chequeos sin Maven ni Neo4j
```

## Verificación

Los 43 chequeos pasan contra los valores que `GUIA-TPO.md` da calculados a mano:

- `dijkstra(A,C)` = 7 por A→B→C, no 9 por el directo
- `dijkstra(A,H)` = 20 por A→B→C→D→F→H
- `prim(A)` y `kruskal()` = 27, con 7 aristas
- el MST de Kruskal es exactamente C-D(2) B-C(3) F-H(3) A-B(4) G-H(4) D-E(5) E-F(6)
- greedy con capacidad 10 da 90 y el óptimo por PD da 105

Para correrlos sin levantar nada:

```bash
cd verificacion
javac --release 17 -d classes $(find . -name "*.java")
java -cp classes Verificacion
```

Después, con Neo4j arriba: `./mvnw test`

## Único cambio al scaffold

El record `RamificacionYPoda.Asignacion` tiene tres campos nuevos
(`nodosExplorados`, `nodosPodados`, `cotaInicial`) porque el hito 11 pide
reportar los nodos podados. El controller no necesita ningún cambio.

## Lo que falta y no se puede hacer sin el grupo

1. **Hito 3 — dominio.** Decidirlo entre los cuatro y escribir la página del
   README: qué es un nodo, qué es una arista, qué representa el costo y en qué
   unidad, más el diagrama de 8+ nodos con pesos.
2. **Hito 4 — cargar el dominio propio** en Neo4j reemplazando `CargaInicial`,
   y regenerar los ejemplos de `docs/ENDPOINTS.md` con los nuevos valores.
3. **README** con los integrantes (nombre completo y usuario de GitHub) y la
   sección «Uso de IA» que exige el punto 7 del enunciado.
4. **Commits repartidos.** La admisibilidad exige que el historial muestre
   participación de todos. Si esto se commitea desde una sola cuenta, la
   entrega se devuelve sin corregir aunque el código esté perfecto.

## Sección «Uso de IA» para el README (borrador)

```markdown
## Uso de IA

Se utilizó Claude (Anthropic) como asistente durante el desarrollo, para:
- implementar los algoritmos del paquete `algorithm` a partir de las firmas
  provistas por el scaffold
- redactar los tests de `AlgoritmosTest` y la documentación de `docs/`
- verificar los resultados contra los valores de referencia de `GUIA-TPO.md`

La comprensión de los algoritmos no fue delegada: cada integrante puede
explicar el funcionamiento, la complejidad y las estructuras de datos de
todos los algoritmos entregados.
```

> Esa última frase hay que poder sostenerla. El Segundo Parcial es escribir
> estos mismos algoritmos a mano, sin asistentes y sin el repositorio.
