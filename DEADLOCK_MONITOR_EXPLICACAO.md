# DeadlockMonitor - Explicação Detalhada

## 📌 Objetivo Principal

**DeadlockMonitor** é um vigilante de fundo que deteta **deadlocks** no servidor em tempo de execução.

```
Deadlock = Situação onde 2+ threads estão bloqueadas esperando uma pela outra
           de forma cíclica (impossível de sair)
```

---

## 🔴 O Problema: Deadlock

### Exemplo de Deadlock Clássico

```java
Thread 1:
  ├─ Adquire Lock A
  └─ Tenta adquirir Lock B (bloqueado por Thread 2)
     └─ BLOQUEIA AQUI (espera Lock B)

Thread 2:
  ├─ Adquire Lock B
  └─ Tenta adquirir Lock A (bloqueado por Thread 1)
     └─ BLOQUEIA AQUI (espera Lock A)

Resultado: DEADLOCK! 🔒
  Thread 1 espera Lock B (detido por Thread 2)
  Thread 2 espera Lock A (detido por Thread 1)
  → Ninguém consegue progredir
  → Servidor "congela"
```

### Impacto

- ❌ Servidor não responde
- ❌ Clients recebem timeout
- ❌ Impossível detetar sem ferramentas

**DeadlockMonitor = Detector automático de deadlocks**

---

## 🛠️ Componentes

### 1. ThreadMXBean (Java Management Extension)

```java
private final ThreadMXBean threadBean;

threadBean = ManagementFactory.getThreadMXBean();
```

**O que é:**

- API do Java para inspecionar threads em tempo de execução
- Consegue aceder a informações profundas (locks, stack traces)

**Métodos importantes:**

```java
threadBean.findDeadlockedThreads()
  └─ Retorna IDs das threads em deadlock (null se nenhuma)

threadBean.getThreadInfo(threadIds[], includeLockedMonitors, includeLockedSynchronizers)
  └─ Retorna informações detalhadas de cada thread (locks, stack)
```

### 2. ScheduledExecutorService (Scheduler)

```java
private final ScheduledExecutorService scheduler;

scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "DeadlockMonitor");
    t.setDaemon(true);    // ← Thread daemon (morre com main)
    return t;
});
```

**Função:**

- Executa `checkForDeadlocks()` em intervalos regulares
- **1 thread dedicada** (não bloqueia nada)
- Daemon (não impede shutdown)

### 3. ReentrantLock (Sincronização do Monitor)

```java
private final ReentrantLock stateLock = new ReentrantLock();
private boolean running;
```

**Função:**

- Sincroniza acesso ao flag `running`
- Evita race conditions entre `start()` e `stop()`

---

## 🔄 Ciclo de Vida Completo

### Passo 1: Construtor

```java
public DeadlockMonitor() {
    this.threadBean = ManagementFactory.getThreadMXBean();

    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DeadlockMonitor");
        t.setDaemon(true);        // ← Não impede shutdown do servidor
        return t;
    });

    this.running = false;         // ← Ainda não monitoriza
}
```

**Timeline:**

1. Obtém handle para ThreadMXBean (sem custo)
2. Cria ScheduledExecutor com 1 thread daemon
3. Marca como não running

**Código = 0ms**

### Passo 2: Iniciar (`start()`)

```java
public void start(long intervalSeconds) {
    // 1. Lock para evitar race conditions
    stateLock.lock();
    try {
        if (running) return;      // ← Se já running, ignora
        running = true;           // ← Marca como running
    } finally {
        stateLock.unlock();
    }

    // 2. Agenda tarefa periódica
    scheduler.scheduleAtFixedRate(
        this::checkForDeadlocks,    // ← Qual método executar
        intervalSeconds,             // ← Delay inicial (segundos)
        intervalSeconds,             // ← Intervalo entre execuções (segundos)
        TimeUnit.SECONDS             // ← Unidade de tempo
    );

    System.out.println("DeadlockMonitor iniciado (intervalo: " + intervalSeconds + "s)");
}
```

**Timeline:**

```
Chamada: start(5)
├─ Lock adquirido
├─ running = true
├─ ScheduledExecutor agenda checkForDeadlocks()
│  ├─ Primeira execução: 5 segundos depois
│  ├─ Segunda execução: 5 + 5 = 10 segundos depois
│  ├─ Terceira execução: 5 + 5 + 5 = 15 segundos depois
│  └─ ... infinito até stop()
└─ Retorna imediatamente
```

**Overhead:**

- Scheduling: ~1ms
- Thread daemon já criada: 0ms (reutiliza)

### Passo 3: Monitorização (`checkForDeadlocks()`)

```java
private void checkForDeadlocks() {
    // 1. Consulta ThreadMXBean: "há threads em deadlock?"
    long[] deadlockedThreads = threadBean.findDeadlockedThreads();

    if (deadlockedThreads != null && deadlockedThreads.length > 0) {
        // SIM! Há deadlock

        // 2. Obtém informações detalhadas
        ThreadInfo[] threadInfos = threadBean.getThreadInfo(
            deadlockedThreads,
            true,      // ← Inclui monitores bloqueados
            true       // ← Inclui synchronizers bloqueados
        );

        // 3. Formata relatório
        StringBuilder sb = new StringBuilder();
        sb.append("DEADLOCK DETECTADO! \n");
        sb.append("Threads em deadlock: ").append(deadlockedThreads.length).append("\n\n");

        for (ThreadInfo info : threadInfos) {
            // ← Para CADA thread em deadlock

            sb.append("Thread: ").append(info.getThreadName())
              .append(" (ID: ").append(info.getThreadId()).append(")\n");

            sb.append("Estado: ").append(info.getThreadState()).append("\n");
            // ← Ex: WAITING

            if (info.getLockName() != null) {
                sb.append("Aguardando lock: ").append(info.getLockName()).append("\n");
                // ← Ex: "java.util.concurrent.locks.ReentrantLock$NonfairSync@7e33e382"
            }

            if (info.getLockOwnerName() != null) {
                sb.append("Lock detido por: ").append(info.getLockOwnerName())
                  .append(" (ID: ").append(info.getLockOwnerId()).append(")\n");
                // ← Ex: "RequestWorker-5 (ID: 42)"
            }

            sb.append("Stack trace:\n");
            for (StackTraceElement element : info.getStackTrace()) {
                sb.append("  ").append(element.toString()).append("\n");
                // ← Ex: "ServicoAgregacoes.calcularAgregacao(ServicoAgregacoes.java:45)"
            }
            sb.append("\n");
        }

        // 4. Registra erro
        System.err.println(sb.toString());
        ErrorLogger.getInstance().logError("DeadlockMonitor",
            new Exception("Deadlock detectado!\n" + sb.toString()));
    }
}
```

**Output de Exemplo:**

```
DEADLOCK DETECTADO!
Threads em deadlock: 2

Thread: RequestWorker-1 (ID: 41)
Estado: WAITING
Aguardando lock: java.util.concurrent.locks.ReentrantLock$NonfairSync@7e33e382
Lock detido por: RequestWorker-2 (ID: 42)
Stack trace:
  java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:285)
  ServicoAgregacoes.calcularAgregacao(ServicoAgregacoes.java:45)
  RequestHandler.processar(RequestHandler.java:123)

Thread: RequestWorker-2 (ID: 42)
Estado: WAITING
Aguardando lock: java.util.concurrent.locks.ReentrantLock$NonfairSync@9f7c4e91
Lock detido por: RequestWorker-1 (ID: 41)
Stack trace:
  java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:285)
  CacheManager.get(CacheManager.java:78)
  ServicoAgregacoes.consultarCache(ServicoAgregacoes.java:67)
  RequestHandler.processar(RequestHandler.java:130)
```

**Análise:**

- Worker-1 espera lock detido por Worker-2
- Worker-2 espera lock detido por Worker-1
- **Stack traces mostram exatamente onde estão bloqueadas!**
- Overhead: ~50-200ms (inspecionar threads)

### Passo 4: Parar (`stop()`)

```java
public void stop() {
    // 1. Lock
    stateLock.lock();
    try {
        if (!running) return;      // ← Se já parado, ignora
        running = false;           // ← Marca como não running
    } finally {
        stateLock.unlock();
    }

    // 2. Shutdown gracioso do scheduler
    scheduler.shutdown();

    // 3. Aguarda até 5 segundos para tarefas terminarem
    try {
        scheduler.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        // 4. Se demorar > 5s, força shutdown
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
    }

    System.out.println("DeadlockMonitor parado");
}
```

**Timeline:**

```
Chamada: stop()
├─ running = false
├─ scheduler.shutdown()
│  └─ Pede para parar graciosamente
├─ awaitTermination(5s)
│  ├─ Aguarda até 5s
│  └─ Depois timeout
├─ Se > 5s: scheduler.shutdownNow()
│  └─ Força parada (Thread.interrupt)
└─ Retorna
```

---

## ⏱️ Perfor​mance & Overhead

### Tempo de Execução

| Operação                             | Tempo     | Notas                                 |
| ------------------------------------ | --------- | ------------------------------------- |
| `start(5)`                           | ~1ms      | Apenas scheduling, não bloqueia       |
| `checkForDeadlocks()` (sem deadlock) | ~10-20ms  | Verifica N threads, muito rápido      |
| `checkForDeadlocks()` (com deadlock) | ~50-200ms | Recolhe stack traces (maior overhead) |
| `stop()`                             | ~5-10ms   | Shutdown gracioso                     |

### Impacto no Servidor

**Cenário: 100 threads, monitoria a cada 5 segundos**

```
Tempo total de execução do servidor: 300 segundos
├─ checkForDeadlocks() executado: 300 / 5 = 60 vezes
├─ Tempo consumido (sem deadlock): 60 * 20ms = 1200ms = 1.2s
├─ % CPU: 1.2 / 300 = 0.4%
└─ Imperceptível!
```

**Se houver deadlock:**

```
checkForDeadlocks() detecta → emite alerta em ~100ms
└─ Servidor ainda está "congelado" (as threads bloqueadas não progridem)
└─ DeadlockMonitor é assíncrono, não "desbloqueia" threads
```

---

## 🎯 Quando é Usado

### No Projeto

```java
// Server.java

public class Server {
    private final DeadlockMonitor deadlockMonitor;

    public Server() {
        this.deadlockMonitor = new DeadlockMonitor();
    }

    public void iniciar() throws IOException {
        // ...
        deadlockMonitor.start(5);  // ← Monitoria a cada 5 segundos
        // ...
    }

    public void parar() {
        deadlockMonitor.stop();    // ← Para na shutdown
        // ...
    }
}
```

### Ciclo de Vida Completo

```
Servidor start()
├─ DeadlockMonitor.start(5)
│  └─ Scheduler agenda checkForDeadlocks() a cada 5s
│
├─ Servidor rodando...
│  └─ A cada 5s:
│     ├─ DeadlockMonitor verifica threads
│     ├─ Se deadlock: emite alerta (não desbloqueia)
│     └─ Se sem deadlock: continua normalmente
│
├─ [Ctrl+C ou erro fatal]
│  └─ Servidor shutdown()
│
└─ DeadlockMonitor.stop()
   └─ Scheduler termina (5s timeout)
   └─ Servidor sai
```

---

## 🔍 Informações Recolhidas

### ThreadInfo (informações por thread)

```java
ThreadInfo info;

info.getThreadName()           // Ex: "RequestWorker-5"
info.getThreadId()             // Ex: 42
info.getThreadState()          // Ex: WAITING, RUNNABLE, BLOCKED
info.getLockName()             // Ex: "java.util.concurrent.locks.ReentrantLock@7e33e382"
info.getLockOwnerName()        // Ex: "RequestWorker-7"
info.getLockOwnerId()          // Ex: 44
info.getStackTrace()           // Array de StackTraceElement (call stack)
```

### Útil para Debugar

**Exemplo Real:**

```
Tenho um deadlock, como debugar?

1. DeadlockMonitor detecta e imprime stack traces

2. Vejo:
   Thread-1 espera Lock A (detido por Thread-2)
   └─ Stack: ServicoAgregacoes.java:45

   Thread-2 espera Lock B (detido por Thread-1)
   └─ Stack: CacheManager.java:78

3. Analisas:
   ✓ ServicoAgregacoes.java:45 está a adquirir Lock A
     depois Lock B

   ✓ CacheManager.java:78 está a adquirir Lock B
     depois Lock A

   ✓ Ordem diferente = deadlock!

4. Solução: garantir mesma ordem de aquisição de locks
```

---

## 🚀 Vantagens

| Vantagem                   | Descrição                                           |
| -------------------------- | --------------------------------------------------- |
| **Detecção Automática**    | Não precisa de logs manuais para deadlock           |
| **Informações Detalhadas** | Stack traces mostram exatamente onde está bloqueado |
| **Não Invasivo**           | Não modifica código de aplicação (apenas adiciona)  |
| **Assincrono**             | Usa thread daemon, não bloqueia servidor            |
| **Scheduler Periódico**    | Verifica regularmente, configurable                 |
| **Alerta em Tempo Real**   | Quando detecta, notifica imediatamente              |

---

## ⚠️ Limitações

| Limitação           | Descrição                                            |
| ------------------- | ---------------------------------------------------- |
| **Não Desbloqueia** | Apenas detecta/alerta, não resolve deadlock          |
| **Overhead**        | ~10-200ms a cada verificação (trade-off)             |
| **Apenas Detecção** | Precisa de análise humana para resolver              |
| **Intervalo Fixo**  | Se intervalo = 60s, pode levar até 60s para detetar  |
| **JMX Overhead**    | ThreadMXBean tem overhead em JVMs com muitas threads |

---

## 📊 Comparação: Com vs Sem DeadlockMonitor

### ❌ SEM DeadlockMonitor

```
Tempo: 0:00
Servidor inicia
│
├─ 0:00-5:30 - Funcionando normal
│
├─ 5:30 - DEADLOCK!
│  ├─ 2 threads bloqueadas
│  ├─ Ninguém consegue calcular agregações
│  └─ Servidor "congelado"
│
├─ 5:30-6:00 - Cliente 1 aguarda resposta
│  ├─ Timeout!
│  ├─ Retry
│  ├─ Timeout novamente!
│  └─ Erro "Server not responding"
│
├─ 6:05 - Admin percebe que servidor não responde
│  ├─ SSH para servidor
│  ├─ ps -aux | grep java (vê processo vivo)
│  ├─ jstack (tira thread dump manual)
│  ├─ Analisa output (muito confuso)
│  └─ Finalmente vê: "DEADLOCK DETECTED"
│
└─ 6:20 - Admin mata servidor (kill -9)
   └─ Perda de dados, downtime 50min!
```

**Tempo para descobrir deadlock: ~50 minutos 😞**

### ✅ COM DeadlockMonitor

```
Tempo: 0:00
Servidor inicia
│
├─ 0:00-5:30 - Funcionando normal
│  ├─ DeadlockMonitor verifica a cada 5s
│  └─ Tudo OK
│
├─ 5:30 - DEADLOCK!
│  └─ (mesma situação)
│
├─ 5:35 - DeadlockMonitor ALERTA!
│  ├─ Imprime no stderr:
│  │  "DEADLOCK DETECTADO!"
│  │  "Threads em deadlock: 2"
│  │  "Thread-41 espera lock de Thread-42"
│  │  "Thread-42 espera lock de Thread-41"
│  │  "Stack traces → exatamente onde"
│  │
│  └─ Registra em ErrorLogger
│
├─ 5:36 - Admin vê alerta nos logs
│  ├─ Sabe exatamente o que é (deadlock)
│  ├─ Sabe exatamente onde (stack traces)
│  ├─ Pode corrigir ou reiniciar com confiança
│  └─ 4 minutos depois de ocorrer!
│
└─ 5:40 - Admin reinicia servidor
   └─ Downtime 10min em vez de 50min! ✅
```

**Tempo para descobrir deadlock: ~5 minutos 😊**

**Benefício: 10x mais rápido!**

---

## 🛡️ Conclusão

**DeadlockMonitor** = Vigilante silencioso que:

1. ✅ Verifica threads periodicamente (a cada 5 segundos)
2. ✅ Detecta deadlocks automaticamente
3. ✅ Fornece informações detalhadas (stack traces, locks)
4. ✅ Alerta admin em tempo real
5. ✅ Permite debug rápido

**Overhead:** ~0.4% CPU (imperceptível)
**Tempo para descobrir deadlock:** 5 minutos (vs 50 sem monitor)
**Valor:** Imenso para manutenção em produção!
