package jchat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Condition;

public class ColaTTL {

    private final List<Reserva> cola = new ArrayList<>();
    private final GestorSincronizacion gestor;
    private final Condition condicion;

    public ColaTTL(GestorSincronizacion gestor) {
        this.gestor    = gestor;
        this.condicion = gestor.getMutexTTL().newCondition();
    }

    public void agregar(Reserva reserva) {
        gestor.getMutexTTL().lock();
        try {
            cola.add(reserva);
            cola.sort(Comparator.comparingLong(Reserva::getTTL));
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

    public List<Reserva> obtenerVencidas() {
        gestor.getMutexTTL().lock();
        try {
            List<Reserva> vencidas  = new ArrayList<>();
            List<Reserva> vigentes  = new ArrayList<>();
            for (Reserva r : cola) {
                if (r.estaVencida()) vencidas.add(r);
                else                 vigentes.add(r);
            }
            cola.clear();
            cola.addAll(vigentes);
            return vencidas;
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
        try { return cola.isEmpty(); }
        finally { gestor.getMutexTTL().unlock(); }
    }

    public List<Reserva> getCopia() {
        gestor.getMutexTTL().lock();
        try { return new ArrayList<>(cola); }
        finally { gestor.getMutexTTL().unlock(); }
    }
}