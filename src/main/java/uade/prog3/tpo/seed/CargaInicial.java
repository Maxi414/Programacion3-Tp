package uade.prog3.tpo.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.model.Nodo;
import uade.prog3.tpo.repository.ItemRepository;
import uade.prog3.tpo.repository.NodoRepository;

import java.util.List;

/**
 * Carga el grafo del dominio propio del grupo: una red logistica de ultima
 * milla (reparto de paquetes desde un deposito central). Reemplaza la
 * semilla generica (nodos A..H) del scaffold. Detalle completo del dominio
 * en el README, seccion "Dominio".
 *
 * El grafo tiene, a proposito, las mismas propiedades que exigia la
 * semilla original:
 *   - 8 vertices y 12 aristas: chico para verificar a mano, grande para
 *     que los algoritmos no den resultados triviales
 *   - todos los costos positivos (km), para que Dijkstra sea aplicable
 *   - es conexo, para que Prim y Kruskal tengan solucion
 *   - CD-BE directo (12 km) es mas caro que CD-PA-BE (5+4=9 km), para
 *     que se note la relajacion de aristas
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
        if (nodoRepository.count() > 0) {
            log.info("La base ya tiene datos. No se ejecuta la carga inicial.");
            return;
        }

        // valor = demanda diaria promedio, en cantidad de paquetes (0 para el deposito)
        Nodo cd = new Nodo("CD", "Deposito Central", "DEPOSITO", 0);
        Nodo pa = new Nodo("PA", "Palermo", "CLIENTE", 12);
        Nodo be = new Nodo("BE", "Belgrano", "CLIENTE", 8);
        Nodo re = new Nodo("RE", "Recoleta", "CLIENTE", 15);
        Nodo al = new Nodo("AL", "Almagro", "CLIENTE", 6);
        Nodo bo = new Nodo("BO", "Boedo", "CLIENTE", 20);
        Nodo fl = new Nodo("FL", "Flores", "CLIENTE", 9);
        Nodo cb = new Nodo("CB", "Caballito", "CLIENTE", 4);

        // costo = distancia en km.
        // El camino directo CD->BE cuesta 12, pero CD->PA->BE cuesta 5+4=9.
        // Sirve para verificar que la relajacion de aristas este bien hecha.
        cd.conectar(pa, 5);
        cd.conectar(be, 12);
        pa.conectar(be, 4);
        pa.conectar(re, 8);
        be.conectar(re, 3);
        be.conectar(al, 10);
        re.conectar(al, 6);
        re.conectar(bo, 9);
        al.conectar(bo, 7);
        al.conectar(fl, 11);
        bo.conectar(cb, 4);
        fl.conectar(cb, 5);

        nodoRepository.saveAll(List.of(cd, pa, be, re, al, bo, fl, cb));

        // Paquetes para los problemas de seleccion bajo restriccion
        // (greedy, mochila 0/1 con PD, Branch & Bound de reparto entre vehiculos).
        // peso = kg que consume del vehiculo, valor = valor asegurado en pesos.
        //
        // Calibrado a proposito: con capacidad 10 kg, el greedy por ratio
        // valor/peso elige {I4, I2} y obtiene 90, mientras que el optimo real
        // es {I4, I5} con 105. Es el contraejemplo que pide documentar el
        // Hito 6 (greedy contra programacion dinamica).
        itemRepository.saveAll(List.of(
                new Item("I1", "Paquete 1", 5, 10, "CD"),
                new Item("I2", "Paquete 2", 4, 40, "PA"),
                new Item("I3", "Paquete 3", 6, 30, "BE"),
                new Item("I4", "Paquete 4", 3, 50, "RE"),
                new Item("I5", "Paquete 5", 7, 55, "AL")
        ));

        log.info("Carga inicial completada: 8 nodos, 12 aristas, 5 paquetes.");
        log.info("Para desactivarla: app.seed.enabled=false");
    }
}
