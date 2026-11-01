package uade.prog3.tpo.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uade.prog3.tpo.juego.Mapa;
import uade.prog3.tpo.model.Nodo;
import uade.prog3.tpo.repository.ItemRepository;
import uade.prog3.tpo.repository.NodoRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carga el dominio del grupo en Neo4j: el mapa del juego de conquista
 * (12 territorios, 17 rutas) y el catalogo de 6 unidades. Reemplaza la
 * semilla generica (nodos A..H) del scaffold y el dominio anterior (red
 * logistica). Los datos salen de MapaInicial.
 *
 * Si la base tiene datos de OTRO dominio (la semilla A..H o la red logistica
 * de las versiones anteriores del trabajo) los borra y carga el mapa. Si ya
 * tiene el mapa, no hace nada.
 *
 * Propiedades del grafo, a proposito:
 *   - todos los costos positivos (dias de marcha): Dijkstra es aplicable
 *   - es conexo: Prim y Kruskal tienen solucion
 *   - PIN-CON directo (10) es mas caro que PIN-AZU-VAD-CON (9): se nota la
 *     relajacion de aristas
 *   - es ralo (cada territorio linda con 2 a 4): lista de adyacencia
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class CargaInicial implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);

    private final NodoRepository nodoRepository;
    private final ItemRepository itemRepository;

    public CargaInicial(NodoRepository nodoRepository, ItemRepository itemRepository) {
        this.nodoRepository = nodoRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public void run(String... args) {
        if (yaEstaCargadoElMapa()) {
            log.info("La base ya tiene el mapa del juego. No se ejecuta la carga inicial.");
            return;
        }
        if (nodoRepository.count() > 0 || itemRepository.count() > 0) {
            log.info("La base tiene datos de otro dominio: se borran para cargar el mapa del juego.");
            nodoRepository.deleteAll();
            itemRepository.deleteAll();
        }

        // tipo = terreno, valor = produccion (oro por turno)
        Map<String, Nodo> porId = new HashMap<>();
        for (Mapa.Territorio t : MapaInicial.TERRITORIOS) {
            porId.put(t.id(), new Nodo(t.id(), t.nombre(), t.terreno().name(), t.produccion(), t.x(), t.y()));
        }

        // costo = dias de marcha. Cada ruta se guarda UNA vez; el grafo se
        // carga como no dirigido (una ruta se recorre en los dos sentidos).
        for (Mapa.Ruta r : MapaInicial.RUTAS) {
            porId.get(r.origen()).conectar(porId.get(r.destino()), r.costo());
        }

        List<Nodo> nodos = MapaInicial.TERRITORIOS.stream().map(t -> porId.get(t.id())).toList();
        nodoRepository.saveAll(nodos);

        // catalogo del mercado: peso = costo en oro, valor = poder
        itemRepository.saveAll(MapaInicial.catalogo());

        log.info("Carga inicial completada: {} territorios, {} rutas, {} unidades.",
                MapaInicial.TERRITORIOS.size(), MapaInicial.RUTAS.size(), MapaInicial.catalogo().size());
        log.info("Para desactivarla: app.seed.enabled=false");
    }

    private boolean yaEstaCargadoElMapa() {
        Optional<Nodo> capital = nodoRepository.findById("AZU");
        return capital.isPresent()
                && "CIUDAD".equals(capital.get().getTipo())
                && nodoRepository.count() == MapaInicial.TERRITORIOS.size()
                && itemRepository.findById("U6").isPresent();
    }
}
