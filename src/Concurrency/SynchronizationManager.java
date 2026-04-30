package Concurrency;

import Core.Reservation;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages synchronization for shared resources in the reservation system.
 * It controls access to calendar operations, capacity limits, and equipment
 * using locks and semaphores to ensure thread safety.
 */
public class SynchronizationManager {

    private final ReentrantReadWriteLock rwlockCalendar =
            new ReentrantReadWriteLock(true);

    /**
     * Provides the read lock for calendar access.
     *
     * @return the read lock of the calendar
     */
    public ReentrantReadWriteLock.ReadLock lockReadCalendar() {
        return rwlockCalendar.readLock();
    }

    /**
     * Provides the write lock for calendar access.
     *
     * @return the write lock of the calendar
     */
    public ReentrantReadWriteLock.WriteLock lockWriteCalendar() {
        return rwlockCalendar.writeLock();
    }

    private final int maxCapacity;
    private final Semaphore capacitySemaphore;

    private final Semaphore projectorSemaphore;
    private final Semaphore microphoneSemaphore;
    private final Semaphore soundSemaphore;

    private final ReentrantLock ttlMutex = new ReentrantLock(true);
    private final ReentrantLock logMutex = new ReentrantLock(true);

    /**
     * Creates a new synchronization manager with resource limits.
     *
     * @param maxCapacity maximum number of attendees allowed
     * @param projectorUnits number of available projectors
     * @param microphoneUnits number of available microphones
     * @param soundUnits number of available sound systems
     */
    public SynchronizationManager(int maxCapacity,
                               int projectorUnits,
                               int microphoneUnits,
                               int soundUnits) {

        this.maxCapacity = maxCapacity;

        this.capacitySemaphore = new Semaphore(maxCapacity, true);
        this.projectorSemaphore = new Semaphore(projectorUnits, true);
        this.microphoneSemaphore = new Semaphore(microphoneUnits, true);
        this.soundSemaphore = new Semaphore(soundUnits, true);
    }

    /**
     * @return maximum capacity allowed in the system
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * @return number of available capacity permits
     */
    public int getAvailableCapacity() {
        return capacitySemaphore.availablePermits();
    }

    /**
     * Acquires capacity and required equipment for a reservation.
     *
     * @param attendees number of attendees in the reservation
     * @param equipment required equipment type
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void acquireForReservation(int attendees, 
            Reservation.Equipment equipment)
            throws InterruptedException {

        capacitySemaphore.acquire(attendees);

        try {
            acquireEquipment(equipment);
        } catch (InterruptedException e) {
            capacitySemaphore.release(attendees);
            throw e;
        }
    }

    /** Acquires only the equipment semaphore — capacity is managed per time range.
     * @param equipment
     * @throws java.lang.InterruptedException */
    public void acquireEquipmentOnly(Reservation.Equipment equipment)
            throws InterruptedException {
        acquireEquipment(equipment);
    }

    /** Releases only the equipment semaphore.
     * @param equipment */
    public void releaseEquipmentOnly(Reservation.Equipment equipment) {
        releaseEquipment(equipment);
    }

    /**
     * Releases capacity and equipment from a reservation.
     *
     * @param attendees number of attendees to release
     * @param equipment equipment type to release
     */
    public void releaseFromReservation(int attendees, Reservation.Equipment 
            equipment) {

        releaseEquipment(equipment);

        if (attendees > 0) {
            capacitySemaphore.release(attendees);
        }
    }

    /**
     * Acquires required equipment semaphores.
     *
     * @param equipment equipment type requested
     * @throws InterruptedException if acquisition is interrupted
     */
    private void acquireEquipment(Reservation.Equipment equipment)
            throws InterruptedException {

        if (equipment == null) return;

        switch (equipment) {

            case PROYECTOR:
                projectorSemaphore.acquire();
                break;

            case MICROFONO:
                microphoneSemaphore.acquire();
                break;

            case SONIDO:
                soundSemaphore.acquire();
                break;

            case COMPLETO:
                projectorSemaphore.acquire();
                microphoneSemaphore.acquire();
                soundSemaphore.acquire();
                break;

            case NINGUNO:
            default:
                break;
        }
    }

    /**
     * Releases previously acquired equipment semaphores.
     *
     * @param equipment equipment type to release
     */
    private void releaseEquipment(Reservation.Equipment equipment) {

        if (equipment == null) return;

        switch (equipment) {

            case PROYECTOR:
                projectorSemaphore.release();
                break;

            case MICROFONO:
                microphoneSemaphore.release();
                break;

            case SONIDO:
                soundSemaphore.release();
                break;

            case COMPLETO:
                soundSemaphore.release();
                microphoneSemaphore.release();
                projectorSemaphore.release();
                break;

            case NINGUNO:
            default:
                break;
        }
    }

    /**
     * @return mutex used for TTL thread synchronization
     */
    public ReentrantLock getTtlMutex() {
        return ttlMutex;
    }

    /**
     * @return mutex used for logging synchronization
     */
    public ReentrantLock getLogMutex() {
        return logMutex;
    }

    /**
     * @return number of available projectors
     */
    public int getAvailableProjectors() {
        return projectorSemaphore.availablePermits();
    }

    /**
     * @return number of available microphones
     */
    public int getAvailableMicrophones() {
        return microphoneSemaphore.availablePermits();
    }

    /**
     * @return number of available sound systems
     */
    public int getAvailableSound() {
        return soundSemaphore.availablePermits();
    }
}