package Core;

import Concurrency.SynchronizationManager;

/**
 * Manages the auditorium's physical resources and seating capacity.
 * Acts as a wrapper for the SynchronizationManager to provide high-level 
 * resource status and release operations.
 */
public class AuditoriumManager {

    private final SynchronizationManager manager;

    /**
     * Constructs an AuditoriumManager with a specific synchronization manager.
     * @param manager the synchronization manager that controls resource locks
     */
    public AuditoriumManager(SynchronizationManager manager) {
        this.manager = manager;
    }

    /**
     * Releases auditorium seating capacity back to the system.
     * @param attendees the number of seats to be freed
     */
    public void releaseCapacity(int attendees) {
        manager.releaseFromReservation(attendees, 
                Reservation.Equipment.NINGUNO);
    }

    /**
     * Checks if the requested equipment is currently available.
     * Includes a check for "FULL" which requires all equipment types.
     *
     * @param equipment the equipment type to check
     * @return true if the equipment is available or not required, false 
     * otherwise
     */
    public boolean hasEquipment(Reservation.Equipment equipment) {
        switch (equipment) {
            case PROYECTOR:
                return manager.getAvailableProjectors() > 0;
            case MICROFONO:
                return manager.getAvailableMicrophones() > 0;
            case SONIDO:
                return manager.getAvailableSound() > 0;
            case COMPLETO:
                return manager.getAvailableProjectors() > 0
                        && manager.getAvailableMicrophones() > 0
                        && manager.getAvailableSound() > 0;
            case NINGUNO:
            default:
                return true;
        }
    }

    /**
     * Releases specific equipment back to the system pool.
     * @param equipment the equipment type to release
     */
    public void releaseEquipment(Reservation.Equipment equipment) {
        manager.releaseFromReservation(0, equipment);
    }

    /**
     * Returns the current number of available seats.
     * @return available seating capacity
     */
    public int getAvailableCapacity() {
        return manager.getAvailableCapacity();
    }

    /**
     * Returns the current number of available projectors.
     * @return count of available projectors
     */
    public int getAvailableProjectors() {
        return manager.getAvailableProjectors();
    }

    /**
     * Returns the current number of available microphones.
     * @return count of available microphones
     */
    public int getAvailableMicrophones() {
        return manager.getAvailableMicrophones();
    }

    /**
     * Returns the current number of available sound systems.
     * @return count of available sound systems
     */
    public int getAvailableSound() {
        return manager.getAvailableSound();
    }

    /**
     * Provides a formatted string representing the current state of all 
     * resources.
     * Note: Labels are kept in Spanish for user display consistency.
     * @return formatted resource status string
     */
    @Override
    public String toString() {
        return String.format(
                "Capacidad: %d | Proyectores: %d | Micrófonos: %d | Sonido: %d",
                getAvailableCapacity(),
                getAvailableProjectors(),
                getAvailableMicrophones(),
                getAvailableSound()
        );
    }
}