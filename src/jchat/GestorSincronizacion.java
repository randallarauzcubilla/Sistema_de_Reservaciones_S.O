package jchat;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GestorSincronizacion {

    // NIVEL 1: Calendario (RWLock)
    private final ReentrantReadWriteLock rwlockCalendario =
            new ReentrantReadWriteLock(true);

    public ReentrantReadWriteLock.ReadLock lockLecturaCalendario() {
        return rwlockCalendario.readLock();
    }
    public ReentrantReadWriteLock.WriteLock lockEscrituraCalendario() {
        return rwlockCalendario.writeLock();
    }

    // NIVEL 2: Capacidad máxima (Semáforo contador)
    private final Semaphore semCapacidad;

    // NIVEL 3: Equipamiento (Semáforos por tipo)
    private final Semaphore semProyector;
    private final Semaphore semMicrofono;
    private final Semaphore semSonido;

    // NIVEL 4: Cola TTL (ReentrantLock)
    private final ReentrantLock mutexTTL = new ReentrantLock(true);

    // NIVEL 5: Bitácora (ReentrantLock)
    private final ReentrantLock mutexBitacora = new ReentrantLock(true);

    public GestorSincronizacion(int capacidadMaxima,
                                 int unidadesProyector,
                                 int unidadesMicrofono,
                                 int unidadesSonido) {
        this.semCapacidad = new Semaphore(capacidadMaxima,  true);
        this.semProyector = new Semaphore(unidadesProyector, true);
        this.semMicrofono = new Semaphore(unidadesMicrofono, true);
        this.semSonido    = new Semaphore(unidadesSonido,    true);
    }

    public Semaphore getSemCapacidad() { return semCapacidad; }
    public Semaphore getSemProyector() { return semProyector; }
    public Semaphore getSemMicrofono() { return semMicrofono; }
    public Semaphore getSemSonido()    { return semSonido;    }
    public ReentrantLock getMutexTTL()      { return mutexTTL;     }
    public ReentrantLock getMutexBitacora() { return mutexBitacora; }

    public void adquirirParaReserva(int asistentes, Reserva.Equipo equipo)
            throws InterruptedException {
        semCapacidad.acquire(asistentes);
        try {
            adquirirEquipo(equipo);
        } catch (InterruptedException e) {
            semCapacidad.release(asistentes);
            throw e;
        }
    }

    public void liberarDeReserva(int asistentes, Reserva.Equipo equipo) {
        liberarEquipo(equipo);
        if (asistentes > 0) semCapacidad.release(asistentes);
    }

    private void adquirirEquipo(Reserva.Equipo equipo)
            throws InterruptedException {
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
                try {
                    semMicrofono.acquire();
                    try {
                        semSonido.acquire();
                    } catch (InterruptedException e) {
                        semMicrofono.release();
                        throw e;
                    }
                } catch (InterruptedException e) {
                    semProyector.release();
                    throw e;
                }
                break;
            case NINGUNO:
            default:
                break;
        }
    }

    private void liberarEquipo(Reserva.Equipo equipo) {
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

    public int capacidadDisponible()    { return semCapacidad.availablePermits(); }
    public int proyectoresDisponibles() { return semProyector.availablePermits(); }
    public int microfonosDisponibles()  { return semMicrofono.availablePermits(); }
    public int sonidosDisponibles()     { return semSonido.availablePermits();    }
}