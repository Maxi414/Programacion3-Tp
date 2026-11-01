package uade.prog3.tpo.juego;

/**
 * Estado DINAMICO de una partida: quien es dueno de cada territorio, cuantas
 * tropas tiene, el oro de cada jugador y de quien es el turno.
 *
 * DECISION DE DISENO CLAVE PARA EL MINIMAX: el estado es chico y se clona
 * barato. Son unos pocos arreglos de int indexados igual que los territorios
 * del Mapa (que es lo estatico y NO se copia). Copiar un estado es O(V); asi
 * el minimax puede generar miles de hijos sin retroceso explicito: cada
 * jugada produce una copia nueva y el padre queda intacto.
 */
public final class Estado {

    public static final int NEUTRAL = 0;
    public static final int AZUL = 1;   // el humano
    public static final int ROJO = 2;   // la IA
    public static final int EMPATE = 3; // valor posible de 'ganador'

    final int[] duenio;
    final int[] tropas;
    final int[] oro = new int[3];       // indice = jugador (0 sin uso)
    final int[] capital = new int[3];   // indice del territorio capital de cada jugador
    int jugadorActual = AZUL;
    int ronda = 1;                      // una ronda = un turno de cada jugador
    Fase fase = Fase.RECLUTAMIENTO;
    int ganador = 0;                    // 0 = en curso, AZUL, ROJO o EMPATE

    public Estado(int cantidadTerritorios) {
        this.duenio = new int[cantidadTerritorios];
        this.tropas = new int[cantidadTerritorios];
    }

    private Estado(Estado otro) {
        this.duenio = otro.duenio.clone();
        this.tropas = otro.tropas.clone();
        System.arraycopy(otro.oro, 0, this.oro, 0, 3);
        System.arraycopy(otro.capital, 0, this.capital, 0, 3);
        this.jugadorActual = otro.jugadorActual;
        this.ronda = otro.ronda;
        this.fase = otro.fase;
        this.ganador = otro.ganador;
    }

    /** Copia profunda en O(V). */
    public Estado copia() {
        return new Estado(this);
    }

    public static int rival(int jugador) {
        return jugador == AZUL ? ROJO : AZUL;
    }

    public static String nombreJugador(int jugador) {
        return switch (jugador) {
            case AZUL -> "AZUL";
            case ROJO -> "ROJO";
            case EMPATE -> "EMPATE";
            default -> "NEUTRAL";
        };
    }

    public int duenio(int territorio) { return duenio[territorio]; }
    public int tropas(int territorio) { return tropas[territorio]; }
    public int oro(int jugador) { return oro[jugador]; }
    public int capital(int jugador) { return capital[jugador]; }
    public int jugadorActual() { return jugadorActual; }
    public int ronda() { return ronda; }
    public Fase fase() { return fase; }
    public int ganador() { return ganador; }
    public boolean terminada() { return ganador != 0; }
    public int cantidadTerritorios() { return duenio.length; }
}
