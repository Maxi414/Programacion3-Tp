package uade.prog3.tpo.juego;

import uade.prog3.tpo.algorithm.GeneradorDeEscenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Una partida en curso: el mapa (estatico), el estado (dinamico), la
 * dificultad de la IA y el registro de lo que paso.
 *
 * Hay dos registros:
 *  - eventos: texto para mostrarle al jugador ("AZUL conquista CON...")
 *  - replay: una linea compacta por turno, pensada para guardarse y
 *    comprimirse con Huffman. Formato de cada turno:
 *        <ronda><A|R>:C<unidad>@<destino>,M<origen>><destino>x<tropas>,A<origen>><destino>x<tropas><+|->;
 *    ejemplo: 1A:CU5@VAD,AVAD>CONx11+;
 */
public final class Partida {

    private final String id;
    private final Mapa mapa;
    private final Dificultad dificultad;
    private final GeneradorDeEscenario.Escenario escenario;
    private final Estado estado;
    private final List<String> eventos = new ArrayList<>();
    private final StringBuilder replay = new StringBuilder();
    private final List<String> accionesDelTurno = new ArrayList<>();
    private Estratega.ReporteIa ultimoReporteIa;

    public Partida(String id, Mapa mapa, Dificultad dificultad,
                   GeneradorDeEscenario.Escenario escenario, Estado estado) {
        this.id = id;
        this.mapa = mapa;
        this.dificultad = dificultad;
        this.escenario = escenario;
        this.estado = estado;
    }

    public String id() { return id; }
    public Mapa mapa() { return mapa; }
    public Dificultad dificultad() { return dificultad; }
    public GeneradorDeEscenario.Escenario escenario() { return escenario; }
    public Estado estado() { return estado; }
    public List<String> eventos() { return Collections.unmodifiableList(eventos); }
    public String replay() { return replay.toString(); }
    public Estratega.ReporteIa ultimoReporteIa() { return ultimoReporteIa; }

    public void registrar(String evento) {
        eventos.add(evento);
    }

    /** Anota una accion del turno en curso para el replay (ej. "CU5@VAD"). */
    public void registrarAccion(String accion) {
        accionesDelTurno.add(accion);
    }

    /** Cierra el turno en el replay: "<ronda><A|R>:<acciones separadas por coma>;". */
    public void cerrarTurno(int ronda, int jugador) {
        replay.append(ronda).append(jugador == Estado.AZUL ? 'A' : 'R').append(':')
                .append(String.join(",", accionesDelTurno)).append(';');
        accionesDelTurno.clear();
    }

    public void setUltimoReporteIa(Estratega.ReporteIa reporte) {
        this.ultimoReporteIa = reporte;
    }
}
