package server.business.services;

import common.concurrency.ThreadPool;
import common.concurrency.ThreadPoolImpl;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestor centralizado de notificações
 * Permite que clientes se registem para receber notificações assíncronas
 * Thread-safe usando locks e conditions
 */
public class NotificationManager {
    
    // Lock para proteger estruturas de dados
    private final ReentrantLock lock = new ReentrantLock();
    
    // Notificações de venda específica: chave = "produto1:produto2:dia"
    private final Map<String, List<NotificationHandler>> vendasEspecificas = new HashMap<>();
    
    // Notificações de vendas consecutivas: chave = "produtoID:n:dia"
    private final Map<String, List<NotificationHandler>> vendasConsecutivas = new HashMap<>();
    
    // Conditions por tipo de notificação
    private final Map<String, Condition> conditions = new HashMap<>();
    
    // Pool para processar notificações de forma assíncrona
    private final ThreadPool notificationPool;
    
    public NotificationManager() {
        this.notificationPool = new ThreadPoolImpl(10); // 10 threads para notificações
    }
    
    /**
     * Regista interesse em notificação de venda específica
     * Retorna quando AMBOS os produtos forem vendidos no dia atual
     */
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
            
            // Criar condition se não existir
            if (!conditions.containsKey(key)) {
                conditions.put(key, lock.newCondition());
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Regista interesse em notificação de vendas consecutivas
     */
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
    
    /**
     * Notifica quando um evento de venda ocorre
     * Verifica todas as condições pendentes
     */
    //todo: confirmafr estes argumentos sus
    public void notificarEvento(int produtoID, int diaAtual, Map<Integer, List<?>> eventosDia, int lastProductID, int consecutiveCount) {
        lock.lock();
        try {
            // Verificar vendas específicas
            verificarVendasEspecificas(produtoID, diaAtual, eventosDia);
            
            // Verificar vendas consecutivas
            verificarVendasConsecutivas(produtoID, lastProductID, 
                                       consecutiveCount, diaAtual);
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Limpa notificações de um dia (quando o dia avança)
     */
    public void limparNotificacoesDia(int dia) {
        lock.lock();
        try {
            // Remover todas as chaves deste dia
            vendasEspecificas.keySet().removeIf(key -> key.endsWith(":" + dia));
            vendasConsecutivas.keySet().removeIf(key -> key.endsWith(":" + dia));
            
            // Acordar todas as threads (vão receber null/timeout)
            for (Condition cond : conditions.values()) {
                cond.signalAll();
            }
            conditions.clear();
            
        } finally {
            lock.unlock();
        }
    }
    
    // === Métodos Privados ===
    
    private void verificarVendasEspecificas(int produtoID, int diaAtual, Map<Integer, List<?>> eventosDia) {
        // Procurar todas as chaves que envolvem este produto
        List<String> keysParaNotificar = new ArrayList<>();
        
        for (String key : vendasEspecificas.keySet()) {
            if (!key.endsWith(":" + diaAtual)) continue;
            
            String[] parts = key.split(":");
            int p1 = Integer.parseInt(parts[0]);
            int p2 = Integer.parseInt(parts[1]);
            
            // Se este evento completa a condição (ambos produtos vendidos)
            if ((produtoID == p1 || produtoID == p2) && 
                eventosDia.containsKey(p1) && eventosDia.containsKey(p2)) {
                keysParaNotificar.add(key);
            }
        }
        
        // Notificar handlers
        for (String key : keysParaNotificar) {
            notificarHandlers(vendasEspecificas.get(key), 
                            "Produtos vendidos no dia " + diaAtual);
            vendasEspecificas.remove(key);
        }
    }
    
    private void verificarVendasConsecutivas(int produtoID, int lastProductID, int consecutiveCount, int diaAtual) {
        if (lastProductID != produtoID) return;
        
        List<String> keysParaNotificar = new ArrayList<>();
        
        for (String key : vendasConsecutivas.keySet()) {
            if (!key.endsWith(":" + diaAtual)) continue;
            
            String[] parts = key.split(":");
            int pid = Integer.parseInt(parts[0]);
            int n = Integer.parseInt(parts[1]);
            
            // Se atingiu o número de vendas consecutivas
            if (pid == produtoID && consecutiveCount >= n) {
                keysParaNotificar.add(key);
            }
        }
        
        // Notificar handlers
        for (String key : keysParaNotificar) {
            notificarHandlers(vendasConsecutivas.get(key),
                            "Produto " + produtoID + " atingiu " + consecutiveCount + 
                            " vendas consecutivas");
            vendasConsecutivas.remove(key);
        }
    }
    
    private void notificarHandlers(List<NotificationHandler> handlers, String mensagem) {
        if (handlers == null || handlers.isEmpty()) return;
        
        for (NotificationHandler handler : handlers) {
            // Processar notificação de forma assíncrona
            notificationPool.submit(() -> {
                try {
                    handler.callback.onNotification(mensagem);
                } catch (Exception e) {
                    System.err.println("Erro ao processar callback: " + e.getMessage());
                }
            });
        }
    }
    
    private String criarChaveVendaEspecifica(int p1, int p2, int dia) {
        // Normalizar ordem para evitar duplicatas
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
    
    // === Classes Internas ===
    
    private static class NotificationHandler {
        final NotificationCallback callback;
        final int diaRegisto;
        
        NotificationHandler(NotificationCallback callback, int diaRegistro) {
            this.callback = callback;
            this.diaRegisto = diaRegistro;
        }
    }
    
    /**
     * Interface para callbacks de notificações
     */
    public interface NotificationCallback {
        void onNotification(String mensagem);
    }
}