package Concurrency;

import Core.Reservation;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SynchronizationManager {

    private final ReentrantReadWriteLock rwlockCalendario =
            new ReentrantReadWriteLock(true);

    public ReentrantReadWriteLock.ReadLock lockLecturaCalendario() {
        return rwlockCalendario.readLock();
    }

    public ReentrantReadWriteLock.WriteLock lockEscrituraCalendario() {
        return rwlockCalendario.writeLock();
    }

    private final int capacidadMaxima;
    private final Semaphore semCapacidad;

    private final Semaphore semProyector;
    private final Semaphore semMicrofono;
    private final Semaphore semSonido;

    private final ReentrantLock mutexTTL = new ReentrantLock(true);
    private final ReentrantLock mutexBitacora = new ReentrantLock(true);

    public SynchronizationManager(int capacidadMaxima,
                               int unidadesProyector,
                               int unidadesMicrofono,
                               int unidadesSonido) {

        this.capacidadMaxima = capacidadMaxima;

        this.semCapacidad = new Semaphore(capacidadMaxima, true);
        this.semProyector = new Semaphore(unidadesProyector, true);
        this.semMicrofono = new Semaphore(unidadesMicrofono, true);
        this.semSonido = new Semaphore(unidadesSonido, true);
    }

    public int getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public int capacidadDisponible() {
        return semCapacidad.availablePermits();
    }

    public void adquirirParaReserva(int asistentes, Reservation.Equipo equipo)
            throws InterruptedException {

        semCapacidad.acquire(asistentes);

        try {
            adquirirEquipo(equipo);
        } catch (InterruptedException e) {
            semCapacidad.release(asistentes);
            throw e;
        }
    }

    public void liberarDeReserva(int asistentes, Reservation.Equipo equipo) {

        liberarEquipo(equipo);

        if (asistentes > 0) {
            semCapacidad.release(asistentes);
        }
    }

    private void adquirirEquipo(Reservation.Equipo equipo)
            throws InterruptedException {

        if (equipo == null) return;

        switch (equipo) {

            case PROYECTOR:
                semProyector.acquire();
                break;

            case MICROFONO:
                semMicrofono.acquire();
                break;

            case SONIDO:
                semSonido.acquire();
                break;

            case COMPLETO:
                semProyector.acquire();
                semMicrofono.acquire();
                semSonido.acquire();
                break;

            case NINGUNO:
            default:
                break;
        }
    }

    private void liberarEquipo(Reservation.Equipo equipo) {

        if (equipo == null) return;

        switch (equipo) {

            case PROYECTOR:
                semProyector.release();
                break;

            case MICROFONO:
                semMicrofono.release();
                break;

            case SONIDO:
                semSonido.release();
                break;

            case COMPLETO:
                semSonido.release();
                semMicrofono.release();
                semProyector.release();
                break;

            case NINGUNO:
            default:
                break;
        }
    }

    public ReentrantLock getMutexTTL() {
        return mutexTTL;
    }

    public ReentrantLock getMutexBitacora() {
        return mutexBitacora;
    }

    public int proyectoresDisponibles() {
        return semProyector.availablePermits();
    }

    public int microfonosDisponibles() {
        return semMicrofono.availablePermits();
    }

    public int sonidosDisponibles() {
        return semSonido.availablePermits();
    }
}