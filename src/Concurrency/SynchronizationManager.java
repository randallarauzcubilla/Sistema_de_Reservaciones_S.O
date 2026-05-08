package Concurrency;

import Core.Reservation;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SynchronizationManager {

    private final ReentrantReadWriteLock rwlockCalendar
            = new ReentrantReadWriteLock(true);

    public ReentrantReadWriteLock.ReadLock lockReadCalendar() {
        return rwlockCalendar.readLock();
    }

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

    public SynchronizationManager(int maxCapacity,
            int projectorUnits, int microphoneUnits, int soundUnits) {
        this.maxCapacity       = maxCapacity;
        this.capacitySemaphore = new Semaphore(maxCapacity, true);
        this.projectorSemaphore  = new Semaphore(projectorUnits, true);
        this.microphoneSemaphore = new Semaphore(microphoneUnits, true);
        this.soundSemaphore      = new Semaphore(soundUnits, true);
        this.totalProjectors  = projectorUnits;
        this.totalMicrophones = microphoneUnits;
        this.totalSound       = soundUnits;
    }

    // --- Getters básicos ---
    public int getMaxCapacity()          { return maxCapacity; }
    public int getAvailableCapacity()    { return capacitySemaphore.availablePermits(); }
    public int getAvailableProjectors()  { return projectorSemaphore.availablePermits(); }
    public int getAvailableMicrophones() { return microphoneSemaphore.availablePermits(); }
    public int getAvailableSound()       { return soundSemaphore.availablePermits(); }
    public int getTotalProjectors()      { return totalProjectors; }
    public int getTotalMicrophones()     { return totalMicrophones; }
    public int getTotalSound()           { return totalSound; }
    public ReentrantLock getTtlMutex()   { return ttlMutex; }
    public ReentrantLock getLogMutex()   { return logMutex; }

    // --- Capacidad ---
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

    public void releaseFromReservation(int attendees,
            Reservation.Equipment equipment) {
        releaseEquipment(equipment, 1);
        if (attendees > 0) capacitySemaphore.release(attendees);
    }

    // --- API de mapa {tipo -> cantidad} ---
    public void acquireEquipmentMap(Map<Reservation.Equipment, Integer> items)
            throws InterruptedException {
        java.util.List<Map.Entry<Reservation.Equipment, Integer>> acquired
                = new java.util.ArrayList<>();
        try {
            for (Map.Entry<Reservation.Equipment, Integer> e : items.entrySet()) {
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

    public void releaseEquipmentMap(Map<Reservation.Equipment, Integer> items) {
        if (items == null) return;
        for (Map.Entry<Reservation.Equipment, Integer> e : items.entrySet()) {
            releaseEquipment(e.getKey(), e.getValue());
        }
    }

    public boolean isEquipmentMapAvailable(
            Map<Reservation.Equipment, Integer> items) {
        if (items == null || items.isEmpty()) return true;
        for (Map.Entry<Reservation.Equipment, Integer> e : items.entrySet()) {
            if (!isSingleAvailable(e.getKey(), e.getValue())) return false;
        }
        return true;
    }

    // --- API de unidad única (backward-compat) ---
    public void acquireEquipmentOnly(Reservation.Equipment equipment)
            throws InterruptedException {
        acquireEquipment(equipment, 1);
    }

    public void releaseEquipmentOnly(Reservation.Equipment equipment) {
        releaseEquipment(equipment, 1);
    }

    // --- Privados ---
    private boolean isSingleAvailable(Reservation.Equipment eq, int qty) {
        if (eq == null) return true;
        switch (eq) {
            case PROYECTOR: return projectorSemaphore.availablePermits()  >= qty;
            case MICROFONO: return microphoneSemaphore.availablePermits() >= qty;
            case SONIDO:    return soundSemaphore.availablePermits()      >= qty;
            case COMPLETO:
                return projectorSemaphore.availablePermits()  >= qty
                    && microphoneSemaphore.availablePermits() >= qty
                    && soundSemaphore.availablePermits()      >= qty;
            default: return true;
        }
    }

    private void acquireEquipment(Reservation.Equipment eq, int qty)
            throws InterruptedException {
        if (eq == null) return;
        switch (eq) {
            case PROYECTOR: projectorSemaphore.acquire(qty);  break;
            case MICROFONO: microphoneSemaphore.acquire(qty); break;
            case SONIDO:    soundSemaphore.acquire(qty);      break;
            case COMPLETO:
                projectorSemaphore.acquire(qty);
                microphoneSemaphore.acquire(qty);
                soundSemaphore.acquire(qty);
                break;
            default: break;
        }
    }

    private void releaseEquipment(Reservation.Equipment eq, int qty) {
        if (eq == null) return;
        switch (eq) {
            case PROYECTOR: projectorSemaphore.release(qty);  break;
            case MICROFONO: microphoneSemaphore.release(qty); break;
            case SONIDO:    soundSemaphore.release(qty);      break;
            case COMPLETO:
                soundSemaphore.release(qty);
                microphoneSemaphore.release(qty);
                projectorSemaphore.release(qty);
                break;
            default: break;
        }
    }
}