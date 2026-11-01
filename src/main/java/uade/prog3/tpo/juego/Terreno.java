package uade.prog3.tpo.juego;

/**
 * Tipo de terreno de un territorio. Es lo que se guarda en el campo 'tipo' del
 * Nodo en Neo4j.
 *
 * El terreno da un BONUS DE DEFENSA: para conquistar un territorio hay que
 * mandar mas tropas que (tropas defensoras + bonus). Las montanas son caras
 * de tomar; las llanuras, baratas.
 */
public enum Terreno {

    LLANURA(0, "Llanura"),
    BOSQUE(1, "Bosque"),
    CIUDAD(1, "Ciudad"),
    MONTANA(2, "Montaña");

    private final int bonusDefensa;
    private final String nombre;

    Terreno(int bonusDefensa, String nombre) {
        this.bonusDefensa = bonusDefensa;
        this.nombre = nombre;
    }

    public int bonusDefensa() {
        return bonusDefensa;
    }

    public String nombre() {
        return nombre;
    }

    /** Solo las ciudades pueden ser capitales. */
    public boolean puedeSerCapital() {
        return this == CIUDAD;
    }
}
