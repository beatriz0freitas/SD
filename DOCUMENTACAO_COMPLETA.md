# DOCUMENTAÇÃO COMPLETA DO PROJETO SD - Gestão de Eventos de Vendas

## Índice

1. [Visão Geral da Arquitetura](#visão-geral)
2. [Fluxo de Threads e Conexão](#fluxo-threads)
3. [Módulo Client](#módulo-client)
4. [Módulo Middleware](#módulo-middleware)
5. [Módulo Server](#módulo-server)
6. [Módulo Common](#módulo-common)
7. [Fluxo Detalhado de Operações](#fluxo-operações)

---

## Visão Geral da Arquitetura

O projeto implementa um sistema distribuído cliente-servidor para gestão de eventos de vendas. A arquitetura está dividida em camadas:

- **Client**: Interface do utilizador + Middleware para comunicação remota
- **Middleware**: Protocolo de serialização/deserialização + Estruturas de mensagens
- **Server**: Lógica de negócio, persistência, cache e gestão de clientes
- **Common**: Utilitários, DTOs, exceções e interfaces compartilhadas

### Características principais:

- **Concorrência**: Pools de threads customizados, locks para segurança
- **Persistência**: Repositórios file-based com locking
- **Cache**: Manager com LRU e cálculo otimizado
- **Performance**: Métricas globais (latência, throughput, cache hits/misses)
- **Monitoramento**: Detecção de deadlocks e logging de erros

---

## Fluxo de Threads e Conexão

### 1. Lado Cliente: Conexão e Envio de Mensagens

#### Cliente.java (main)

```java
// Ponto de entrada do cliente
// Argumentos: host porta [pool maxConnections]
public static void main(String[] args) {
    String host = args.length > 0 ? args[0] : "localhost";        // host padrão
    int porta = args.length > 1 ? parsePorta(args[1]) : 5001;     // porta padrão
    boolean usePool = args.length > 2 && "pool".equals(args[2]);   // usar pool?
    int maxConnections = args.length > 3 ? Integer.parseInt(args[3]) : 5;

    // Cria middleware que abstrai a comunicação remota
    ClienteMiddleware middleware = new ClienteMiddleware(host, porta, usePool, maxConnections);

    // Cria fábrica de stubs e interface de utilizador
    StubFactory stubFactory = new StubFactory(middleware);
    InterfaceUtilizador ui = new InterfaceUtilizador(middleware, stubFactory);

    try {
        middleware.conectar();  // Abre conexão ao servidor
        ui.iniciar();           // Inicia loop interativo
    } finally {
        middleware.desconectar(); // Fecha conexão
    }
}
```

#### ClienteMiddleware.java (Comunicação Remota)

```java
// Responsável por:
// 1. Gerir a conexão (dedicada ou pool)
// 2. Enviar requests ao servidor
// 3. Aguardar respostas via demultiplexer
// 4. Garantir thread-safety em escrita

public class ClienteMiddleware {
    // Locks para segurança concorrente
    private final ReentrantLock lockEscrita = new ReentrantLock();      // Protege escrita
    private final ReentrantLock lockTags = new ReentrantLock();         // Protege gerador de tags
    private long contadorPedidos = 0;                                   // Tag global

    // Conexões
    private ConnectionPool connectionPool;           // Para múltiplas conexões
    private PooledConnection dedicatedConnection;    // Para conexão única

    // Demultiplexer para respostas assíncronas
    private Demultiplexer demux;                     // Aguarda respostas
    private Thread threadDemux;                      // Thread do demultiplexer

    /**
     * Conectar ao servidor
     * - Se usePool: cria pool de conexões
     * - Senão: cria conexão dedicada + inicia demultiplexer
     */
    public void conectar() throws IOException {
        lockEscrita.lock();
        try {
            if (!usePool) {
                dedicatedConnection = new PooledConnection(host, porta, null);
                DataInputStream entrada = dedicatedConnection.getInputStream();

                // Demultiplexer lê respostas em thread separada
                demux = new Demultiplexer(entrada, shutdownHandler);
                threadDemux = new Thread(demux, "Demux-" + host + ":" + porta);
                threadDemux.start();  // Começa a ler respostas
            }
            setConectado(true);
        } finally {
            lockEscrita.unlock();
        }
    }

    /**
     * Enviar pedido e aguardar resposta
     * 1. Gera tag única (contador++, thread-safe)
     * 2. Serializa parametros em DTO
     * 3. Cria Message com tag, serviço, método, payload
     * 4. Envia ao servidor
     * 5. Demultiplexer aguarda resposta com mesmo tag
     */
    public void enviar(byte serviceId, byte methodId, Object parametros) throws Exception {
        long tag = nextTag();  // Tag única: ++contadorPedidos

        byte[] payload = null;
        if (parametros != null) {
            // Serializa DTO em bytes
            payload = ((Serializable) parametros).serialize();
        }

        Message msg = Message.request(tag, serviceId, methodId, payload);

        lockEscrita.lock();
        try {
            protocolo.enviar(msg, out);  // Envia ao servidor
        } finally {
            lockEscrita.unlock();
        }

        // Aguarda resposta com mesmo tag (demux.aguardar(tag))
        byte[] resposta = demux.aguardar(tag);
        return RespostaDTO.deserialize(resposta);
    }
}
```

#### Demultiplexer.java (Receber Respostas)

```java
// Thread que constantemente lê respostas do servidor
// Usa mapa [tag] -> [resposta] para correlacionar com requests
// Threads clientes usam Condition para aguardar

public class Demultiplexer implements Runnable {
    private final DataInputStream entrada;         // Lê do socket
    private final Map<Long, byte[]> respostas;     // Cache de respostas por tag
    private final Map<Long, Condition> threadsEspera; // Conditions por tag

    @Override
    public void run() {
        try {
            while (shouldRun()) {
                Message msg = protocolo.receber(entrada);  // Bloqueia até receber msg

                if (msg.getTag() == -1) {
                    // Tag -1 = servidor a encerrar
                    setServerShutdown(true);
                    acordarTodasThreads();
                    break;
                }

                // Processa resposta
                entregarResposta(msg.getTag(), msg.getPayload());
            }
        } catch (IOException e) {
            // Conexão fechada
            tratarErroConexao(e);
        }
    }

    /**
     * Thread cliente chama isto para aguardar resposta
     */
    public byte[] aguardar(long tag) throws Exception {
        lock.lock();
        try {
            // Se já temos resposta, devolve
            if (respostas.containsKey(tag)) {
                return respostas.remove(tag);
            }

            // Senão, cria Condition e aguarda
            Condition condicao = lock.newCondition();
            threadsEspera.put(tag, condicao);

            // Aguarda notificação (quando resposta chegar)
            while (!respostas.containsKey(tag)) {
                condicao.await();  // Bloqueia até notificação
            }

            return respostas.remove(tag);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Chamado quando resposta chega
     */
    private void entregarResposta(long tag, byte[] payload) {
        lock.lock();
        try {
            respostas.put(tag, payload);
            Condition cond = threadsEspera.get(tag);
            if (cond != null) {
                cond.signal();  // Acorda thread que aguarda
            }
        } finally {
            lock.unlock();
        }
    }
}
```

---

### 2. Lado Servidor: Aceitação de Clientes e Processamento

#### Server.java (main do servidor)

```java
// Responsável por:
// 1. Criar ServerSocket na porta
// 2. Aceitar clientes em loop
// 3. Criar ClientHandler para cada cliente (thread pool)
// 4. Gerir ciclo de vida

public class Server {
    private ServerSocket serverSocket;

    // Pools de threads
    private final ThreadPool clientHandlerPool;  // Para ClientHandlers
    private final ThreadPool requestPool;         // Para processar requests

    // Segurança concorrente
    private final Set<Socket> clientesAtivos;    // Clientes conectados
    private final ReentrantLock clientesLock;    // Protege conjunto de clientes

    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);
            setAtivo(true);

            while (isAtivo()) {
                Socket clientSocket = serverSocket.accept();  // Aguarda cliente

                adicionarCliente(clientSocket);  // Adiciona à lista (thread-safe)

                // Cria handler para cliente
                ClientHandler handler = new ClientHandler(
                    clientSocket,
                    dispatcher,
                    requestPool,
                    this::removerCliente
                );

                // Submete ao pool
                if (!clientHandlerPool.submit(handler)) {
                    // Pool cheio
                    try { clientSocket.close(); } catch (IOException ignored) {}
                    removerCliente(clientSocket);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
}
```

#### ThreadPoolImpl.java (Pool de Threads Customizado)

```java
// Implementação manual de pool de threads
// Razão: controlo fino sobre comportamento, sem dependências

public class ThreadPoolImpl implements ThreadPool {
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();     // Sinal: fila não vazia
    private final Condition termination = lock.newCondition();  // Sinal: todas threads terminadas

    private final Queue<Runnable> taskQueue = new ArrayDeque<>();  // Fila de tarefas
    private final Set<Thread> workers = new HashSet<>();           // Workers ativas

    private final int maxThreads;        // Máximo de threads
    private final int maxQueueSize;      // Tamanho máximo da fila

    private int workerCount = 0;         // Threads ativas
    private boolean shutdown = false;    // Modo shutdown normal
    private boolean shutdownNow = false; // Shutdown forçado

    /**
     * Submeter tarefa
     * - Se fila cheia: rejeita
     * - Se menos de maxThreads: cria nova thread
     * - Notifica threads ociosas
     */
    @Override
    public boolean submit(Runnable task) {
        lock.lock();
        try {
            if (shutdown || shutdownNow) return false;
            if (taskQueue.size() >= maxQueueSize) return false;  // Rejeita se cheio

            taskQueue.add(task);

            if (workerCount < maxThreads) {
                startWorker();  // Cria nova thread se necessário
            }

            notEmpty.signal();  // Acorda thread ocioso
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loop de worker
     * - Aguarda tarefas
     * - Executa tarefas
     * - Termina se shutdown
     */
    private void runWorkerLoop() {
        try {
            while (true) {
                Runnable task;

                lock.lock();
                try {
                    // Aguarda tarefa
                    while (taskQueue.isEmpty() && !shutdown) {
                        notEmpty.await();  // Bloqueia até notEmpty
                    }

                    if (shutdownNow) return;           // Shutdown forçado
                    if (taskQueue.isEmpty()) return;   // Shutdown normal, sem tarefas

                    task = taskQueue.poll();
                } finally {
                    lock.unlock();
                }

                // Executa tarefa (sem lock, para concorrência)
                try {
                    task.run();
                } catch (Exception e) {
                    // Log erro, continua
                }
            }
        } finally {
            finalizarWorker();  // Remove thread do conjunto
        }
    }
}
```

#### ClientHandler.java (Trata Cada Cliente)

```java
// Uma instância por cliente conectado, rodada numa thread do pool
// Responsabilidades:
// 1. Ler requests do cliente
// 2. Validar autenticação
// 3. Submeter ao pool de requests
// 4. Responder ao cliente
// 5. Tratar desconexões

public class ClientHandler implements Runnable {
    private final Socket socket;                     // Socket do cliente
    private final RequestDispatcher dispatcher;       // Para processar requests
    private final ThreadPool requestExecutor;         // Pool para executar requests

    private final Lock writeLock;                     // Protege escrita ao socket
    private final Lock authLock;                      // Protege estado de autenticação
    private boolean autenticado = false;              // Estado: autenticado?

    @Override
    public void run() {
        System.out.println("Cliente conectado: " + socket.getInetAddress());

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(...));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(...))) {

            while (true) {
                // 1. Lê request do cliente (bloqueia até chegar)
                Message msg = proto.receber(in);

                if (!msg.isRequest()) {
                    continue;  // Ignora responses
                }

                // 2. Submete processamento ao pool
                if (!requestExecutor.submit(new RequestProcessor(msg, out))) {
                    enviarErro(msg.getTag(), "Fila cheia", out);
                }
            }
        } catch (EOFException e) {
            // Cliente desconectou normalmente
            System.out.println("Cliente desconectado: " + socket.getInetAddress());
        } catch (IOException e) {
            // Erro de I/O
        } finally {
            encerrar();  // Cleanup
        }
    }

    /**
     * Processador de request (executado em thread do pool)
     */
    private class RequestProcessor implements Runnable {
        @Override
        public void run() {
            try {
                // 1. Verifica autenticação (exceto para login)
                if (!isAutenticado() && msg.getServiceId() != SERVICO_AUTENTICACAO) {
                    enviarErro(msg.getTag(), "Não autenticado", out);
                    return;
                }

                // 2. Despacha para skeleton do serviço
                RespostaDTO resp = dispatcher.despachar(msg);

                // 3. Atualiza estado de autenticação se login bem-sucedido
                if (msg.getServiceId() == SERVICO_AUTENTICACAO && resp.isSucesso()) {
                    setAutenticado(true);
                }

                // 4. Envia resposta (com lock para evitar entrelançamento)
                Message response = Message.response(msg.getTag(), resp.serialize());
                enviarResposta(response, out);

            } catch (Exception e) {
                enviarErro(msg.getTag(), e.getMessage(), out);
            }
        }
    }

    /**
     * Enviar resposta (thread-safe)
     */
    private void enviarResposta(Message msg, DataOutputStream out) throws IOException {
        writeLock.lock();
        try {
            proto.enviar(msg, out);
        } finally {
            writeLock.unlock();
        }
    }
}
```

---

### 3. Processamento de Requests: Dispatcher e Skeletons

#### RequestDispatcher.java

```java
// Mapeia requests para skeletons
// - Recebe Message com serviceId + methodId + payload
// - Identifica skeleton
// - Descodifica parametros
// - Invoca skeleton
// - Regista métricas

public class RequestDispatcher {
    private final Map<Byte, ISkeleton> skeletonsPorServico;
    private final PerformanceMetrics metrics;

    /**
     * Cria dispatcher com todos os serviços e cache
     */
    public static RequestDispatcher criar(int D, int S) {
        IEventoRepository eventoRepository = RepositoryFactory.getInstance().getEventoRepository();
        CacheManager cacheManager = new CacheManager(eventoRepository, S);

        // Cria serviços com injeção de dependências
        ServicoAutenticacao servicoAuth = new ServicoAutenticacao();
        ServicoEventos servicoEventos = new ServicoEventos(eventoRepository, cacheManager, D);
        ServicoAgregacoes servicoAgregacoes = new ServicoAgregacoes(cacheManager, servicoEventos, eventoRepository, D);
        ServicoAdmin servicoAdmin = new ServicoAdmin();

        // Cria skeletons (adaptadores para cada serviço)
        Map<Byte, ISkeleton> skeletons = Map.of(
            SERVICO_AUTENTICACAO, new ServicoAutenticacaoSkeleton(servicoAuth),
            SERVICO_EVENTOS, new ServicoEventosSkeleton(servicoEventos),
            SERVICO_AGREGACOES, new ServicoAgregacoesSkeleton(servicoAgregacoes),
            SERVICO_ADMIN, new ServicoAdminSkeleton(servicoAdmin)
        );

        return new RequestDispatcher(skeletons, servicoEventos);
    }

    /**
     * Despacha request para skeleton
     * - Tempo: inicio = System.nanoTime()
     * - Descodifica parametros do payload
     * - Invoca skeleton.processarRequisicao()
     * - Regista métrica (sucesso, latência)
     */
    public RespostaDTO despachar(Message msg) {
        long inicio = System.nanoTime();
        boolean sucesso = false;

        try {
            ISkeleton skeleton = skeletonsPorServico.get(msg.getServiceId());
            if (skeleton == null) {
                return RespostaDTO.erro("Serviço desconhecido");
            }

            // Descodifica parametros do payload bytes
            Object parametros = decodeParametros(msg.getServiceId(), msg.getMethodId(), msg.getPayload());

            // Invoca skeleton
            RespostaDTO resposta = skeleton.processarRequisicao(msg.getMethodId(), parametros);
            sucesso = resposta.isSucesso();
            return resposta;

        } finally {
            long latencia = System.nanoTime() - inicio;
            metrics.recordRequest(sucesso, latencia);  // Regista métrica
        }
    }
}
```

---

### 4. Serviços: Lógica de Negócio

#### ServicoEventos.java

```java
// Gestão de eventos de vendas
// - Registar evento
// - Listar eventos de um dia
// - Filtrar eventos
// - Novo dia (muda diaAtual)
// - Notificações de vendas

public class ServicoEventos implements IServicoEventos {
    private final IEventoRepository eventoRepository;  // Persistência
    private final CacheManager cacheManager;           // Cache
    private final NotificationManager notificationManager;

    // Dia atual em memória
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int diaAtual;
    private final Map<Integer, List<Evento>> eventosDiaAtual = new HashMap<>();  // eventos em memória

    /**
     * Registar evento (adicionado ao dia atual em memória)
     */
    @Override
    public RespostaDTO registrarEvento(EventoDTO dto) throws EventoException {
        validarEvento(dto);

        Evento evento = new Evento(dto.getProdutoID(), dto.getQuantidade(), dto.getPreco());

        lock.writeLock().lock();
        try {
            // Adiciona evento à lista do produto (dia atual)
            List<Evento> lista = eventosDiaAtual.computeIfAbsent(
                dto.getProdutoID(), k -> new ArrayList<>()
            );
            lista.add(evento);

            // Verifica notificações
            ...

            return RespostaDTO.sucesso("Evento registado");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Novo dia (persiste dia atual, começa novo)
     */
    @Override
    public RespostaDTO novoDia() {
        lock.writeLock().lock();
        try {
            // Persiste eventos do dia atual ao ficheiro
            eventoRepository.salvarEventosDia(diaAtual, eventosDiaAtual);

            // Limpa memória
            eventosDiaAtual.clear();

            // Próximo dia
            diaAtual++;

            // Invalida cache
            cacheManager.invalidarCache();

            return RespostaDTO.sucesso("Novo dia iniciado");
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

#### ServicoAgregacoes.java

```java
// Calcula agregações (somas, médias)
// - Quantidade de vendas
// - Volume de vendas
// - Preço médio
// - Preço máximo

public class ServicoAgregacoes implements IServicoAgregacoes {
    private final CacheManager cacheManager;
    private final IEventoRepository eventoRepository;

    /**
     * Obter quantidade de vendas (últimos N dias)
     * 1. Calcula intervalo de dias
     * 2. Para cada dia: tenta cache, se não existe, calcula
     * 3. Soma resultados
     */
    @Override
    public RespostaDTO obterQuantidadeVendas(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        return RespostaDTO.sucesso("Quantidade: " + agregacao.getQuantidadeVendas());
    }

    private Agregacao calcularAgregacao(int produtoID, int dias) {
        int ultimoDia = eventoRepository.obterUltimoDia();
        if (ultimoDia < 0) return new Agregacao();  // Nenhum dia

        int diasReais = Math.min(dias, ultimoDia + 1);
        int diaInicio = ultimoDia - diasReais + 1;
        int diaFim = ultimoDia;

        Agregacao resultado = new Agregacao();

        // Para cada dia no intervalo
        for (int dia = diaInicio; dia <= diaFim; dia++) {
            // Tenta obter do cache (ou calcula se não existe)
            Agregacao cached = cacheManager.obterAgregacaoDia(produtoID, dia);
            resultado.acumular(cached);  // Soma
        }

        return resultado;
    }
}
```

---

### 5. Persistência e Cache

#### EventoFileRepository.java

```java
// Persistência de eventos em ficheiros binários
// Ficheiros: dados/eventos/eventos_dia_N.dat
// Formato: [numProdutos][produtoID][numEventos][quantidade:int][preco:double]...

public class EventoFileRepository implements IEventoRepository {
    private final String pastaBase;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();  // Leitura/escrita concorrente

    /**
     * Salvar eventos de um dia (mapa produto -> lista eventos)
     * Formato binário ordenado por produtoID (para busca binária)
     */
    @Override
    public void salvarEventosDia(int dia, Map<Integer, List<Evento>> eventosPorProduto) {
        writeLock.lock();
        try {
            File ficheiro = ficheiroDia(dia);

            // Ordena produtos para busca eficiente
            List<Integer> produtosOrdenados = new ArrayList<>(eventosPorProduto.keySet());
            Collections.sort(produtosOrdenados);

            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(...))) {
                out.writeInt(produtosOrdenados.size());  // Número de produtos

                for (int produtoID : produtosOrdenados) {
                    List<Evento> eventos = eventosPorProduto.get(produtoID);

                    out.writeInt(produtoID);             // ID do produto
                    out.writeInt(eventos.size());        // Número de eventos

                    for (Evento e : eventos) {
                        out.writeInt(e.getQuantidade()); // Quantidade
                        out.writeDouble(e.getPreco());   // Preço
                    }
                }
                out.flush();
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Agregar eventos de um produto num dia (SEM ler todo o ficheiro)
     * Usa RandomAccessFile para skip eficiente
     */
    @Override
    public Agregacao agregarEventosDia(int produtoID, int dia) {
        readLock.lock();
        try {
            try (RandomAccessFile raf = new RandomAccessFile(ficheiroDia(dia), "r")) {
                int numProdutos = raf.readInt();

                // Busca binária pelo produtoID
                for (int i = 0; i < numProdutos; i++) {
                    int pid = raf.readInt();
                    int numEventos = raf.readInt();

                    if (pid == produtoID) {
                        // Encontrado! Lê eventos
                        Agregacao agregacao = new Agregacao();
                        for (int j = 0; j < numEventos; j++) {
                            int quantidade = raf.readInt();
                            double preco = raf.readDouble();
                            agregacao.update(quantidade, preco);
                        }
                        agregacao.updatePrecoMedio();
                        return agregacao;

                    } else if (pid < produtoID) {
                        // Ainda não chegou, skip eventos
                        raf.skipBytes(numEventos * 12);  // 12 bytes por evento

                    } else {
                        // Passou, produto não existe
                        break;
                    }
                }
            }

            return new Agregacao();  // Produto não existe
        } finally {
            readLock.unlock();
        }
    }
}
```

#### CacheManager.java

```java
// Cache de agregações com LRU (Least Recently Used)
// - Armazena até S séries em memória
// - Quando S+1, remove série menos usada

public class CacheManager {
    private final int S;  // Tamanho máximo do cache

    // Cache: produto -> (dia -> Agregacao)
    private final Map<Integer, Map<Integer, Agregacao>> cacheAgregacoes = new HashMap<>();

    // Séries em memória (para cálculo rápido)
    private final Map<Integer, Map<Integer, List<Evento>>> seriesEmMemoria = new HashMap<>();

    // Ordem de acesso (para LRU)
    private final List<Integer> ordemAcesso = new ArrayList<>();

    /**
     * Obter agregação (com cache)
     * 1. Tenta ler do cache (readLock, recordCacheHit)
     * 2. Se não existe: computa ou usa streaming
     * 3. Armazena no cache
     * 4. Manage LRU
     */
    public Agregacao obterAgregacaoDia(int produtoID, int dia) {
        // Tenta read-lock primeiro (rápido se existir)
        readLock.lock();
        try {
            if (cacheAgregacoes.containsKey(produtoID)) {
                Agregacao existente = cacheAgregacoes.get(produtoID).get(dia);
                if (existente != null) {
                    metrics.recordCacheHit();
                    return existente;
                }
            }
        } finally {
            readLock.unlock();
        }

        metrics.recordCacheMiss();

        // Computation lock para evitar múltiplos cálculos
        String computationKey = produtoID + ":" + dia;
        ReentrantLock compLock = getComputationLock(computationKey);
        compLock.lock();
        try {
            // Double-check com cache
            readLock.lock();
            try {
                if (cacheAgregacoes.get(produtoID) != null &&
                    cacheAgregacoes.get(produtoID).get(dia) != null) {
                    return cacheAgregacoes.get(produtoID).get(dia);
                }
            } finally {
                readLock.unlock();
            }

            // Calcula (depende se série está em memória)
            Agregacao calculada;

            // Verifica se usa memória ou ficheiro
            boolean usarStreaming;
            readLock.lock();
            try {
                usarStreaming = seriesEmMemoria.size() >= S &&
                               !seriesEmMemoria.containsKey(dia);
            } finally {
                readLock.unlock();
            }

            if (usarStreaming) {
                // Calcula direto do ficheiro (streaming)
                calculada = eventoRepository.agregarEventosDia(produtoID, dia);
            } else {
                // Carrega série em memória e calcula
                calculada = calcularComMemoria(produtoID, dia);
            }

            // Armazena no cache
            writeLock.lock();
            try {
                Map<Integer, Agregacao> porProduto = cacheAgregacoes.computeIfAbsent(
                    produtoID, k -> new HashMap<>()
                );
                porProduto.put(dia, calculada);

                // Manage LRU
                if (seriesEmMemoria.size() >= S) {
                    removeNaoUsada();  // Remove série menos usada
                }
            } finally {
                writeLock.unlock();
            }

            return calculada;
        } finally {
            compLock.unlock();
        }
    }
}
```

---

### 6. Protocolo de Comunicação

#### Protocolo.java

```java
// Serialização/deserialização de mensagens sobre TCP
// Formato na rede: [tamanho:4bytes][dados:Nbytes]
// Garante robustez contra corrupção/ataques

public class Protocolo {
    private static final int MAX_TAMANHO = 10_000_000;  // Proteção contra ataques

    /**
     * Enviar mensagem ao servidor/cliente
     * Formato: [int length][byte[length] data]
     */
    public void enviar(Message msg, DataOutputStream out) throws IOException {
        byte[] data = msg.serialize();  // Serializa mensagem

        if (data.length <= 0 || data.length > MAX_TAMANHO) {
            throw new IOException("Tamanho inválido: " + data.length);
        }

        out.writeInt(data.length);   // Escreve tamanho
        out.write(data);             // Escreve dados
        out.flush();                 // Força flush (importante!)
    }

    /**
     * Receber mensagem
     * Bloqueia até receber mensagem completa
     */
    public Message receber(DataInputStream in) throws IOException {
        int len = in.readInt();  // Lê tamanho

        if (len <= 0 || len > MAX_TAMANHO) {
            throw new IOException("Tamanho inválido: " + len);
        }

        byte[] data = new byte[len];
        in.readFully(data);  // Bloqueia até ler todos os bytes

        return Message.deserialize(data);
    }
}
```

---

### 7. Monitorização

#### DeadlockMonitor.java

```java
// Detecção de deadlocks em background
// Executa periodicamente (ex: a cada 30 segundos)

public class DeadlockMonitor {
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Inicia verificação periódica
     */
    public void start(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(
            this::checkForDeadlocks,
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }

    /**
     * Verifica deadlocks usando JMX
     */
    private void checkForDeadlocks() {
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();

        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            // Deadlock detectado!
            ThreadInfo[] infos = threadBean.getThreadInfo(deadlockedThreads, true, true);

            // Log detalhado
            StringBuilder sb = new StringBuilder("DEADLOCK DETECTADO!\n");
            for (ThreadInfo info : infos) {
                sb.append("Thread: ").append(info.getThreadName())
                  .append("\n")
                  .append("Stack:\n");
                for (StackTraceElement ste : info.getStackTrace()) {
                    sb.append("  ").append(ste).append("\n");
                }
            }

            ErrorLogger.getInstance().logError("DeadlockMonitor", new Exception(sb.toString()));
        }
    }
}
```

#### PerformanceMetrics.java

```java
// Singleton para métricas globais
// Regista: requisições, erros, cache hits/misses, latência, throughput

public class PerformanceMetrics {
    private static PerformanceMetrics instance;  // Singleton

    // Métricas
    private long totalRequests = 0;
    private long totalErrors = 0;
    private long totalCacheHits = 0;
    private long totalCacheMisses = 0;
    private long totalLatencyNs = 0;
    private long minLatencyNs = Long.MAX_VALUE;
    private long maxLatencyNs = 0;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Registar requisição (chamado após cada request)
     */
    public void recordRequest(boolean success, long latencyNs) {
        lock.writeLock().lock();
        try {
            totalRequests++;
            if (!success) totalErrors++;

            totalLatencyNs += latencyNs;
            if (latencyNs < minLatencyNs) minLatencyNs = latencyNs;
            if (latencyNs > maxLatencyNs) maxLatencyNs = latencyNs;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Obter snapshot de métricas
     */
    public MetricsSnapshot getSnapshot() {
        lock.readLock().lock();
        try {
            double avgLatency = totalRequests > 0 ? totalLatencyNs / totalRequests / 1_000_000.0 : 0;
            double hitRate = (totalCacheHits + totalCacheMisses) > 0 ?
                            100.0 * totalCacheHits / (totalCacheHits + totalCacheMisses) : 0;

            return new MetricsSnapshot(
                totalRequests,
                totalErrors,
                totalCacheHits,
                totalCacheMisses,
                avgLatency,
                hitRate
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}
```

---

## Fluxo Detalhado de Operações

### Cenário: Registar Evento

```
CLIENTE                          MIDDLEWARE                   SERVIDOR
  |                                  |                            |
  | Chamada: registrarEvento()       |                            |
  |--------------------------------->|                            |
  |                                  | Gera tag=1                 |
  |                                  | Serializa EventoDTO        |
  |                                  | Cria Message(tag=1, ...)   |
  |                                  | Envia ao servidor          |
  |                                  |--------------------------->|
  |                                  |                            | Server.accept()
  |                                  |                            | Cria ClientHandler
  |                                  |                            | Submete ao pool
  |                                  |                            |
  |                                  |                            | ClientHandler.run()
  |                                  |                            | Lê Message(tag=1)
  |                                  |                            | Submete RequestProcessor
  |                                  |                            |
  |                                  |                            | RequestProcessor.run()
  |                                  |                            | dispatcher.despachar()
  |                                  |                            | Descodifica EventoDTO
  |                                  |                            | skeleton.processarRequisicao()
  |                                  |                            | servicoEventos.registrarEvento()
  |                                  |                            | Adiciona evento em memória
  |                                  |                            | Cria RespostaDTO
  |                                  |                            | Serializa resposta
  |                                  |                            | Cria Message(tag=1, response)
  |                                  |                            | Envia resposta
  |                                  |<---------------------------|
  |                                  | Demultiplexer.run()
  |                                  | Lê Message(tag=1, response)
  |                                  | Localiza thread em aguardar(1)
  |                                  | Armazena resposta no mapa
  |                                  | Notifica Condition
  |                                  |
  | Retorna resposta                |
  |<--------------------------------|
```

### Cenário: Obter Agregação

```
CLIENTE                          SERVIDOR
  |                                  |
  | Chamada: obterQuantidadeVendas()  |
  |--------------------------------->|
  | (similar ao anterior)             |
  |                                  | servicoAgregacoes.calcularAgregacao()
  |                                  | Para cada dia:
  |                                  |   - cacheManager.obterAgregacaoDia()
  |                                  |   - Se cache HIT: devolve rapidamente
  |                                  |   - Se cache MISS:
  |                                  |     - eventoRepository.agregarEventosDia()
  |                                  |     - Usa RandomAccessFile para skip
  |                                  |     - Calcula agregação
  |                                  |     - Armazena no cache (com LRU)
  |                                  | Soma agregações
  |                                  | Devolve resultado
  |<--------------------------------|
```

### Cenário: Novo Dia

```
CLIENTE                          SERVIDOR
  |                                  |
  | Chamada: novoDia()               |
  |--------------------------------->|
  |                                  | servicoEventos.novoDia()
  |                                  | writeLock.lock()
  |                                  | eventoRepository.salvarEventosDia()
  |                                  |   - Persiste eventos em ficheiro
  |                                  |   - Formato: eventos_dia_N.dat
  |                                  | Limpa eventosDiaAtual (memória)
  |                                  | diaAtual++
  |                                  | cacheManager.invalidarCache()
  |                                  | writeLock.unlock()
  |                                  | Devolve sucesso
  |<--------------------------------|
```

---

## Resumo de Decisões Técnicas

| Decisão                    | Razão                                       | Alternativa Rejeitada                                 |
| -------------------------- | ------------------------------------------- | ----------------------------------------------------- |
| **ThreadPool customizado** | Controlo fino, sem dependências             | Usar ExecutorService do Java                          |
| **Locks (ReentrantLock)**  | Segurança concorrente, Read/Write separados | Synchronized (mais simples mas menos flexível)        |
| **Protocolo customizado**  | Controlo sobre serialização e tamanho       | gRPC (mais automático mas menos controlo)             |
| **File-based repository**  | Simples, sem BD externa                     | Banco de dados relacional (mais robusto mas complexo) |
| **Cache com LRU**          | Otimiza agregações, economiza memória       | Cache sem limite (mais memória)                       |
| **Demultiplexer**          | Permite respostas assíncronas               | Pedidos síncronos bloqueantes                         |

---

## Detalhes Técnicos: Stubs, Demultiplexer, Skeletons, Dispatcher, ThreadPool

### Stubs (Client-side RPC)

#### Conceito

Stubs são proxies que implementam interfaces de serviços, mas em vez de executar lógica local, enviam pedidos ao servidor remotamente.

#### ServicoEventosStub.java (Exemplo)

```java
/**
 * Stub para ServicoEventos (implementa IServicoEventos)
 * Cada chamada de método é serializada e enviada ao servidor
 */
public class ServicoEventosStub implements IServicoEventos {
    private final ClienteMiddleware middleware;  // Canal de comunicação

    public ServicoEventosStub(ClienteMiddleware middleware) {
        this.middleware = middleware;
    }

    /**
     * Quando cliente chama registrarEvento():
     * 1. Cria EventoDTO com parametros
     * 2. Envia ao servidor (via middleware)
     * 3. Recebe resposta, desserializa
     * 4. Devolve resultado
     */
    @Override
    public RespostaDTO registrarEvento(EventoDTO dto) throws EventoException {
        try {
            // Envia request (SERVICO_EVENTOS, EVENTO_REGISTRAR, dto)
            byte[] resposta = middleware.enviar(
                SERVICO_EVENTOS,
                EVENTO_REGISTRAR,
                dto
            );

            // Desserializa resposta
            RespostaDTO resp = RespostaDTO.deserialize(resposta);

            if (!resp.isSucesso()) {
                throw new EventoException(resp.getMensagem());
            }

            return resp;
        } catch (Exception e) {
            throw new EventoException("Erro ao registar evento: " + e.getMessage());
        }
    }

    /**
     * Novo dia
     */
    @Override
    public RespostaDTO novoDia() throws EventoException {
        try {
            byte[] resposta = middleware.enviar(
                SERVICO_EVENTOS,
                EVENTO_NOVO_DIA,
                null  // Sem parametros
            );

            RespostaDTO resp = RespostaDTO.deserialize(resposta);

            if (!resp.isSucesso()) {
                throw new EventoException(resp.getMensagem());
            }

            return resp;
        } catch (Exception e) {
            throw new EventoException("Erro ao mudar de dia: " + e.getMessage());
        }
    }
}

/**
 * StubFactory - Factory Pattern para criar stubs
 * Centraliza criação de stubs
 */
public class StubFactory {
    private final ClienteMiddleware middleware;

    public IServicoEventos criarStubEventos() {
        return new ServicoEventosStub(middleware);
    }

    public IServicoAgregacoes criarStubAgregacoes() {
        return new ServicoAgregacoesStub(middleware);
    }

    public IServicoAutenticacao criarStubAutenticacao() {
        return new ServicoAutenticacaoStub(middleware);
    }

    public IServicoAdmin criarStubAdmin() {
        return new ServicoAdminStub(middleware);
    }
}
```

#### Por que Stubs?

- **Transparência**: Cliente chama métodos como se fossem locais
- **Encapsulação**: Detalhes de serialização/rede escondidos
- **Reutilização**: Mesma interface para local e remoto
- **Type-safety**: Compilador verifica tipos

---

### Demultiplexer (Correlação de Respostas)

#### Conceito

Problema: Cliente envia múltiplos requests. Como saber qual resposta corresponde a qual request?

Solução: Usar tags únicos para correlacionar request/response.

#### Fluxo Detalhado

```java
/**
 * Cliente Thread 1:
 * 1. Envia Message(tag=1, EVENTO_REGISTRAR, evento)
 * 2. Chama demux.aguardar(1)  <- BLOQUEIA aqui
 */

/**
 * Cliente Thread 2 (Demultiplexer):
 * 1. Lê resposta: Message(tag=1, response)
 * 2. Entrega resposta para Thread 1
 * 3. Thread 1 desbloqueia e retorna
 */

public class Demultiplexer implements Runnable {
    private final DataInputStream entrada;
    private final Protocolo protocolo;
    private final ReentrantLock lock;

    // Correlação: tag -> resposta
    private final Map<Long, byte[]> respostas = new HashMap<>();

    // Sincronização: tag -> Condition
    private final Map<Long, Condition> threadsEspera = new HashMap<>();

    /**
     * Thread Demultiplexer - Lê respostas em loop
     */
    @Override
    public void run() {
        try {
            while (shouldRun()) {
                // 1. Bloqueia até receber mensagem
                Message msg = protocolo.receber(entrada);

                // 2. É uma response?
                if (!msg.isResponse()) {
                    System.err.println("Mensagem inesperada: não é response");
                    continue;
                }

                // 3. Tag -1 = servidor encerrou
                if (msg.getTag() == -1) {
                    setServerShutdown(true);
                    acordarTodasThreads();
                    break;
                }

                // 4. Entrega resposta para thread cliente correspondente
                entregarResposta(msg.getTag(), msg.getPayload());
            }
        } catch (IOException e) {
            tratarErroConexao(e);
        }
    }

    /**
     * Thread cliente chama isto para aguardar resposta
     * @param tag: identificador do request
     * @return: payload da resposta
     */
    public byte[] aguardar(long tag) throws Exception {
        lock.lock();
        try {
            verificarErro();

            // Se resposta já chegou, devolve imediatamente
            if (respostas.containsKey(tag)) {
                return respostas.remove(tag);
            }

            // Senão, aguarda
            Condition condicao = lock.newCondition();
            threadsEspera.put(tag, condicao);

            // BLOQUEIA aqui
            while (!respostas.containsKey(tag)) {
                condicao.await();  // Libertina CPU, aguarda notificação
            }

            // Resposta chegou!
            return respostas.remove(tag);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Chamado quando resposta chega (thread Demultiplexer)
     */
    private void entregarResposta(long tag, byte[] payload) {
        lock.lock();
        try {
            respostas.put(tag, payload);

            // Notifica thread cliente que aguarda este tag
            Condition cond = threadsEspera.get(tag);
            if (cond != null) {
                cond.signal();  // ACORDA thread cliente
            }
        } finally {
            lock.unlock();
        }
    }
}

/**
 * Sequência temporal:
 *
 * T1: Cliente Thread 1 envia tag=1
 * T2: Cliente Thread 2 envia tag=2
 * T3: Demultiplexer lê resposta tag=2
 * T4: Demultiplexer notifica Thread 2
 * T5: Thread 2 retorna com resposta 2
 * T6: Demultiplexer lê resposta tag=1
 * T7: Demultiplexer notifica Thread 1
 * T8: Thread 1 retorna com resposta 1
 *
 * Note: Respostas podem chegar fora de ordem!
 */
```

#### Vantagens

- **Multiplexação**: Uma conexão para múltiplos requests
- **Eficiência**: Não precisa thread por conexão
- **Assincronismo**: Respostas desacopladas

---

### Skeletons (Server-side RPC)

#### Conceito

Skeleton é o inverso do Stub. Recebe requests remotos e invoca serviços locais.

#### ServicoEventosSkeleton.java (Exemplo)

```java
/**
 * Skeleton para ServicoEventos (implementa ISkeleton)
 * Desserializa request, invoca serviço, serializa resposta
 */
public class ServicoEventosSkeleton implements ISkeleton {
    private final ServicoEventos servico;

    public ServicoEventosSkeleton(ServicoEventos servico) {
        this.servico = servico;
    }

    /**
     * Processa request remoto
     * @param methodId: qual método foi pedido
     * @param parametros: parametros do método (desserializados)
     * @return: resposta serializada
     */
    @Override
    public RespostaDTO processarRequisicao(byte methodId, Object parametros) {
        try {
            switch (methodId) {
                case EVENTO_REGISTRAR:
                    // Parametros é EventoDTO
                    EventoDTO dto = (EventoDTO) parametros;
                    return servico.registrarEvento(dto);

                case EVENTO_LISTAR:
                    // Sem parametros
                    return servico.listarEventosDia();

                case EVENTO_FILTRAR:
                    // Parametros é FiltrarEventosDTO
                    FiltrarEventosDTO filtro = (FiltrarEventosDTO) parametros;
                    return servico.filtrarEventos(filtro);

                case EVENTO_NOVO_DIA:
                    // Sem parametros
                    return servico.novoDia();

                case EVENTO_NOTIFICAR_VENDA_ESPECIFICA:
                    NotificacaoDTO notif = (NotificacaoDTO) parametros;
                    return servico.verificarNotificacao(notif);

                default:
                    return RespostaDTO.erro("Método desconhecido: " + methodId);
            }
        } catch (EventoException e) {
            return RespostaDTO.erro("Erro: " + e.getMessage());
        } catch (Exception e) {
            return RespostaDTO.erro("Erro interno: " + e.getMessage());
        }
    }
}

/**
 * Fluxo:
 * Request -> Skeleton.processarRequisicao() -> Serviço.método() -> RespostaDTO
 */
```

#### Padrão Stub/Skeleton

```
        Cliente                         Servidor

        Stub                            Skeleton
        ├─ Interface remota             ├─ Interface local
        ├─ Serializa params             ├─ Desserializa params
        ├─ Envia request                ├─ Invoca serviço
        └─ Desserializa resposta        └─ Serializa resposta

        Vantagem: Separação cliente-servidor, facilita testes unitários
```

---

### Dispatcher (Roteamento de Requests)

#### Conceito

Dispatcher mapeia requests para skeletons corretos.

Fluxo:

```
Message (serviceId, methodId, payload)
   ↓
RequestDispatcher.despachar()
   ↓
Identifica skeleton (por serviceId)
   ↓
Descodifica parametros (pelo payload)
   ↓
Skeleton.processarRequisicao()
   ↓
RespostaDTO
```

#### RequestDispatcher.java (Detalhado)

```java
/**
 * Dispatcher - Mapeia requests para skeletons
 * Também mede performance de cada requisição
 */
public class RequestDispatcher {
    // Mapa: serviceId -> skeleton
    private final Map<Byte, ISkeleton> skeletonsPorServico = Map.of(
        SERVICO_AUTENTICACAO, new ServicoAutenticacaoSkeleton(servicoAuth),
        SERVICO_EVENTOS, new ServicoEventosSkeleton(servicoEventos),
        SERVICO_AGREGACOES, new ServicoAgregacoesSkeleton(servicoAgregacoes),
        SERVICO_ADMIN, new ServicoAdminSkeleton(servicoAdmin)
    );

    private final PerformanceMetrics metrics;

    /**
     * Despachador principal
     *
     * Responsabilidades:
     * 1. Medir latência (tempo total)
     * 2. Encontrar skeleton
     * 3. Descodificar parametros
     * 4. Invocar skeleton
     * 5. Registar métricas
     */
    public RespostaDTO despachar(Message msg) {
        long inicio = System.nanoTime();  // Mede tempo
        boolean sucesso = false;

        try {
            // 1. Encontra skeleton pelo serviceId
            ISkeleton skeleton = skeletonsPorServico.get(msg.getServiceId());
            if (skeleton == null) {
                return RespostaDTO.erro("Serviço desconhecido: " + msg.getServiceId());
            }

            // 2. Descodifica parametros do payload binário
            Object parametros = decodeParametros(
                msg.getServiceId(),
                msg.getMethodId(),
                msg.getPayload()
            );

            // 3. Invoca skeleton
            RespostaDTO resposta = skeleton.processarRequisicao(msg.getMethodId(), parametros);
            sucesso = resposta.isSucesso();

            return resposta;

        } finally {
            // 4. Registar métrica
            long latencia = System.nanoTime() - inicio;
            metrics.recordRequest(sucesso, latencia);
        }
    }

    /**
     * Descodificar parametros (switch por serviceId + methodId)
     */
    private Object decodeParametros(byte serviceId, byte methodId, byte[] payload)
            throws IOException {
        if (payload == null || payload.length == 0) {
            return null;  // Sem parametros
        }

        switch (serviceId) {
            case SERVICO_AUTENTICACAO:
                // Sempre desserializa UsuarioDTO
                return UsuarioDTO.deserialize(payload);

            case SERVICO_EVENTOS:
                switch (methodId) {
                    case EVENTO_REGISTRAR:
                        return EventoDTO.deserialize(payload);
                    case EVENTO_FILTRAR:
                        return FiltrarEventosDTO.deserialize(payload);
                    case EVENTO_LISTAR:
                    case EVENTO_NOVO_DIA:
                        return null;  // Sem parametros
                    default:
                        throw new IOException("Método desconhecido");
                }

            case SERVICO_AGREGACOES:
                return AgregacaoRequestDTO.deserialize(payload);

            case SERVICO_ADMIN:
                return null;  // Admin sem parametros

            default:
                throw new IOException("Serviço desconhecido: " + serviceId);
        }
    }
}
```

#### Por que Dispatcher?

- **Centralização**: Um ponto para todos os requests
- **Métricas**: Mede performance de todas as operações
- **Extensibilidade**: Fácil adicionar novos serviços
- **Encapsulamento**: Detalhes de roteamento isolados

---

### ThreadPoolImpl (Pool de Threads Customizado)

#### Conceito

Pool de threads reutiliza threads de um conjunto fixo, evitando overhead de criar/destruir.

#### Arquitetura

```
Submit task
    ↓
fila.add(task)
    ↓
Se workers < maxThreads: cria nova worker
    ↓
Notifica workers ociosas
    ↓
Workers pegam tasks da fila e executam
    ↓
Quando fila vazia e shutdown=true: workers terminam
```

#### ThreadPoolImpl.java (Detalhado)

```java
/**
 * ThreadPoolImpl - Implementação customizada de pool
 *
 * Por que customizada?
 * - Controlo fino sobre comportamento
 * - Sem dependências externas
 * - Entender concorrência em detalhes
 */
public class ThreadPoolImpl implements ThreadPool {
    private final Lock lock = new ReentrantLock();

    // Signals
    private final Condition notEmpty = lock.newCondition();      // Fila tem tasks
    private final Condition termination = lock.newCondition();   // Pool terminado

    // Estado
    private final Queue<Runnable> taskQueue = new ArrayDeque<>();
    private final Set<Thread> workers = new HashSet<>();

    private final int maxThreads;
    private final int maxQueueSize;

    private int workerCount = 0;      // Workers ativas
    private boolean shutdown = false;  // Modo normal shutdown
    private boolean shutdownNow = false; // Shutdown forçado

    /**
     * Submeter tarefa
     *
     * Retorna false se:
     * - Fila está cheia
     * - Pool está em shutdown
     */
    @Override
    public boolean submit(Runnable task) {
        lock.lock();
        try {
            // 1. Verifica estado
            if (shutdown || shutdownNow) {
                return false;  // Rejeita
            }

            // 2. Verifica espaço na fila
            if (taskQueue.size() >= maxQueueSize) {
                return false;  // Fila cheia, rejeita
            }

            // 3. Adiciona à fila
            taskQueue.add(task);

            // 4. Cria nova worker se necessário
            if (workerCount < maxThreads) {
                startWorker();
            }

            // 5. Acorda workers ociosas
            notEmpty.signal();

            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cria nova worker thread
     */
    private void startWorker() {
        workerIdCounter++;
        int workerId = workerIdCounter;

        Thread worker = new Thread(
            this::runWorkerLoop,
            "ThreadPool-worker-" + workerId
        );

        worker.setDaemon(false);  // Não é daemon
        workers.add(worker);
        workerCount++;
        worker.start();
    }

    /**
     * Loop de worker - Executa tasks continuamente
     */
    private void runWorkerLoop() {
        try {
            while (true) {
                Runnable task;

                lock.lock();
                try {
                    // 1. Aguarda fila não vazia
                    while (taskQueue.isEmpty() && !shutdown) {
                        notEmpty.await();  // BLOQUEIA aqui
                    }

                    // 2. Se shutdown forçado, sai imediatamente
                    if (shutdownNow) {
                        return;
                    }

                    // 3. Se shutdown normal e fila vazia, sai
                    if (taskQueue.isEmpty()) {
                        return;
                    }

                    // 4. Pega task
                    task = taskQueue.poll();

                } finally {
                    lock.unlock();
                }

                // 5. Executa task (SEM lock)
                try {
                    task.run();
                } catch (Exception e) {
                    // Log e continua
                    ErrorLogger.getInstance().logError("Worker-" + Thread.currentThread().getName(), e);
                }
            }
        } finally {
            // Cleanup
            finalizarWorker();
        }
    }

    /**
     * Remove worker do pool
     */
    private void finalizarWorker() {
        lock.lock();
        try {
            workerCount--;
            workers.remove(Thread.currentThread());

            if (workerCount == 0) {
                termination.signalAll();  // Acorda threads aguardando termination
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Shutdown gracioso
     * - Processa tasks restantes
     * - Rejeita novos tasks
     */
    @Override
    public void shutdown() {
        lock.lock();
        try {
            if (shutdown) return;
            shutdown = true;
            notEmpty.signalAll();  // Acorda workers (para saírem quando fila vazia)

            if (workerCount == 0) {
                termination.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Shutdown forçado
     * - Cancela tasks pendentes
     * - Interrompe workers
     */
    @Override
    public void shutdownNow() {
        lock.lock();
        try {
            if (shutdownNow) return;
            shutdown = true;
            shutdownNow = true;

            taskQueue.clear();  // Descarta tasks

            for (Thread t : workers) {
                t.interrupt();  // Interrompe workers
            }

            notEmpty.signalAll();
            termination.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Aguarda pool terminar
     * Bloqueia até todos workers terminarem
     */
    @Override
    public void awaitTermination() throws InterruptedException {
        lock.lock();
        try {
            while (!isTerminated()) {
                termination.await();  // Bloqueia
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean isTerminated() {
        return shutdown && workerCount == 0;
    }
}
```

#### Uso no Projeto

```java
// No Server:
ThreadPool clientHandlerPool = new ThreadPoolImpl(20, 20);      // Para ClientHandlers
ThreadPool requestPool = new ThreadPoolImpl(50, 100);           // Para RequestProcessors

// Adicionar client handler
clientHandlerPool.submit(new ClientHandler(...));

// Shutdown gracioso
server.shutdown();
clientHandlerPool.shutdown();
requestPool.shutdown();
clientHandlerPool.awaitTermination();  // Bloqueia até terminar
```

#### Vantagens

- **Eficiência**: Reutiliza threads, evita overhead
- **Limitação**: Maxthreads previne runaway
- **Rejeição**: Quando fila cheia, rejeitam novos tasks (backpressure)

---

## Notas Finais

- **Sincronização**: Usado locks para garantir segurança em acesso concorrente
- **Escalabilidade**: Pools limitam carga, cache reduz I/O
- **Robustez**: Tratamento de erros, timeouts, shutdown gracioso
- **Performance**: Métricas registadas, streaming de eventos, skip eficiente
