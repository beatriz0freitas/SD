# Resumo Rápido - Cheat Sheet para Defesa

## Componentes Principais

### Cliente

- **Cliente.java**: main, inicializa UI
- **ClienteMiddleware.java**: envia requests, aguarda respostas
- **Demultiplexer.java**: thread separada, lê respostas assincronamente
- **Stubs**: proxies para cada serviço remoto

### Servidor

- **Server.java**: aceita clientes
- **ClientHandler**: uma thread por cliente
- **RequestDispatcher**: mapeia requests para skeletons
- **ThreadPoolImpl**: customizado, 2 pools (handlers + workers)

### Serviços

- **ServicoEventos**: registra, filtra eventos
- **ServicoAgregacoes**: calcula agregações (cache com LRU)
- **ServicoAutenticacao**: login, registro
- **ServicoAdmin**: info do servidor

### Persistência & Cache

- **EventoFileRepository**: ficheiros binários (.dat)
- **CacheManager**: cache com LRU, max S séries
- **RandomAccessFile**: skip eficiente (não lê tudo)

### Protocolo

- **Message.java**: tag + serviceId + methodId + payload
- **Protocolo.java**: [tamanho:4bytes][dados]
- **Validação**: max 10 MB (proteção DoS)

## Decisões-Chave

| Decisão                | Razão                                         |
| ---------------------- | --------------------------------------------- |
| ThreadPool customizado | Controlo fino, aprendizagem                   |
| ReentrantReadWriteLock | Múltiplas leituras simultâneas                |
| Demultiplexer com tags | Respostas assincronamente, fora de ordem      |
| Cache LRU              | Economiza memória, dados quentes em RAM       |
| Ficheiros binários     | Simples, controlo total, sem BD               |
| Stubs/Skeleton         | Transparência RPC, type-safe                  |
| Dois thread pools      | Isolação: leitura rápida, processamento lento |

## Fluxos Críticos

### Registar Evento

```
1. Client: stub.registrarEvento(dto)
2. Middleware: gera tag, serializa, envia
3. Demultiplexer: bloqueia em aguardar(tag)
4. Servidor recebe Message
5. ClientHandler submete RequestProcessor ao pool
6. RequestProcessor: dispatcher.despachar()
7. Skeleton invoca servicoEventos.registrarEvento()
8. Evento adicionado em memória
9. RespostaDTO enviada
10. Demultiplexer entrega resposta
11. Client retorna
```

### Obter Agregação

```
1. Client: stub.obterQuantidadeVendas(produtoID, dias)
2. Para cada dia no intervalo:
   - cacheManager.obterAgregacaoDia()
   - Cache HIT: retorna rápido
   - Cache MISS:
     - Se cache cheio & série não em memória: streaming
     - Senão: carrega em memória
     - Calcula (sum quantidade, sum volume, avg preço)
     - Armazena no cache
3. Soma agregações dos dias
4. Retorna resultado
```

### Novo Dia

```
1. Client: stub.novoDia()
2. Servidor adquire WRITE lock
3. eventoRepository.salvarEventosDia() -> ficheiro
4. Limpa memória (eventosDiaAtual)
5. diaAtual++
6. Invalida cache
7. Liberta lock
8. Retorna sucesso
```

## Sincronização

- **lockEscrita** (ClienteMiddleware): protege envio ao socket
- **lockTags** (ClienteMiddleware): protege contador de tags
- **clientesLock** (Server): protege conjunto de clients
- **stateLock** (Server): protege boolean ativo
- **rwLock** (EventoFileRepository): read/write locks
- **lock** (ThreadPoolImpl): protege fila de tasks
- **lock** (Demultiplexer): protege mapa de respostas
- **lock** (CacheManager): protege cache
- **computationLocks** (CacheManager): por chave, evita múltiplos cálculos

## Validações

- Tamanho de mensagens: <= 10 MB
- Username: min 3 chars, apenas alfanuméricos
- Password: min 3 chars, apenas alfanuméricos
- Quantidade: > 0
- Preço: > 0
- ProdutoID: > 0
- Dias: > 0, <= D

## Métricas Registadas

- totalRequests, totalErrors, errorRate
- totalCacheHits, totalCacheMisses, cacheHitRate
- minLatencyMs, avgLatencyMs, maxLatencyMs
- throughput (req/s)
- uptime

## Stack Traces (Erros Comuns)

**StackException (socket closed)**: Cliente desconectou, é normal

**EOFException**: Fim de stream, cliente desconectou gracefully

**IOException (tamanho inválido)**: Dados corrompidos ou ataque

**InterruptedException**: Thread interrompida durante await()

## Performance Tips

- Cache atinge 80%+ hit rate em workloads normais
- RandomAccessFile skip evita ler 90%+ do ficheiro
- Lock-free reads (read lock não compete com write)
- Computation locks evitam duplicação de cálculos
- Buffering reduz syscalls 10x

## Deadlock Prevention

- Sempre acquirir locks na mesma ordem
- Timeouts em await() (não implementado mas considerado)
- DeadlockMonitor verifica a cada 30s
- Comunicação entre threads via Conditions (não busy wait)

## Se Perguntarem "Por que..."

### "Por que não usaram Framework X?"

- Controlo total, aprendizagem, sem dependências

### "Por que dois pools?"

- Isolação: leitura não compete com processamento pesado

### "Por que não usaram BD?"

- Simplicidade, controlo total, adequado para protótipo

### "Por que Stubs/Skeleton?"

- Transparência RPC, type-safe, fácil mockar para testes

### "Por que cache com LRU?"

- Economiza memória, mantém dados quentes em RAM

### "Por que ReentrantLock?"

- Read/Write locks, melhor performance em leitura pesada

## Benchmarks Esperados

- Throughput sem cache: ~100-200 req/s
- Throughput com cache: ~500-1000 req/s
- Latência média: 5-15ms
- Cache hit rate: 60-80%
- Escalabilidade: até 100 clients (com degradação linear)

## Respostas Modelo Curtas

**"Como evitam race conditions?"**

- ReentrantLock + ReadWriteLock em dados partilhados

**"Como correlacionam requests/responses?"**

- Cada request tem tag único
- Demultiplexer mapeia tag -> resposta
- Threads bloqueiam em Condition até resposta

**"Como otimizam agregações?"**

- Cache com LRU (até S séries em memória)
- Double-check locking (evita múltiplos cálculos)
- RandomAccessFile (streaming para séries não em cache)

**"Como escalabilidade?"**

- ThreadPool limita carga
- Cache reduz I/O
- Rejeição quando fila cheia (backpressure)

**"Como garantem integridade?"**

- Locks em acesso concorrente
- Validações antes de persistir
- Ficheiros binários ordenados (busca eficiente)

## Ficheiros Importantes

- ThreadPoolImpl.java: 183 linhas, 2 pools (20 + 50 threads)
- EventoFileRepository.java: 261 linhas, streaming + skip
- CacheManager.java: 279 linhas, LRU + computation locks
- ServicoEventos.java: 429 linhas, lógica central
- RequestDispatcher.java: 135 linhas, roteamento

## Testes Executáveis

```bash
# Compilar
make compile

# Testes
make test

# Testes específicos
make test-single TEST=EscalabilidadeTest
make test-single TEST=CargaTrabalhoTest

# Ver resumo
make test-summary

# Executar servidor
make run-server

# Executar cliente
make run-client
```

## Última Consulta

- Conhecer código de cor em ThreadPoolImpl, Demultiplexer, CacheManager
- Ter diagramas mentais de fluxo prontos
- Exemplos concretos de cenários (100 clients, cache miss, novo dia)
- Trade-offs: memória vs velocidade, simplicidade vs robustez
- Limitações: sem replicação, sem HA, sem BD, sem TLS
