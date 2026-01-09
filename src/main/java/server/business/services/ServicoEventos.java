package server.business.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import common.ErrorLogger;
import common.dto.EventoDTO;
import common.dto.EventosFiltradosDTO;
import common.dto.FiltrarEventosDTO;
import common.dto.NotificacaoDTO;
import common.dto.RespostaDTO;
import common.exceptions.EventoException;
import common.interfaces.IServicoEventos;
import server.business.domain.Evento;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;

/**
 * Serviço de gestão de eventos com sistema de notificações assíncronas
 */
public class ServicoEventos implements IServicoEventos {
    private final IEventoRepository eventoRepository;
    private final CacheManager cacheManager;
    private final NotificationManager notificationManager;
    private final int D;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int diaAtual;
    private final Map<Integer, List<Evento>> eventosDiaAtual = new HashMap<>();

    // Estado para vendas consecutivas
    private int lastProductID = -1;
    private int consecutiveCount = 0;

    private final ReentrantLock esperaLock = new ReentrantLock();
    private final List<EsperaHandle> threadsEmEspera = new ArrayList<>();
    private boolean shuttingDown = false;

    public ServicoEventos(IEventoRepository eventoRepository, CacheManager cacheManager, int D) {
        this.eventoRepository = eventoRepository;
        this.cacheManager = cacheManager;
        this.notificationManager = new NotificationManager();
        this.D = D;
        this.diaAtual = eventoRepository.obterUltimoDia() + 1;

        System.out.println("ServicoEventos iniciado no dia: " + diaAtual + " (Janela D=" + D + ")");
    }

    @Override
    public RespostaDTO registrarEvento(EventoDTO dto) throws EventoException {
        validarEvento(dto);

        Evento evento = new Evento(dto.getProdutoID(), dto.getQuantidade(), dto.getPreco());

        lock.writeLock().lock();
        try {
            // Adicionar evento ao dia atual
            List<Evento> lista = eventosDiaAtual.computeIfAbsent(
                dto.getProdutoID(), k -> new ArrayList<>()
            );
            lista.add(evento);

            // Atualizar contadores de vendas consecutivas
            if (lastProductID == dto.getProdutoID()) {
                consecutiveCount++;
            } else {
                lastProductID = dto.getProdutoID();
                consecutiveCount = 1;
            }

            // Notificar sistema de notificações
            Map<Integer, List<?>> eventosDiaGenerico = new HashMap<>(eventosDiaAtual);
            notificationManager.notificarEvento(
                dto.getProdutoID(),
                diaAtual,
                eventosDiaGenerico,
                lastProductID,
                consecutiveCount
            );

        } finally {
            lock.writeLock().unlock();
        }

        System.out.println(String.format(
            "Evento registrado: Produto=%d, Qtd=%d, Preço=%.2f (Dia %d)",
            dto.getProdutoID(), dto.getQuantidade(), dto.getPreco(), diaAtual
        ));

        return RespostaDTO.sucesso("Evento registado com sucesso");
    }

    @Override
    public RespostaDTO notificarVendaEspecifica(NotificacaoDTO notificacao) throws EventoException {
        int produtoID1 = notificacao.getArg1();
        int produtoID2 = notificacao.getArg2();

        if (isShuttingDown()) {
            return RespostaDTO.erro("Servidor a encerrar");
        }

        lock.readLock().lock();
        int diaSnapshot = diaAtual;

        // Verificar se já foi satisfeita
        boolean jaSatisfeita = eventosDiaAtual.containsKey(produtoID1) &&
                               eventosDiaAtual.containsKey(produtoID2);
        lock.readLock().unlock();

        if (jaSatisfeita) {
            return RespostaDTO.sucesso("Produtos já vendidos no dia atual!");
        }

        try {
            final ReentrantLock waitLock = new ReentrantLock();
            final Condition done = waitLock.newCondition();
            final boolean[] notified = {false};
            final String[] message = {null};
            final EsperaHandle handle = new EsperaHandle(waitLock, done);

            NotificationManager.NotificationCallback callback = msg -> {
                waitLock.lock();
                try {
                    notified[0] = true;
                    message[0] = msg;
                    done.signal(); // equivalente a notify()
                } finally {
                    waitLock.unlock();
                }
            };

            notificationManager.registarVendaEspecifica(
                produtoID1,
                produtoID2,
                diaSnapshot,
                callback
            );

            registerWaiter(handle);

            // Aguardar notificação (sem synchronized/wait/notify)
            waitLock.lock();
            try {
                while (!notified[0]) {
                    if (isShuttingDown()) {
                        return RespostaDTO.erro("Servidor a encerrar");
                    }

                    // Verificar se dia mudou
                    lock.readLock().lock();
                    try {
                        if (diaAtual != diaSnapshot) {
                            return RespostaDTO.erro("Dia avançou, notificação cancelada");
                        }
                    } finally {
                        lock.readLock().unlock();
                    }

                    done.await();
                }

                return RespostaDTO.sucesso(message[0]);
            } finally {
                waitLock.unlock();
                unregisterWaiter(handle);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoException("Espera interrompida", e);
        }
    }

    @Override
    public RespostaDTO notificarVendasConsecutivas(NotificacaoDTO notificacao) throws EventoException {
        int produtoID = notificacao.getArg1();
        int n = notificacao.getArg2();

        if (isShuttingDown()) {
            return RespostaDTO.erro("Servidor a encerrar");
        }

        lock.readLock().lock();
        int diaSnapshot = diaAtual;
        boolean jaAtingiu = lastProductID == produtoID && consecutiveCount >= n;
        lock.readLock().unlock();

        if (jaAtingiu) {
            return RespostaDTO.sucesso("Produto já atingiu " + n + " vendas consecutivas!");
        }

        try {
            final ReentrantLock waitLock = new ReentrantLock();
            final Condition done = waitLock.newCondition();
            final boolean[] notified = {false};
            final String[] message = {null};
            final EsperaHandle handle = new EsperaHandle(waitLock, done);

            NotificationManager.NotificationCallback callback = msg -> {
                waitLock.lock();
                try {
                    notified[0] = true;
                    message[0] = msg;
                    done.signal();
                } finally {
                    waitLock.unlock();
                }
            };

            notificationManager.registarVendasConsecutivas(
                produtoID,
                n,
                diaSnapshot,
                callback
            );

            registerWaiter(handle);

            // Aguardar notificação (sem synchronized/wait/notify)
            waitLock.lock();
            try {
                while (!notified[0]) {
                    if (isShuttingDown()) {
                        return RespostaDTO.erro("Servidor a encerrar");
                    }

                    // cancelar se o dia mudou
                    lock.readLock().lock();
                    try {
                        if (diaAtual != diaSnapshot) {
                            return RespostaDTO.erro("Dia avançou, notificação cancelada");
                        }
                    } finally {
                        lock.readLock().unlock();
                    }

                    done.await();
                }

                return RespostaDTO.sucesso(message[0]);
            } finally {
                waitLock.unlock();
                unregisterWaiter(handle);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoException("Espera interrompida", e);
        }
    }

    @Override
    public RespostaDTO filtrarEventos(FiltrarEventosDTO filtro) throws EventoException {
        if (filtro == null) {
            throw new EventoException("Filtro não fornecido");
        }

        Set<Integer> produtosIDs = filtro.getProdutosIDs();
        int diaAnterior = filtro.getDiaAnterior();

        // Validar
        if (produtosIDs == null || produtosIDs.isEmpty()) {
            throw new EventoException("Conjunto de produtos vazio");
        }
        if (diaAnterior < 1 || diaAnterior > D) {
            throw new EventoException("Dia anterior inválido (deve estar entre 1 e " + D + ")");
        }

        lock.readLock().lock();
        int diaAlvo = diaAtual - diaAnterior;
        lock.readLock().unlock();

        if (diaAlvo < 0) {
            throw new EventoException("Dia anterior excede histórico disponível");
        }

        // Carregar eventos do dia
        Map<Integer, List<Evento>> eventosDia = eventoRepository.carregarEventosDia(diaAlvo);

        // Filtrar apenas produtos do conjunto
        Map<Integer, List<EventosFiltradosDTO.EventoCompacto>> resultado = new HashMap<>();

        for (int produtoID : produtosIDs) {
            List<Evento> eventos = eventosDia.get(produtoID);
            if (eventos != null && !eventos.isEmpty()) {
                List<EventosFiltradosDTO.EventoCompacto> compactos = new ArrayList<>();
                for (Evento e : eventos) {
                    compactos.add(new EventosFiltradosDTO.EventoCompacto(
                        e.getQuantidade(),
                        e.getPreco()
                    ));
                }
                resultado.put(produtoID, compactos);
            }
        }

        EventosFiltradosDTO resposta = new EventosFiltradosDTO(resultado, diaAlvo);

        return RespostaDTO.sucesso("Eventos filtrados do dia " + diaAlvo + ": " + resposta.toString());
    }

    @Override
    public RespostaDTO listarEventosDiaAtual() throws EventoException {
        lock.readLock().lock();
        try {
            if (eventosDiaAtual.isEmpty()) {
                return RespostaDTO.sucesso(
                    "=== EVENTOS DO DIA " + diaAtual + " ===\n\n(sem eventos)\n"
                );
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== EVENTOS DO DIA ").append(diaAtual).append(" ===\n\n");

            int totalEventos = 0;

            for (var entry : eventosDiaAtual.entrySet()) {
                sb.append("Produto ").append(entry.getKey())
                  .append(" (").append(entry.getValue().size()).append(" eventos):\n");

                int i = 1;
                for (Evento e : entry.getValue()) {
                    sb.append(String.format(
                        "  %d. Qtd: %d - Preço: %.2f€ (Volume: %.2f€)\n",
                        i++, e.getQuantidade(), e.getPreco(), e.getVolume()
                    ));
                    totalEventos++;
                }
                sb.append("\n");
            }

            sb.append("Total: ").append(totalEventos).append(" eventos\n");

            return RespostaDTO.sucesso(sb.toString());

        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public RespostaDTO novoDia() throws EventoException {
        lock.writeLock().lock();
        try {
            int diaAnterior = diaAtual;
            Map<Integer, List<Evento>> backup = new HashMap<>();
            for (Map.Entry<Integer, List<Evento>> entry : eventosDiaAtual.entrySet()) {
                backup.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            try {
                if (!eventosDiaAtual.isEmpty()) {
                    eventoRepository.salvarEventosDia(diaAnterior, eventosDiaAtual);
                    System.out.println("Eventos do dia " + diaAnterior + " persistidos");
                }

                diaAtual++;

                notificationManager.limparNotificacoesDia(diaAnterior);
                signalAllWaiters();

                if (cacheManager != null) {
                    int diaForaDaJanela = diaAtual - D - 1;
                    if (diaForaDaJanela >= 0) {
                        cacheManager.limparAgregacoesDia(diaForaDaJanela);
                        cacheManager.removerSerieDaMemoria(diaForaDaJanela);
                        System.out.println("Dia " + diaForaDaJanela + " saiu da janela");
                    }
                }

                eventosDiaAtual.clear();
                lastProductID = -1;
                consecutiveCount = 0;

                System.out.println("========================================");
                System.out.println("Novo dia iniciado: " + diaAtual);
                System.out.println("========================================");

                return RespostaDTO.sucesso("Novo dia iniciado: " + diaAtual);

            } catch (Exception e) {
                diaAtual = diaAnterior;
                eventosDiaAtual.clear();
                eventosDiaAtual.putAll(backup);

                ErrorLogger.getInstance().logError(
                    "ServicoEventos.novoDia[diaAnterior=" + diaAnterior + "]", e);

                throw new EventoException("Erro ao avançar dia: " + e.getMessage(), e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getDiaAtual() {
        lock.readLock().lock();
        try {
            return diaAtual;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void shutdown() {
        esperaLock.lock();
        try {
            shuttingDown = true;
        } finally {
            esperaLock.unlock();
        }
        signalAllWaiters();
        notificationManager.shutdown();
    }

    private void validarEvento(EventoDTO dto) throws EventoException {
        if (dto == null) {
            throw new EventoException("Dados do evento não fornecidos");
        }
        if (dto.getProdutoID() <= 0) {
            throw new EventoException("ID de produto inválido");
        }
        if (dto.getQuantidade() <= 0) {
            throw new EventoException("Quantidade deve ser positiva");
        }
        if (dto.getPreco() <= 0) {
            throw new EventoException("Preço deve ser positivo");
        }
    }

    private boolean isShuttingDown() {
        esperaLock.lock();
        try {
            return shuttingDown;
        } finally {
            esperaLock.unlock();
        }
    }

    private void registerWaiter(EsperaHandle handle) {
        esperaLock.lock();
        try {
            threadsEmEspera.add(handle);
        } finally {
            esperaLock.unlock();
        }
    }

    private void unregisterWaiter(EsperaHandle handle) {
        esperaLock.lock();
        try {
            threadsEmEspera.remove(handle);
        } finally {
            esperaLock.unlock();
        }
    }

    private void signalAllWaiters() {
        List<EsperaHandle> snapshot;
        esperaLock.lock();
        try {
            snapshot = new ArrayList<>(threadsEmEspera);
        } finally {
            esperaLock.unlock();
        }

        for (EsperaHandle handle : snapshot) {
            handle.lock.lock();
            try {
                handle.condition.signalAll();
            } finally {
                handle.lock.unlock();
            }
        }
    }

    private static class EsperaHandle {
        final ReentrantLock lock;
        final Condition condition;

        EsperaHandle(ReentrantLock lock, Condition condition) {
            this.lock = lock;
            this.condition = condition;
        }
    }
}
