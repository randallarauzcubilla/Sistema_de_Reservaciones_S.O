package Concurrency;

import Core.Reservation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Condition;

public class TTLQueue {

    private final List<Reservation> cola = new ArrayList<>();
    private final SynchronizationManager gestor;
    private final Condition condicion;

    public TTLQueue(SynchronizationManager gestor) {
        this.gestor = gestor;
        this.condicion = gestor.getMutexTTL().newCondition();
    }

    public void agregar(Reservation reserva) {
        gestor.getMutexTTL().lock();
        try {
            cola.add(reserva);
            cola.sort(Comparator.comparingLong(Reservation::getTTL));
            condicion.signalAll();
        } finally {
            gestor.getMutexTTL().unlock();
        }
    }

    public boolean remover(String idReserva) {
        gestor.getMutexTTL().lock();
        try {
            return cola.removeIf(r -> r.getIdReserva().equals(idReserva));
        } finally {
            gestor.getMutexTTL().unlock();
        }
    }

    public void esperarConTimeout(long ms) throws InterruptedException {
        gestor.getMutexTTL().lock();
        try {
            if (cola.isEmpty()) {
                condicion.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } finally {
            gestor.getMutexTTL().unlock();
        }
    }

    public long msHastaProxima() {
        gestor.getMutexTTL().lock();
        try {
            if (cola.isEmpty()) return 5000;
            long restante = cola.get(0).getTTL() - System.currentTimeMillis();
            return Math.max(100, restante);
        } finally {
            gestor.getMutexTTL().unlock();
        }
    }

    public boolean isEmpty() {
        gestor.getMutexTTL().lock();
        try {
            return cola.isEmpty();
        } finally {
            gestor.getMutexTTL().unlock();
        }
    }

    public List<Reservation> getCopia() {
        gestor.getMutexTTL().lock();
        try {
            return new ArrayList<>(cola);
        } finally {
            gestor.getMutexTTL().unlock();
        }
    }
}