# 🧵 ThreadPool - Explicação Completa

## O Que É ThreadPool?

Um **ThreadPool** é um conjunto de threads reutilizáveis que processam tarefas de forma eficiente.

**Sem ThreadPool (ineficiente):**

```
Cada request → Criar nova thread → Processar → Destruir thread
└─ Overhead: criar/destruir threads é custoso
└─ Problema: 1000 requests = 1000 threads criadas/destruídas
└─ CPU gasta > 50% apenas em overhead de threads
```

**Com ThreadPool (eficiente):**

```
N threads pré-criadas → Reutilizam → Processam requests
└─ Overhead único: criar N threads uma vez
└─ Problema resolvido: 1000 requests = N threads reutilizadas
└─ CPU economizado: dedicado a processar, não a gerir threads
```

---

## Arquitetura de ThreadPoolImpl

```
┌──────────────────────────────────────────────────────────────┐
│ ThreadPoolImpl                                                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ FILA DE TAREFAS (taskQueue)                            │ │
│  │ ┌──────────────────────────────────────────────────┐  │ │
│  │ │ Task1 → Task2 → Task3 → Task4 → [vazio]        │  │ │
│  │ └──────────────────────────────────────────────────┘  │ │
│  │ Limite: maxQueueSize = 100 tarefas                   │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ WORKERS (Threads)                                      │ │
│  │ maxThreads = 50                                        │ │
│  │                                                        │ │
│  │ Worker-1: [aguardando] → [processando Task1]         │ │
│  │ Worker-2: [aguardando] → [processando Task2]         │ │
│  │ Worker-3: [aguardando] ← livre                       │ │
│  │ Worker-4: [aguardando] ← livre                       │ │
│  │ ...                                                    │ │
│  │ Worker-20: [aguardando] → [processando Task20]       │ │
│  │                                                        │ │
│  │ Ativas agora: 5 threads (workerCount=5)              │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ CONDITION VARIABLES (Sincronização)                   │ │
│  │                                                        │ │
│  │ notEmpty: Sinal quando fila tem tarefas             │ │
│  │           Workers aguardam aqui                       │ │
│  │                                                        │ │
│  │ termination: Sinal quando pool terminado            │ │
│  │             Clientes aguardam aqui                    │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Estrutura de Dados

```java
// Fila FIFO de tarefas
Queue<Runnable> taskQueue = new ArrayDeque<>();
// Limite: maxQueueSize (ex: 100)

// Conjunto de threads worker ativas
Set<Thread> workers = new HashSet<>();
// Limite: maxThreads (ex: 50)

// Lock para proteger estado compartilhado
Lock lock = new ReentrantLock();

// Condition: Notifica workers quando fila não vazia
Condition notEmpty = lock.newCondition();

// Condition: Notifica clientes quando pool terminou
Condition termination = lock.newCondition();

// Contadores
int workerCount = 0;        // Threads ativas
int workerIdCounter = 0;    // Para nomar threads
boolean shutdown = false;   // Modo normal (aceita restantes)
boolean shutdownNow = false;// Modo forçado (rejeita tudo)
```

---

## Ciclo de Vida: Submit de Uma Tarefa

```
┌────────────────────────────────────────────────────────────┐
│ submit(task)                                               │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ 1. Adquire lock                                            │
│    ├─ Se shutdown || shutdownNow → return false           │
│    │  └─ Rejeita (pool não aceita mais)                   │
│    └─ Se taskQueue.size() >= maxQueueSize → return false  │
│       └─ Rejeita (fila cheia, backpressure)              │
│                                                            │
│ 2. taskQueue.add(task)                                     │
│    └─ Adiciona à fila FIFO                                │
│                                                            │
│ 3. Se workerCount < maxThreads:                           │
│    ├─ startWorker()                                        │
│    └─ Cria nova thread se ainda não atingiu limite        │
│                                                            │
│ 4. notEmpty.signal()                                       │
│    └─ Acorda 1 worker aguardando (se houver)             │
│                                                            │
│ 5. Liberta lock                                            │
│    └─ return true (sucesso)                               │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**Exemplo:**

```
Pool: maxThreads=50, maxQueueSize=100

submit(task1): taskQueue=[task1], workers=1 ✓
submit(task2): taskQueue=[task1,task2], workers=2 ✓
...
submit(task50): taskQueue=[...50...], workers=50 ✓
submit(task51): taskQueue=[...50...], workers=50 ✓ (fila)
...
submit(task150): taskQueue=[100] ✓ (fila cheia)
submit(task151): taskQueue=[100] ✗ (REJEITADO! fila cheia)
```

---

## Ciclo de Vida: Worker Loop

```java
// Cada worker executa este loop infinitamente
private void runWorkerLoop() {
    try {
        while (true) {
            Runnable task;

            lock.lock();
            try {
                // ====== BLOQUEIA AQUI ======
                // Aguarda até: (1) fila não vazia OU (2) shutdown
                while (taskQueue.isEmpty() && !shutdown) {
                    notEmpty.await();  // Liberta CPU, aguarda sinal
                }

                // Se forçado: termina já
                if (shutdownNow) return;

                // Se shutdown normal e fila vazia: termina
                if (shutdown && taskQueue.isEmpty()) return;

                // Pega tarefa da fila
                task = taskQueue.poll();
            } finally {
                lock.unlock();
            }

            // Executa tarefa (SEM lock, paralelo com outras threads)
            if (task != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    ErrorLogger.getInstance().logError(...);
                }
            }
        }
    } finally {
        // Cleanup
        lock.lock();
        try {
            workerCount--;
            if (shutdown && workerCount == 0) {
                termination.signalAll();  // Notifica awaitTermination()
            }
        } finally {
            lock.unlock();
        }
    }
}
```

**Fluxo Visual:**

```
Worker-1:
┌─ lock.lock()
│  ├─ while (taskQueue.isEmpty() && !shutdown)
│  │  └─ notEmpty.await()  ← BLOQUEIA, liberta CPU
│  │     ↓
│  │     [Main thread signal]
│  │     ↓
│  │  Acorda
│  │
│  ├─ task = taskQueue.poll()  ← Pega tarefa
│  └─ lock.unlock()
│
├─ task.run()  ← Executa (SEM lock!)
│  ├─ Tempo: pode ser 100ms
│  └─ Outras threads processam em paralelo
│
└─ Volta ao loop (aguarda próxima tarefa)
```

---

## No Servidor: Dois Pools

O servidor cria **2 pools distintos** para isolação e performance:

```
┌─────────────────────────────────────────────────────────────────┐
│ SERVER                                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ clientHandlerPool = ThreadPoolImpl(20, 20)             │    │
│  │                                                        │    │
│  │ Tamanho: maxThreads = 20                              │    │
│  │          maxQueueSize = 20                            │    │
│  │                                                        │    │
│  │ Propósito:                                             │    │
│  │ └─ UMA thread por CLIENT                              │    │
│  │    └─ Lê requests do socket (operação rápida)         │    │
│  │    └─ Desserializa requests                           │    │
│  │    └─ Submete ao requestPool                          │    │
│  │                                                        │    │
│  │ Workload: IO-bound (socket read é rápido)            │    │
│  │ Latência: ~1-10 ms por request                        │    │
│  │                                                        │    │
│  │ Permite: 20 clients simultâneos                       │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ requestPool = ThreadPoolImpl(50, 100)                  │    │
│  │                                                        │    │
│  │ Tamanho: maxThreads = 50                              │    │
│  │          maxQueueSize = 100                           │    │
│  │                                                        │    │
│  │ Propósito:                                             │    │
│  │ └─ Processa requests (operação LENTA)                 │    │
│  │    ├─ Cache lookup                                    │    │
│  │    ├─ Lê ficheiros (I/O disco)                        │    │
│  │    ├─ Cálculos agregações                            │    │
│  │    └─ Serializa resposta                             │    │
│  │                                                        │    │
│  │ Workload: CPU-bound + IO-bound (I/O disco)          │    │
│  │ Latência: ~50-500 ms por request                     │    │
│  │                                                        │    │
│  │ Permite: 50 requests processando                     │    │
│  │          100 requests na fila (espera)               │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                 │
│  FLUXO:                                                         │
│  Client 1 → clientHandlerPool (worker-1)                      │
│             ├─ Lê request (rápido)                            │
│             └─ submit(RequestProcessor) → requestPool          │
│                                   ↓                            │
│                          requestPool (worker-1)               │
│                          ├─ Processa (lento)                  │
│                          └─ Envia resposta                    │
│                                                                 │
│  Client 2 → clientHandlerPool (worker-2)  ← Em paralelo!      │
│             ├─ Lê request                                     │
│             └─ submit(RequestProcessor) → requestPool          │
│                                   ↓                            │
│                          requestPool (worker-2) ← Em paralelo! │
│                          ├─ Processa                          │
│                          └─ Envia resposta                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Configuração (ServerConfig.java)

```java
public static final int N_CLIENT_HANDLERS = 20;
  └─ 20 threads para ler requests dos clients
  └─ Cada client ocupa 1 thread (dedicada)
  └─ Máximo 20 clients simultâneos

public static final int N_WORKERS_SERVER = 50;
  └─ 50 threads para processar requests
  └─ Compartilhadas entre todos os clients
  └─ Máximo 50 requests processando

public static final int REQUEST_QUEUE_SIZE = 100;
  └─ Fila de requests: até 100 aguardando
  └─ Total possível: 50 processando + 100 na fila = 150

public static final int DEFAULT_D = 30;
  └─ Histórico de 30 dias

public static final int DEFAULT_S = 10;
  └─ Cache com máximo 10 séries em memória
```

---

## Benefício: Isolação entre Pools

### Cenário: Agregação Lenta

```
TIME 0:
  Client 1 pede agregação (vai demorar 5 segundos)
    ├─ clientHandlerPool-1: lê (rápido, 1 ms)
    ├─ submit → requestPool-1: processa (lento, 5000 ms)
    └─ requestPool está com 1 worker ocupado

TIME 0.5s:
  Client 2 pede simples query (rápido, 10 ms)
    ├─ clientHandlerPool-2: lê (rápido, 1 ms) ← Não bloqueia!
    ├─ submit → requestPool-2: processa (rápido, 10 ms)
    └─ requestPool agora tem 2 workers

TIME 1s:
  Client 3, 4, 5... também conseguem ser atendidos
    ├─ clientHandlerPool: cada um em sua thread
    ├─ requestPool: processam em paralelo (até 50)
    └─ Sem bloqueios!

SEM separação (1 pool):
  ├─ Client 1: request slow (5s) → ocupa worker
  ├─ Client 2: request fast (10ms) → bloqueia! (fila)
  ├─ Client 3: request fast (10ms) → bloqueia! (fila)
  └─ Todos esperam o Client 1 terminar
```

---

## Ciclo de Vida: Shutdown Gracioso

```java
parar() {
    // 1. Para de aceitar novos clients
    setAtivo(false);
    fecharServerSocket();

    // 2. Fecha sockets de clients (ClientHandler detecta)
    fecharSocketsClientes();

    // 3. Dispatcher shutdown (recusa novos requests)
    dispatcher.shutdown();

    // 4. Pools entram em modo shutdown
    clientHandlerPool.shutdownNow();   // Rejeita tudo, cancela tasks
    requestPool.shutdown();             // Aceita tasks restantes

    // 5. Aguarda termination
    clientHandlerPool.awaitTermination();  // Bloqueia até workerCount=0
    requestPool.awaitTermination();        // Bloqueia até workerCount=0

    // 6. Tudo terminado
    System.out.println("Servidor encerrado");
}
```

**Fluxo:**

```
Shutdown gracioso (shutdown):
  └─ Aceita tasks restantes na fila
  └─ Workers processam e depois terminam
  └─ Tempo: até que fila fique vazia
  └─ Garantia: sem perdas de dados

Shutdown forçado (shutdownNow):
  ├─ Limpa fila (rejeita tasks)
  ├─ Interrompe threads (Thread.interrupt())
  ├─ Workers detectam e terminam rápido
  └─ Tempo: segundos
```

---

## Performance Característica

### ThreadPoolImpl Overhead

```
1. Criar pool:
   ├─ 20 ClientHandler threads: ~20 ms
   ├─ 50 Worker threads: ~50 ms
   └─ Total: ~70 ms (uma vez)

2. Submit task:
   ├─ Adquire lock: ~0.1 ms
   ├─ Adiciona à fila: ~0.01 ms
   ├─ Signal worker: ~0.1 ms
   └─ Total: ~0.2 ms (rápido!)

3. Worker loop:
   ├─ Aguarda (await): 0 ms (liberta CPU)
   ├─ Desacorda (signal): ~0.1 ms
   ├─ Poll tarefa: ~0.01 ms
   └─ Executa tarefa: depende da tarefa
```

### Sem ThreadPool (criar thread por request)

```
1. Criar thread:
   ├─ Alocar stack: ~1 ms
   ├─ Inicializar: ~1 ms
   └─ Total: ~2 ms por request! ⚠️

2. Com 1000 requests/s:
   └─ Overhead: 1000 * 2 ms = 2000 ms = 2 segundos de overhead!
   └─ 50% CPU apenas para criar threads!

3. Destruir thread:
   └─ ~1 ms por thread
   └─ 1000 * 1 ms = 1 segundo adicional!
```

**Conclusão:**

- **Com ThreadPool**: 1000 requests = ~0.2 ms overhead cada
- **Sem ThreadPool**: 1000 requests = ~2 ms overhead cada
- **Economia**: 10x mais eficiente! 🚀

---

## Resumo Visual

```
┌─────────────────────────────────────────────┐
│ Servidor (ThreadPool)                       │
├─────────────────────────────────────────────┤
│                                             │
│  20 ClientHandlers (lêem requests)          │
│  ├─ Worker-1: lendo Client-1                │
│  ├─ Worker-2: lendo Client-2                │
│  ├─ Worker-3: lendo Client-3                │
│  ├─ Worker-4: aguardando                    │
│  └─ ...                                     │
│                                             │
│  50 Workers (processam requests)            │
│  ├─ Worker-1: agregação (lenta)            │
│  ├─ Worker-2: login (rápido)               │
│  ├─ Worker-3: agregação (lenta)            │
│  ├─ Worker-4: filtro (moderado)            │
│  ├─ Worker-5: novo dia                     │
│  ├─ Worker-6-50: aguardando                │
│  └─ Queue: 20 requests na fila             │
│                                             │
└─────────────────────────────────────────────┘
```

---

## Comparação: ThreadPool vs ExecutorService

| Aspecto              | ThreadPool   | ExecutorService          |
| -------------------- | ------------ | ------------------------ |
| Controlo             | Total        | Limitado                 |
| Dependências         | Nenhumas     | Framework Java           |
| Rejeição customizada | Sim          | RejectedExecutionHandler |
| Double-check locking | Implementado | Interno                  |
| Monitoramento        | Customizado  | Padrão                   |
| Learning             | Alto         | Baixo                    |
| Projeto educacional  | ✅ Melhor    | Menos educativo          |

---

## Ficheiro Completo: ThreadPoolImpl.java (205 linhas)

Métodos principais:

1. `submit(task)`: Adiciona à fila
2. `shutdown()`: Modo gracioso
3. `shutdownNow()`: Modo forçado
4. `awaitTermination()`: Aguarda termination
5. `runWorkerLoop()`: Loop infinito de worker
6. `getActiveThreads()`: Retorna workers ativas

Estrutura:

- Lock + 2 Conditions (notEmpty, termination)
- Queue + Set de workers
- Contadores (workerCount, workerIdCounter)
- Estados (shutdown, shutdownNow)
