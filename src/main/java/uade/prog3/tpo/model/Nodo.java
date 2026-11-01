package uade.prog3.tpo.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING;

/**
 * Vertice del grafo: en este trabajo, un TERRITORIO del mapa del juego.
 *
 *   - tipo  = terreno: "LLANURA" | "BOSQUE" | "CIUDAD" | "MONTANA"
 *             (define el bonus de defensa; solo las ciudades pueden ser capital)
 *   - valor = produccion: oro que rinde el territorio por turno si esta abastecido
 *   - x, y  = posicion en el dibujo del mapa (0..1000 x 0..700). No la usa
 *             ningun algoritmo: es solo para la interfaz.
 *
 * Las conexiones (relacion CONECTA) son las rutas entre territorios y su
 * costo son los DIAS DE MARCHA para cruzarla.
 *
 * Lo unico que NO se debe romper es que exista una relacion con COSTO NUMERICO
 * entre nodos: sin peso en las aristas no se pueden implementar Dijkstra,
 * Prim ni Kruskal, que son 3 de los 10 puntos del trabajo.
 */
@Node("Nodo")
public class Nodo {

    @Id
    private String id;

    private String nombre;

    /** Etiqueta libre para que el grupo distinga subtipos de su dominio. */
    private String tipo;

    /**
     * Atributo numerico propio del vertice (no de la arista).
     * Segun el dominio puede ser demanda, capacidad, poblacion, prioridad...
     * Se usa, por ejemplo, en los algoritmos greedy y de programacion dinamica.
     */
    private double valor;

    /** Posicion en el dibujo del mapa. Solo para la interfaz. */
    private double x;
    private double y;

    @Relationship(type = "CONECTA", direction = OUTGOING)
    private List<Conexion> conexiones = new ArrayList<>();

    public Nodo() {
    }

    public Nodo(String id, String nombre, String tipo, double valor) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.valor = valor;
    }

    public Nodo(String id, String nombre, String tipo, double valor, double x, double y) {
        this(id, nombre, tipo, valor);
        this.x = x;
        this.y = y;
    }

    public void conectar(Nodo destino, double costo) {
        this.conexiones.add(new Conexion(destino, costo));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public List<Conexion> getConexiones() { return conexiones; }
    public void setConexiones(List<Conexion> conexiones) { this.conexiones = conexiones; }

    @Override
    public String toString() {
        return "Nodo{id='" + id + "', nombre='" + nombre + "', tipo='" + tipo + "'}";
    }
}
