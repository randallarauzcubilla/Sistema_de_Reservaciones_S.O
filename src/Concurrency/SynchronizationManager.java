package Concurrency;

import Core.Reservation;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages synchronization for reservations, including capacity control,
 * equipment availability, and thread-safe access coordination.
 */
public class SynchronizationManager {

    private final ReentrantReadWriteLock rwlockCalendar
            = new ReentrantReadWriteLock(true);

    /**
     * @return read lock for calendar access
     */
    public ReentrantReadWriteLock.ReadLock lockReadCalendar() {
        return rwlockCalendar.readLock();
    }

    /**
     * @return write lock for calendar modifications
     */
    public ReentrantReadWriteLock.WriteLock lockWriteCalendar() {
        return rwlockCalendar.writeLock();
    }

    private final int maxCapacity;
    private final Semaphore capacitySemaphore;

    private final Semaphore projectorSemaphore;
    private final Semaphore microphoneSemaphore;
    private final Semaphore soundSemaphore;

    private final int totalProjectors;
    private final int totalMicrophones;
    private final int totalSound;

    private final ReentrantLock ttlMutex = new ReentrantLock(true);
    private final ReentrantLock logMutex = new ReentrantLock(true);

    /**
     * Initializes the synchronization manager with capacity and equipment
     * limits.
     *
     * @param maxCapacity maximum allowed attendees
     * @param projectorUnits total projectors available
     * @param microphoneUnits total microphones available
     * @param soundUnits total sound systems available
     */
    public SynchronizationManager(int maxCapacity,
            int projectorUnits, int microphoneUnits, int soundUnits) {
        this.maxCapacity = maxCapacity;
        this.capacitySemaphore = new Semaphore(maxCapacity, true);
        this.projectorSemaphore = new Semaphore(projectorUnits, true);
        this.microphoneSemaphore = new Semaphore(microphoneUnits, true);
        this.soundSemaphore = new Semaphore(soundUnits, true);
        this.totalProjectors = projectorUnits;
        this.totalMicrophones = microphoneUnits;
        this.totalSound = soundUnits;
    }

    /**
     * @return maximum room capacity
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * @return available capacity slots
     */
    public int getAvailableCapacity() {
        return capacitySemaphore.availablePermits();
    }

    /**
     * @return available projectors
     */
    public int getAvailableProjectors() {
        return projectorSemaphore.availablePermits();
    }

    /**
     * @return available microphones
     */
    public int getAvailableMicrophones() {
        return microphoneSemaphore.availablePermits();
    }

    /**
     * @return available sound systems
     */
    public int getAvailableSound() {
        return soundSemaphore.availablePermits();
    }

    public int getTotalProjectors() {
        return totalProjectors;
    }

    public int getTotalMicrophones() {
        return totalMicrophones;
    }

    public int getTotalSound() {
        return totalSound;
    }

    /**
     * @return lock used for TTL synchronization
     */
    public ReentrantLock getTtlMutex() {
        return ttlMutex;
    }

    /**
     * @return lock used for logging synchronization
     */
    public ReentrantLock getLogMutex() {
        return logMutex;
    }

    /**
     * Acquires capacity and equipment for a reservation.
     *
     * @param attendees number of attendees
     * @param equipment required equipment
     * @throws InterruptedException if acquisition is interrupted
     */
    public void acquireForReservation(int attendees,
            Reservation.Equipment equipment) throws InterruptedException {
        capacitySemaphore.acquire(attendees);
        try {
            acquireEquipment(equipment, 1);
        } catch (InterruptedException e) {
            capacitySemaphore.release(attendees);
            throw e;
        }
    }

    /**
     * Releases capacity and equipment from a reservation.
     *
     * @param attendees number of attendees
     * @param equipment equipment to release
     */
    public void releaseFromReservation(int attendees,
            Reservation.Equipment equipment) {
        releaseEquipment(equipment, 1);
        if (attendees > 0) {
            capacitySemaphore.release(attendees);
        }
    }

    /**
     * Acquires multiple equipment items atomically.
     *
     * @param items equipment and quantities required
     * @throws InterruptedException if acquisition fails
     */
    public void acquireEquipmentMap(Map<Reservation.Equipment, Integer> items)
            throws InterruptedException {
        java.util.List<Map.Entry<Reservation.Equipment, Integer>> acquired
                = new java.util.ArrayList<>();
        try {
            for (Map.Entry<Reservation.Equipment, Integer> e
                    : items.entrySet()) {
                acquireEquipment(e.getKey(), e.getValue());
                acquired.add(e);
            }
        } catch (InterruptedException ex) {
            for (Map.Entry<Reservation.Equipment, Integer> e : acquired) {
                releaseEquipment(e.getKey(), e.getValue());
            }
            throw ex;
        }
    }

    /**
     * Releases a set of equipment items.
     *
     * @param items equipment map to release
     */
    public void releaseEquipmentMap(Map<Reservation.Equipment, Integer> items) {
        if (items == null) {
            return;
        }

        for (Map.Entry<Reservation.Equipment, Integer> e : items.entrySet()) {
            releaseEquipment(e.getKey(), e.getValue());
        }
    }

    /**
     * Checks if a set of equipment is available.
     *
     * @param items equipment requirements
     * @return true if all items are available
     */
    public boolean isEquipmentMapAvailable(
            Map<Reservation.Equipment, Integer> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        for (Map.Entry<Reservation.Equipment, Integer> e : items.entrySet()) {
            if (!isSingleAvailable(e.getKey(), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Acquires a single unit of the specified equipment.
     *
     * @param equipment equipment type to acquire
     * @throws InterruptedException if the thread is interrupted
     */
    public void acquireEquipmentOnly(Reservation.Equipment equipment)
            throws InterruptedException {
        acquireEquipment(equipment, 1);
    }

    /**
     * Releases a single unit of the specified equipment.
     *
     * @param equipment equipment type to release
     */
    public void releaseEquipmentOnly(Reservation.Equipment equipment) {
        releaseEquipment(equipment, 1);
    }

    /**
     * Checks if a specific equipment type is available in the requested
     * quantity.
     *
     * @param eq equipment type
     * @param qty required quantity
     * @return true if available, false otherwise
     */
    private boolean isSingleAvailable(Reservation.Equipment eq, int qty) {
        if (eq == null) {
            return true;
        }

        switch (eq) {
            case PROYECTOR:
                return projectorSemaphore.availablePermits() >= qty;
            case MICROFONO:
                return microphoneSemaphore.availablePermits() >= qty;
            case SONIDO:
                return soundSemaphore.availablePermits() >= qty;
            case COMPLETO:
                return projectorSemaphore.availablePermits() >= qty
                        && microphoneSemaphore.availablePermits() >= qty
                        && soundSemaphore.availablePermits() >= qty;
            default:
                return true;
        }
    }

    /**
     * Acquires the requested equipment in the given quantity.
     *
     * @param eq equipment type
     * @param qty quantity to acquire
     * @throws InterruptedException if acquisition is interrupted
     */
    private void acquireEquipment(Reservation.Equipment eq, int qty)
            throws InterruptedException {
        if (eq == null) {
            return;
        }

        switch (eq) {
            case PROYECTOR:
                projectorSemaphore.acquire(qty);
                break;
            case MICROFONO:
                microphoneSemaphore.acquire(qty);
                break;
            case SONIDO:
                soundSemaphore.acquire(qty);
                break;
            case COMPLETO:
                projectorSemaphore.acquire(qty);
                microphoneSemaphore.acquire(qty);
                soundSemaphore.acquire(qty);
                break;
            default:
                break;
        }
    }

    /**
     * Releases the requested equipment in the given quantity.
     *
     * @param eq equipment type
     * @param qty quantity to release
     */
    private void releaseEquipment(Reservation.Equipment eq, int qty) {
        if (eq == null) {
            return;
        }

        switch (eq) {
            case PROYECTOR:
                projectorSemaphore.release(qty);
                break;
            case MICROFONO:
                microphoneSemaphore.release(qty);
                break;
            case SONIDO:
                soundSemaphore.release(qty);
                break;
            case COMPLETO:
                soundSemaphore.release(qty);
                microphoneSemaphore.release(qty);
                projectorSemaphore.release(qty);
                break;
            default:
                break;
        }
    }
}