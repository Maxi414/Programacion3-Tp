package uade.prog3.tpo.juego;

import org.springframework.stereotype.Component;
import uade.prog3.tpo.algorithm.CaminosMinimos;
import uade.prog3.tpo.algorithm.HeuristicaGreedy;
import uade.prog3.tpo.algorithm.Minimax;
import uade.prog3.tpo.algorithm.Ordenamiento;
import uade.prog3.tpo.juego.Jugadas.PlanDeTurno;

import java.util.List;

/**
 * Decide la jugada de la IA segun la dificultad, y arma la comparacion
 * MIN-MAX contra alfa-beta que se muestra en el juego.
 */
@Component
public class Estratega {

    /** Lo que decidio la IA y cuanto le costo decidirlo. */
    public record ReporteIa(String jugador, String tecnica, PlanDeTurno plan, double valor,
                            long nodosVisitados, long cortes, int profundidad, int jugadasEnLaRaiz,
                            long milisegundos) { }

    /** Las dos busquedas sobre el mismo estado: mismo valor, distinto trabajo. */
    public record ComparacionIa(int profundidad, int jugadasEnLaRaiz,
                                double valorMinimax, long nodosMinimax, long msMinimax,
                                double valorAlfaBeta, long nodosAlfaBeta, long cortesAlfaBeta, long msAlfaBeta,
                                boolean mismoValor, double porcentajeAhorrado,
                                PlanDeTurno jugadaMinimax, PlanDeTurno jugadaAlfaBeta) { }

    private final MotorDeJuego motor;
    private final CaminosMinimos caminos;
    private final HeuristicaGreedy greedy;
    private final Ordenamiento ordenamiento;
    private final Minimax minimax;

    public Estratega(MotorDeJuego motor, CaminosMinimos caminos, HeuristicaGreedy greedy,
                     Ordenamiento ordenamiento, Minimax minimax) {
        this.motor = motor;
        this.caminos = caminos;
        this.greedy = greedy;
        this.ordenamiento = ordenamiento;
        this.minimax = minimax;
    }

    public IaConquista ia(Mapa mapa, int jugadorMax) {
        return new IaConquista(mapa, motor, caminos, greedy, ordenamiento, jugadorMax);
    }

    /** Los planes candidatos del jugador al que le toca, ya ordenados por la heuristica greedy. */
    public List<PlanDeTurno> planes(Mapa mapa, Estado e) {
        return ia(mapa, e.jugadorActual()).jugadas(e);
    }

    /**
     * Elige el plan del jugador que tiene el turno. Tiene que llamarse al
     * PRINCIPIO del turno (fase de reclutamiento), porque un plan es un turno
     * completo.
     */
    public ReporteIa decidir(Mapa mapa, Estado e, Dificultad dificultad) {
        validarInicioDeTurno(e);
        long inicio = System.nanoTime();
        IaConquista juego = ia(mapa, e.jugadorActual());
        List<PlanDeTurno> candidatos = juego.jugadas(e);
        String jugador = Estado.nombreJugador(e.jugadorActual());

        if (dificultad == Dificultad.FACIL) {
            // greedy: el primero del ranking ganancia/riesgo, sin mirar adelante
            PlanDeTurno elegido = candidatos.get(0);
            return new ReporteIa(jugador, "GREEDY (ratio ganancia/riesgo)", elegido, elegido.ratioGreedy(),
                    candidatos.size() + 1L, 0, 1, candidatos.size(), ms(inicio));
        }
        Minimax.Decision<PlanDeTurno> d = minimax.alfaBeta(juego, e, dificultad.profundidad());
        return new ReporteIa(jugador, d.tecnica(), d.jugada(), d.valor(), d.nodosVisitados(), d.cortes(),
                d.profundidad(), candidatos.size(), ms(inicio));
    }

    /**
     * Corre MIN-MAX puro y alfa-beta sobre el mismo estado y la misma
     * profundidad. El valor tiene que coincidir siempre; lo que cambia es la
     * cantidad de nodos visitados.
     */
    public ComparacionIa comparar(Mapa mapa, Estado e, int profundidad) {
        validarInicioDeTurno(e);
        if (profundidad < 1 || profundidad > 4) {
            throw new IllegalArgumentException("La profundidad del analisis va de 1 a 4 (MIN-MAX puro crece como b^d).");
        }
        IaConquista juego = ia(mapa, e.jugadorActual());
        int raiz = juego.jugadas(e).size();

        long t0 = System.nanoTime();
        Minimax.Decision<PlanDeTurno> mm = minimax.minimax(juego, e, profundidad);
        long msMm = ms(t0);
        long t1 = System.nanoTime();
        Minimax.Decision<PlanDeTurno> ab = minimax.alfaBeta(juego, e, profundidad);
        long msAb = ms(t1);

        double ahorro = mm.nodosVisitados() == 0 ? 0
                : Math.round(10000.0 * (mm.nodosVisitados() - ab.nodosVisitados()) / mm.nodosVisitados()) / 100.0;
        return new ComparacionIa(profundidad, raiz,
                mm.valor(), mm.nodosVisitados(), msMm,
                ab.valor(), ab.nodosVisitados(), ab.cortes(), msAb,
                Math.abs(mm.valor() - ab.valor()) < 1e-6, ahorro, mm.jugada(), ab.jugada());
    }

    private void validarInicioDeTurno(Estado e) {
        if (e.terminada()) {
            throw new IllegalStateException("La partida ya termino.");
        }
        if (e.fase() != Fase.RECLUTAMIENTO) {
            throw new IllegalStateException(
                    "La IA planea turnos completos: pedi el analisis al principio del turno, antes de comprar o mover.");
        }
    }

    private long ms(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
