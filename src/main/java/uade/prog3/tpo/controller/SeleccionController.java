package uade.prog3.tpo.controller;

import org.springframework.web.bind.annotation.*;
import uade.prog3.tpo.algorithm.BusquedaBinaria;
import uade.prog3.tpo.algorithm.Huffman;
import uade.prog3.tpo.algorithm.Ordenamiento;
import uade.prog3.tpo.algorithm.RamificacionYPoda;
import uade.prog3.tpo.algorithm.Seleccion;
import uade.prog3.tpo.model.Item;
import uade.prog3.tpo.repository.ItemRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de seleccion, ordenamiento y reparto sobre el CATALOGO DE
 * UNIDADES del juego (Item: peso = costo en oro, valor = poder).
 * Igual que en GrafoController: la logica va en el paquete algorithm.
 */
@RestController
@RequestMapping("/api/seleccion")
public class SeleccionController {

    private final ItemRepository itemRepository;
    private final Seleccion seleccion;
    private final Ordenamiento ordenamiento;
    private final RamificacionYPoda ramificacionYPoda;
    private final BusquedaBinaria busquedaBinaria;
    private final Huffman huffman;

    public SeleccionController(ItemRepository itemRepository,
                               Seleccion seleccion,
                               Ordenamiento ordenamiento,
                               RamificacionYPoda ramificacionYPoda,
                               BusquedaBinaria busquedaBinaria,
                               Huffman huffman) {
        this.itemRepository = itemRepository;
        this.seleccion = seleccion;
        this.ordenamiento = ordenamiento;
        this.ramificacionYPoda = ramificacionYPoda;
        this.busquedaBinaria = busquedaBinaria;
        this.huffman = huffman;
    }

    private List<Item> todos() {
        return itemRepository.findAll();
    }

    /** GET /api/seleccion/greedy?capacidad=6   (capacidad = oro disponible) */
    @GetMapping("/greedy")
    public Seleccion.Resultado greedy(@RequestParam double capacidad) {
        return seleccion.greedy(todos(), capacidad);
    }

    /** GET /api/seleccion/dinamica?capacidad=6 */
    @GetMapping("/dinamica")
    public Seleccion.Resultado dinamica(@RequestParam int capacidad) {
        return seleccion.programacionDinamica(todos(), capacidad);
    }

    /**
     * GET /api/seleccion/quicksort?criterio=ratio
     * criterio admite: valor | poder | peso | costo | ratio
     */
    @GetMapping("/quicksort")
    public List<Item> quickSort(@RequestParam(defaultValue = "ratio") String criterio) {
        return ordenamiento.quickSort(todos(), comparador(criterio));
    }

    /** GET /api/seleccion/mergesort?criterio=costo */
    @GetMapping("/mergesort")
    public List<Item> mergeSort(@RequestParam(defaultValue = "ratio") String criterio) {
        return ordenamiento.mergeSort(todos(), comparador(criterio));
    }

    /**
     * GET /api/seleccion/binaria?oro=6
     * El catalogo se ordena por costo (MergeSort) y la busqueda binaria encuentra
     * la ultima unidad que se puede pagar.
     */
    @GetMapping("/binaria")
    public Map<String, Object> binaria(@RequestParam double oro) {
        List<Item> porCosto = ordenamiento.mergeSort(todos(), Comparator.comparingDouble(Item::getPeso));
        int ultimo = busquedaBinaria.ultimoMenorOIgual(porCosto, oro, Item::getPeso);
        return Map.of(
                "oro", oro,
                "catalogoPorCosto", porCosto,
                "ultimoAsequible", ultimo,
                "asequibles", busquedaBinaria.asequibles(porCosto, oro));
    }

    /** GET /api/seleccion/repartir?frentes=2   (Branch & Bound: max-min del poder por frente) */
    @GetMapping("/repartir")
    public RamificacionYPoda.Reparto repartir(@RequestParam(defaultValue = "2") int frentes) {
        if (frentes < 1) {
            throw new IllegalArgumentException("La cantidad de frentes debe ser al menos 1");
        }
        return ramificacionYPoda.repartir(todos(), frentes);
    }

    /** GET /api/seleccion/huffman?texto=...   (codigo de Huffman sobre un texto cualquiera) */
    @GetMapping("/huffman")
    public Map<String, Object> huffman(@RequestParam String texto) {
        Huffman.Compresion c = huffman.comprimir(texto);
        return Map.of(
                "codigos", c.codigos(),
                "bits", c.bits(),
                "bitsOriginales", c.bitsOriginales(),
                "bitsComprimidos", c.bitsComprimidos(),
                "tasaCompresion", c.tasaCompresion(),
                "decodificado", huffman.descomprimir(c.bits(), c.arbol()));
    }

    private Comparator<Item> comparador(String criterio) {
        return switch (criterio) {
            case "valor", "poder" -> Comparator.comparingDouble(Item::getValor);
            case "peso", "costo" -> Comparator.comparingDouble(Item::getPeso);
            case "ratio" -> Comparator.comparingDouble(Item::ratioValorPeso);
            default -> throw new IllegalArgumentException(
                    "Criterio invalido: " + criterio + ". Use poder, costo o ratio.");
        };
    }
}
