package server.business.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import common.concurrency.ThreadPool;
import common.concurrency.ThreadPoolImpl;


public class NotificationManager {
    
    
    private final ReentrantLock lock = new ReentrantLock();
    
    
    private final Map<String, List<NotificationHandler>> vendasEspecificas = new HashMap<>();
    
    
    private final Map<String, List<NotificationHandler>> vendasConsecutivas = new HashMap<>();
    
    
    private final Map<String, Condition> conditions = new HashMap<>();
    
    
    private final ThreadPool notificationPool;
    
    public NotificationManager() {
        this.notificationPool = new ThreadPoolImpl(10); 
    }
    
    
    public void registarVendaEspecifica(int produtoID1, int produtoID2, int diaAtual, NotificationCallback callback) {
        lock.lock();
        try {
            String key = criarChaveVendaEspecifica(produtoID1, produtoID2, diaAtual);
            
            NotificationHandler handler = new NotificationHandler(callback, diaAtual);
            
            List<NotificationHandler> handlers = vendasEspecificas.get(key);
            if (handlers == null) {
                handlers = new ArrayList<>();
                vendasEspecificas.put(key, handlers);
            }
            handlers.add(handler);
            
            
            if (!conditions.containsKey(key)) {
                conditions.put(key, lock.newCondition());
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    
    public void registarVendasConsecutivas(int produtoID, int n, int diaAtual, NotificationCallback callback) {
        lock.lock();
        try {
            String key = criarChaveVendasConsecutivas(produtoID, n, diaAtual);
            
            NotificationHandler handler = new NotificationHandler(callback, diaAtual);
            
            List<NotificationHandler> handlers = vendasConsecutivas.get(key);
            if (handlers == null) {
                handlers = new ArrayList<>();
                vendasConsecutivas.put(key, handlers);
            }
            handlers.add(handler);
            
            if (!conditions.containsKey(key)) {
                conditions.put(key, lock.newCondition());
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    
    public void notificarEvento(int produtoID, int diaAtual, Map<Integer, List<?>> eventosDia, int lastProductID, int consecutiveCount) {
        lock.lock();
        try {
            
            List<String> keysVendasEspecificas = new ArrayList<>();

            for (String key : vendasEspecificas.keySet()) {
                if (!key.endsWith(":" + diaAtual)) continue;

                String[] parts = key.split(":");
                int p1 = Integer.parseInt(parts[0]);
                int p2 = Integer.parseInt(parts[1]);

                if ((produtoID == p1 || produtoID == p2) && 
                    eventosDia.containsKey(p1) && eventosDia.containsKey(p2)) {
                    keysVendasEspecificas.add(key);
                }
            }

            
            for (String key : keysVendasEspecificas) {
                List<NotificationHandler> handlers = vendasEspecificas.remove(key);
                if (handlers != null) {
                    notificarHandlersAsync(handlers, 
                        "Produtos vendidos no dia " + diaAtual);
                }
            }


            List<String> keysVendasConsecutivas = new ArrayList<>();

            for (String key : vendasConsecutivas.keySet()) {
                if (!key.endsWith(":" + diaAtual)) continue;

                String[] parts = key.split(":");
                int pid = Integer.parseInt(parts[0]);
                int n = Integer.parseInt(parts[1]);

                if (pid == produtoID && consecutiveCount >= n) {
                    keysVendasConsecutivas.add(key);
                }
            }

            
            for (String key : keysVendasConsecutivas) {
                List<NotificationHandler> handlers = vendasConsecutivas.remove(key);
                if (handlers != null) {
                    notificarHandlersAsync(handlers,
                        "Produto " + produtoID + " atingiu " + consecutiveCount + " vendas consecutivas");
                }
            }

        } finally {
            lock.unlock();
        }
    }

    private void notificarHandlersAsync(List<NotificationHandler> handlers, String mensagem) {
        for (NotificationHandler handler : handlers) {
            notificationPool.submit(() -> {
                try {
                    handler.callback.onNotification(mensagem);
                } catch (Exception e) {
                    System.err.println("Erro ao processar callback: " + e.getMessage());
                }
            });
        }
    }
    
    
    public void limparNotificacoesDia(int dia) {
        lock.lock();
        try {
            
            vendasEspecificas.keySet().removeIf(key -> key.endsWith(":" + dia));
            vendasConsecutivas.keySet().removeIf(key -> key.endsWith(":" + dia));
            
            
            for (Condition cond : conditions.values()) {
                cond.signalAll();
            }
            conditions.clear();
            
        } finally {
            lock.unlock();
        }
    }
    
    
    private String criarChaveVendaEspecifica(int p1, int p2, int dia) {
        
        int min = Math.min(p1, p2);
        int max = Math.max(p1, p2);
        return min + ":" + max + ":" + dia;
    }
    
    private String criarChaveVendasConsecutivas(int produtoID, int n, int dia) {
        return produtoID + ":" + n + ":" + dia;
    }
    
    public void shutdown() {
        notificationPool.shutdown();
    }
    
    
    
    private static class NotificationHandler {
        final NotificationCallback callback;
        final int diaRegisto;
        
        NotificationHandler(NotificationCallback callback, int diaRegistro) {
            this.callback = callback;
            this.diaRegisto = diaRegistro;
        }
    }
    
    
    public interface NotificationCallback {
        void onNotification(String mensagem);
    }
}