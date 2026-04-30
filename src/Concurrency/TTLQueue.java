package Concurrency;

import Core.Reservation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Condition;

/**
 * Thread-safe queue that manages reservations with time-to-live (TTL).
 * It ensures reservations are ordered by expiration time and provides
 * synchronization mechanisms for safe concurrent access.
 */
public class TTLQueue {

    private final List<Reservation> queue = new ArrayList<>();
    private final SynchronizationManager manager;
    private final Condition condition;

    /**
     * Creates a new TTLQueue instance using the provided synchronization 
     * manager.
     *
     * @param manager the synchronization manager responsible for thread 
     * coordination
     */
    public TTLQueue(SynchronizationManager manager) {
        this.manager = manager;
        this.condition = manager.getTtlMutex().newCondition();
    }

    /**
     * Adds a reservation to the queue and orders it by TTL.
     * Notifies waiting threads after insertion.
     *
     * @param reservation the reservation to add
     */
    public void add(Reservation reservation) {
        manager.getTtlMutex().lock();
        try {
            queue.add(reservation);
            queue.sort(Comparator.comparingLong(Reservation::getTTL));
            condition.signalAll();
        } finally {
            manager.getTtlMutex().unlock();
        }
    }

    /**
     * Removes a reservation from the queue by its identifier.
     *
     * @param reservationId the id of the reservation to remove
     * @return true if the reservation was removed, false otherwise
     */
    public boolean remove(String reservationId) {
        manager.getTtlMutex().lock();
        try {
            return queue.removeIf(r -> 
                    r.getReservationId().equals(reservationId));
        } finally {
            manager.getTtlMutex().unlock();
        }
    }

    /**
     * Waits until the next reservation TTL expires or until timeout occurs.
     *
     * @param ms maximum time to wait in milliseconds
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void awaitWithTimeout(long ms) throws InterruptedException {
        manager.getTtlMutex().lock();
        try {
            if (queue.isEmpty()) {
                condition.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } finally {
            manager.getTtlMutex().unlock();
        }
    }

    /**
     * Calculates the remaining time until the next reservation expires.
     *
     * @return milliseconds until the next TTL expiration, or default 
     * value if empty
     */
    public long millisUntilNext() {
        manager.getTtlMutex().lock();
        try {
            if (queue.isEmpty()) return 5000;
            long remaining = queue.get(0).getTTL() - System.currentTimeMillis();
            return Math.max(100, remaining);
        } finally {
            manager.getTtlMutex().unlock();
        }
    }

    /**
     * Checks whether the queue is empty.
     *
     * @return true if there are no pending reservations, false otherwise
     */
    public boolean isEmpty() {
        manager.getTtlMutex().lock();
        try {
            return queue.isEmpty();
        } finally {
            manager.getTtlMutex().unlock();
        }
    }

    /**
     * Returns a snapshot copy of the current queue.
     * The returned list is independent of the internal structure.
     *
     * @return a copy of the reservation queue
     */
    public List<Reservation> getCopy() {
        manager.getTtlMutex().lock();
        try {
            return new ArrayList<>(queue);
        } finally {
            manager.getTtlMutex().unlock();
        }
    }
}