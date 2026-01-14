# Questões Extensivas para Defesa - Projeto SD

## Índice

1. [Questões sobre Threads e Concorrência](#threads-concorrência) - Q1-Q5
2. [Questões sobre Comunicação e Protocolo](#comunicação-protocolo) - Q6-Q9
3. [Questões sobre Arquitetura e Design](#arquitetura-design) - Q10-Q13
4. [Questões sobre Performance e Cache](#performance-cache) - Q14-Q16
5. [Questões sobre Persistência](#persistência) - Q17-Q19
6. [Questões de Implementação](#implementação) - Q20-Q23
7. [Questões de Decisões Técnicas](#decisões-técnicas) - Q24-Q30
8. [Questões de Cenários](#questões-de-cenários) - Q31-Q34
9. [Questões Analíticas](#questões-analíticas) - Q35-Q40
10. [Questões Específicas do Enunciado](#questões-específicas-do-enunciado) - Q41-Q55
11. [Questões Detalhadas do Cache LRU](#questões-detalhadas-do-cache-lru) - Q59-Q64
12. [Questões de Comparação](#questões-de-comparação) - Q56-Q58

---

## Questões sobre Threads e Concorrência

### Q1: ThreadPool Customizado

**Pergunta:** "Porquê implementaram um ThreadPool customizado em vez de usar ExecutorService do Java?"

**Resposta Esperada:**

- Controlo fino sobre comportamento
- Aprendizagem e compreensão de concorrência
- Sem dependências externas (melhor para projetos de demonstração)
- Possibilidade de adicionar funcionalidades específicas
- Permite implementar rejeição quando fila cheia (backpressure)

**Código de Suporte:**

```java
// ThreadPoolImpl permite:
// 1. Controlo de tamanho da fila
private final int maxQueueSize;

// 2. Rejeição de tasks
public boolean submit(Runnable task) {
    if (taskQueue.size() >= maxQueueSize) {
        return false;  // Rejeita
    }
}

// 3. Monitoramento de estado
public int getActiveThreads() { return workerCount; }
```

---

### Q2: Sincronização com ReentrantLock vs Synchronized

**Pergunta:** "Porquê usaram ReentrantLock em vez de synchronized em muitos lugares?"

**Resposta Esperada:**

- ReentrantLock permite read/write locks separados (melhor performance)
- ReadWriteLock permite múltiplas threads a ler em simultâneo
- Synchronized é all-or-nothing
- ReentrantReadWriteLock otimiza cenários com muitas leituras

**Exemplos:**

```java
// Repositório com muitas leituras, poucas escritas:
private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

// Múltiplas threads podem ler simultaneamente
readLock.lock();
try {
    return cacheAgregacoes.get(produtoID);
} finally {
    readLock.unlock();
}

// Apenas uma thread escreve
writeLock.lock();
try {
    cacheAgregacoes.put(produtoID, agregacao);
} finally {
    writeLock.unlock();
}
```

---

### Q3: Demultiplexer e Correlação de Respostas

**Pergunta:** "Explique o papel do Demultiplexer. Como evitam confundir respostas entre múltiplos requests?"

**Resposta Esperada:**

- Cada request tem um tag único (incrementado com lock thread-safe)
- Demultiplexer lê respostas em thread separada
- Mapeia tag -> resposta
- Thread cliente bloqueia em Condition até resposta chegar
- Respostas podem chegar fora de ordem, mas tags garantem correlação

**Fluxo:**

```
Cliente T1: tag=1, envia, bloqueia em aguardar(1)
Cliente T2: tag=2, envia, bloqueia em aguardar(2)

Demultiplexer (thread separada):
- Lê: Message(tag=2, resposta2)
- Entrega para T2
- T2 desbloqueia, retorna resposta2
- Lê: Message(tag=1, resposta1)
- Entrega para T1
- T1 desbloqueia, retorna resposta1

Note: Respostas chegam 2, 1 (fora de ordem)
```

---

### Q4: Condition Variables

**Pergunta:** "Qual é o papel das Condition variables no Demultiplexer?"

**Resposta Esperada:**

- Permitem threads libertar CPU enquanto aguardam
- Melhor que sleep() ou polling (desperdiçador de CPU)
- Cada tag tem uma Condition
- Quando resposta chega, notifica Condition correspondente

**Código:**

```java
public byte[] aguardar(long tag) {
    lock.lock();
    try {
        // Cria condition para este tag
        Condition cond = lock.newCondition();
        threadsEspera.put(tag, cond);

        // BLOQUEIA, libertando CPU
        while (!respostas.containsKey(tag)) {
            cond.await();
        }

        return respostas.remove(tag);
    } finally {
        lock.unlock();
    }
}

// Quando resposta chega
private void entregarResposta(long tag, byte[] payload) {
    respostas.put(tag, payload);
    Condition cond = threadsEspera.get(tag);
    cond.signal();  // Acorda thread que aguarda
}
```

---

### Q5: Double-Check Locking no Cache

**Pergunta:** "Porque usam double-check locking no CacheManager? Não é suficiente ler do cache uma vez?"

**Resposta Esperada:**

- Primeira verificação: sem lock (rápido)
- Se miss: adquire computation lock
- Segunda verificação: com lock (thread-safe)
- Evita múltiplos cálculos da mesma agregação

**Código:**

```java
// 1ª verificação (sem lock) - rápido
readLock.lock();
if (cacheAgregacoes.get(produtoID) != null) {
    return existente;  // Cache hit!
}
readLock.unlock();

// Computation lock para evitar múltiplos cálculos
compLock.lock();
try {
    // 2ª verificação (com lock) - thread-safe
    readLock.lock();
    if (cacheAgregacoes.get(produtoID) != null) {
        return existente;  // Outra thread calculou enquanto aguardávamos
    }
    readLock.unlock();

    // Calcula (seguro agora, apenas 1 thread por chave)
    Agregacao calculada = calcular();

    // Armazena
    writeLock.lock();
    cacheAgregacoes.put(produtoID, calculada);
    writeLock.unlock();
} finally {
    compLock.unlock();
}
```

---

## Questões sobre Comunicação e Protocolo

### Q6: Formato de Mensagens

**Pergunta:** "Explique o formato de mensagens. Porque incluem tamanho no início?"

**Resposta Esperada:**

- Formato: [tamanho:4bytes][dados:Nbytes]
- Tamanho permite saber quantos bytes ler
- TCP é stream, sem limites de mensagens
- Sem tamanho: servidor não sabe quando message acaba
- Protege contra corrupção (tamanho inválido = erro)

**Exemplo:**

```java
// Enviar
byte[] data = msg.serialize();  // Por exemplo, 256 bytes
out.writeInt(256);              // Escreve tamanho
out.write(data);                // Escreve dados
out.flush();

// Receber
int len = in.readInt();          // Lê tamanho (256)
byte[] data = new byte[len];
in.readFully(data);              // Bloqueia até ler 256 bytes
Message msg = Message.deserialize(data);
```

---

### Q7: Validação de Tamanho

**Pergunta:** "Porque validam tamanho máximo (10 MB)? É proteção contra o quê?"

**Resposta Esperada:**

- Proteção contra ataques DoS
- Cliente malicioso pode enviar tamanho gigante
- Servidor tentaria alocar memória gigante
- Proteção contra memory exhaustion
- Define limite razoável para aplicação

**Código:**

```java
private static final int MAX_TAMANHO = 10_000_000;  // 10 MB

public Message receber(DataInputStream in) throws IOException {
    int len = in.readInt();

    // Valida tamanho
    if (len <= 0 || len > MAX_TAMANHO) {
        throw new IOException("Tamanho inválido: " + len);
    }

    // Aloca array com segurança
    byte[] data = new byte[len];
    in.readFully(data);
    return Message.deserialize(data);
}
```

---

### Q8: Stubs e Skeleton Pattern

**Pergunta:** "Explique o padrão Stub/Skeleton. Qual é a vantagem?"

**Resposta Esperada:**

- Stub: proxy no cliente, envia requests
- Skeleton: adaptador no servidor, invoca serviços
- Vantagem: transparência (cliente não vê detalhes de rede)
- Interfaces iguais locais e remotas
- Type-safe (compilador verifica)

**Exemplo:**

```
Cliente: servicoEventos.registrarEvento(dto)
         └─> Internamente: stub envia Message ao servidor

Servidor: Message chega
          └─> Skeleton desserializa, invoca serviço real
          └─> servicoEventos.registrarEvento(dto) executa

Cliente: recebe resposta, como se fosse local
```

---

### Q9: RequestDispatcher

**Pergunta:** "Qual é o papel do RequestDispatcher? Poderia o Skeleton fazer tudo isso?"

**Resposta Esperada:**

- Dispatcher: roteamento central
- Todos os requests passam por um ponto
- Facilita medição de performance
- Desacoplamento: skeletons não precisam conhecer IDs dos serviços
- Extensibilidade: fácil adicionar novos serviços

---

## Questões sobre Arquitetura e Design

### Q10: Arquitetura em Camadas

**Pergunta:** "Describa a arquitetura em camadas do projeto. Por que essa separação?"

**Resposta Esperada:**

```
Cliente (UI)
   ↓
Stubs (RPC cliente)
   ↓
Middleware (Comunicação, Demultiplexer)
   ↓
Protocolo (Serialização)
   ↓ (Rede)
   ↓
Protocolo (Desserialização)
   ↓
Skeleton (RPC servidor)
   ↓
RequestDispatcher
   ↓
Serviços (Lógica)
   ↓
Cache + Repositório (Persistência)
```

Vantagens:

- **Modularidade**: cada camada tem responsabilidade clara
- **Testabilidade**: fácil testar cada camada isoladamente
- **Manutenibilidade**: mudanças em uma camada não afetam outras
- **Escalabilidade**: fácil adicionar funcionalidades

---

### Q11: Injeção de Dependências

**Pergunta:** "Como usam injeção de dependências? Onde?"

**Resposta Esperada:**

- RequestDispatcher cria serviços e injeta dependências
- ServicoEventos recebe: eventoRepository, cacheManager, D
- ServicoAgregacoes recebe: cacheManager, servicoEventos, eventoRepository
- Permite trocar implementações facilmente (para testes)

**Exemplo:**

```java
public static RequestDispatcher criar(int D, int S) {
    // Cria repositório
    IEventoRepository eventoRepository = RepositoryFactory.getInstance()
        .getEventoRepository();

    // Cria cache
    CacheManager cacheManager = new CacheManager(eventoRepository, S);

    // Cria serviços com injeção
    ServicoEventos servicoEventos = new ServicoEventos(
        eventoRepository,    // Injeção 1
        cacheManager,        // Injeção 2
        D                    // Parâmetro
    );

    ServicoAgregacoes servicoAgregacoes = new ServicoAgregacoes(
        cacheManager,        // Mesma instância
        servicoEventos,      // Referência
        eventoRepository,    // Mesma instância
        D
    );

    // Fácil trocar para testes!
}
```

---

### Q12: Factory Pattern

**Pergunta:** "Usam Factory Pattern em vários lugares. Porquê?"

**Resposta Esperada:**

- RepositoryFactory: centraliza criação de repositórios
- StubFactory: centraliza criação de stubs
- RequestDispatcher.criar(): factory method para dispatcher
- Vantagens:
  - Encapsulação de lógica de criação
  - Fácil mudar implementações
  - Centralizaçãode instâncias (singletons)

---

### Q13: Padrão Singleton para PerformanceMetrics

**Pergunta:** "Por que PerformanceMetrics é singleton? Não poderia ser static?"

**Resposta Esperada:**

- Singleton permite substituição (mockable para testes)
- Static methods são difíceis de mockar
- Double-check locking garante thread-safety
- Uma única instância compartilhada globalmente

**Código:**

```java
private static PerformanceMetrics instance;
private static final ReentrantReadWriteLock instanceLock = new ReentrantReadWriteLock();

public static PerformanceMetrics getInstance() {
    if (instance == null) {  // 1ª verificação (rápido)
        instanceLock.writeLock().lock();
        try {
            if (instance == null) {  // 2ª verificação (thread-safe)
                instance = new PerformanceMetrics();
            }
        } finally {
            instanceLock.writeLock().unlock();
        }
    }
    return instance;
}
```

---

## Questões sobre Performance e Cache

### Q14: Cache com LRU

**Pergunta:** "Explique a estratégia de cache. Por que LRU?"

**Resposta Esperada:**

- LRU = Least Recently Used
- Quando cache cheio (tamanho S), remove série menos usada
- Porque: séries antigas provavelmente não serão consultadas novamente
- Economiza memória, mantém dados quentes em RAM

**Algoritmo:**

```java
// ordemAcesso: lista de dias, ordenada por acesso recente
private final List<Integer> ordemAcesso = new ArrayList<>();

// Quando acessa um dia
ordemAcesso.remove(dia);      // Remove posição anterior
ordemAcesso.add(dia);          // Adiciona ao final (mais recente)

// Quando cache cheio
int diaAntigo = ordemAcesso.remove(0);  // Remove primeira (menos recente)
seriesEmMemoria.remove(diaAntigo);      // Remove do cache
```

---

### Q15: Streaming vs Memória no Cache

**Pergunta:** "Como decidem entre streaming (RandomAccessFile) e memória para cálculo?"

**Resposta Esperada:**

- Se série não está em memória E cache já tem S séries:
  - Usa RandomAccessFile (streaming)
  - Lê direto do ficheiro, sem alocar memória
  - Eficiente para séries grandes não frequentes
- Senão:
  - Carrega série em memória
  - Cálculo rápido em RAM
  - Próximas consultas deste dia usam cache

**Código:**

```java
boolean usarStreaming;
readLock.lock();
try {
    // Cache cheio E série não em memória?
    usarStreaming = seriesEmMemoria.size() >= S &&
                   !seriesEmMemoria.containsKey(dia);
} finally {
    readLock.unlock();
}

if (usarStreaming) {
    // Streaming: lê do ficheiro
    calculada = eventoRepository.agregarEventosDia(produtoID, dia);
} else {
    // Memória: carrega série, calcula rápido
    calculada = calcularComMemoria(produtoID, dia);
}
```

---

### Q16: Metrização de Performance

**Pergunta:** "Como medem performance? Que métricas recolhem?"

**Resposta Esperada:**

- Requisições: total, erros, taxa de erro
- Latência: mínima, máxima, média (em ns)
- Cache: hits, misses, taxa de acerto
- Throughput: requisições por segundo
- Uptime: tempo desde arranque

**Código:**

```java
public void recordRequest(boolean success, long latencyNs) {
    totalRequests++;
    if (!success) totalErrors++;

    totalLatencyNs += latencyNs;
    minLatencyNs = Math.min(minLatencyNs, latencyNs);
    maxLatencyNs = Math.max(maxLatencyNs, latencyNs);
}

// Snapshot
double avgLatencyMs = totalLatencyNs / totalRequests / 1_000_000.0;
double errorRate = 100.0 * totalErrors / totalRequests;
double cacheHitRate = 100.0 * totalCacheHits / (totalCacheHits + totalCacheMisses);
double throughput = totalRequests / (uptime / 1000.0);
```

---

## Questões sobre Persistência

### Q17: Persistência em Ficheiros

**Pergunta:** "Por que usaram ficheiros binários em vez de um banco de dados?"

**Resposta Esperada:**

- Simplicidade: sem dependências externas
- Controlo total: formato binário otimizado
- Adequado para protótipo/demonstração
- Pode-se trocar por BD sem mudanças na interface
- Repository Pattern permite isso

**Desvantagens reconhecidas:**

- Sem queries complexas (SQL)
- Sem transações ACID garantidas
- Sem replicação automática
- Não escalável para dados muito grandes

---

### Q18: Formato Binário de Eventos

**Pergunta:** "Explique o formato binário. Como permite buscas eficientes?"

**Resposta Esperada:**

- Formato: [numProdutos][produtoID1][numEventos1][eventos1...]...[produtoIDN][numEventosN][eventosN...]
- Produtos ordenados por ID (no write)
- Permite busca sem ler todo o ficheiro (skip bytes)

**Exemplo:**

```java
// Salvar: ordena produtos
List<Integer> produtosOrdenados = new ArrayList<>(eventosPorProduto.keySet());
Collections.sort(produtosOrdenados);  // Ordena!

// Agregar: skip eficiente
for (int i = 0; i < numProdutos; i++) {
    int pid = raf.readInt();
    int numEventos = raf.readInt();

    if (pid == produtoID) {
        // Encontrado!
        for (int j = 0; j < numEventos; j++) {
            agregacao.update(raf.readInt(), raf.readDouble());
        }
        break;
    } else if (pid < produtoID) {
        // Ainda não chegou, skip
        raf.skipBytes(numEventos * 12);  // 4+8=12 bytes por evento
    } else {
        // Passou
        break;
    }
}
```

---

### Q19: Novo Dia (Persistência)

**Pergunta:** "O que acontece quando chamam novoDia()? Qual é o procedimento?"

**Resposta Esperada:**

1. Adquire write lock
2. Persiste eventos do dia atual ao ficheiro
3. Limpa memória (eventosDiaAtual)
4. Incrementa diaAtual
5. Invalida cache (agregações do dia anterior mudam com novoDia)
6. Liberta lock

**Código:**

```java
@Override
public RespostaDTO novoDia() {
    lock.writeLock().lock();
    try {
        // 1. Persiste
        eventoRepository.salvarEventosDia(diaAtual, eventosDiaAtual);

        // 2. Limpa memória
        eventosDiaAtual.clear();

        // 3. Próximo dia
        diaAtual++;

        // 4. Invalida cache
        cacheManager.invalidarCache();

        return RespostaDTO.sucesso("Novo dia: " + diaAtual);
    } finally {
        lock.writeLock().unlock();
    }
}
```

---

## Questões de Implementação

### Q20: Autenticação

**Pergunta:** "Como implementaram autenticação? Como garantem segurança?"

**Resposta Esperada:**

- Senhas com hash SHA-256 (não plaintext)
- Comparação de hashes
- Estado de autenticação mantido por ClientHandler
- Admin tem password especial em ServerConfig

**Código:**

```java
private static class PasswordHasher {
    public String hash(String password) {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hashBytes);
    }
}

// No login
usuario = usuarioRepository.buscar(dto.getUsername());
if (usuario != null && usuario.getPasswordHash().equals(hasher.hash(dto.getPassword()))) {
    // Login bem-sucedido
}
```

---

### Q21: Notificações

**Pergunta:** "Como implementaram notificações de vendas? Que detecções fazem?"

**Resposta Esperada:**

- Detectam venda específica (por produto)
- Detectam vendas consecutivas (mesmo produto várias vezes seguidas)
- NotificationManager gerencia listeners
- Listeners recebem notificações em tempo real

---

### Q22: DTOs e Serialização

**Pergunta:** "Por que usam DTOs? Como funcionam?"

**Resposta Esperada:**

- DTOs = Data Transfer Objects
- Separam dados de lógica
- Métodos serialize/deserialize para conversão em bytes
- Permite envio pela rede

**Exemplo:**

```java
public class EventoDTO {
    private int produtoID;
    private int quantidade;
    private double preco;

    public byte[] serialize() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt(produtoID);
        out.writeInt(quantidade);
        out.writeDouble(preco);
        return baos.toByteArray();
    }

    public static EventoDTO deserialize(byte[] data) {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        EventoDTO dto = new EventoDTO();
        dto.produtoID = in.readInt();
        dto.quantidade = in.readInt();
        dto.preco = in.readDouble();
        return dto;
    }
}
```

---

### Q23: DeadlockMonitor

**Pergunta:** "Como funciona a detecção de deadlocks?"

**Resposta Esperada:**

- Usa ThreadMXBean (JMX)
- Periodicamente chama findDeadlockedThreads()
- Se encontra deadlock, loga stack traces
- Permite investigação pós-facto

**Código:**

```java
private void checkForDeadlocks() {
    long[] deadlockedThreads = threadBean.findDeadlockedThreads();

    if (deadlockedThreads != null && deadlockedThreads.length > 0) {
        ThreadInfo[] infos = threadBean.getThreadInfo(deadlockedThreads, true, true);

        StringBuilder sb = new StringBuilder("DEADLOCK DETECTADO!\n");
        for (ThreadInfo info : infos) {
            sb.append("Thread: ").append(info.getThreadName()).append("\n");
            for (StackTraceElement ste : info.getStackTrace()) {
                sb.append("  ").append(ste).append("\n");
            }
        }

        ErrorLogger.getInstance().logError("DeadlockMonitor", new Exception(sb.toString()));
    }
}
```

---

## Questões de Decisões Técnicas

### Q24: Rejeição em vez de Queue Infinita

**Pergunta:** "Por que rejeitam tasks quando fila cheia? Não seria melhor aceitar e esperar?"

**Resposta Esperada:**

- Rejeição = backpressure (pressão de volta)
- Força cliente a desacelerar
- Evita crescimento não-limitado de memória
- Melhor não processar alguns requests do que crash total
- Padrão em sistemas resilientes

---

### Q25: ClientHandler por Cliente

**Pergunta:** "Por que criar uma thread separada (ClientHandler) para cada cliente?"

**Resposta Esperada:**

- Permite leitura não-bloqueante de múltiplos clientes
- Sem isso: um cliente lento bloqueia todos
- Escalabilidade: servidor aguarda múltiplos clientes

---

### Q26: Two-Tier Thread Pool

**Pergunta:** "Por que dois pools (clientHandlerPool e requestPool)?"

**Resposta Esperada:**

- clientHandlerPool: apenas lê requests (rápido)
- requestPool: processa requests (lento, I/O, CPU)
- Separar permite:
  - Mais handlers (leitura é rápida)
  - Menos workers (processamento é lento)
  - Isolação: leitura não compete com processamento

**Configuração:**

```java
clientHandlerPool = new ThreadPoolImpl(20, 20);      // 20 threads
requestPool = new ThreadPoolImpl(50, 100);           // 50 threads
```

---

### Q27: Buffering em I/O

**Pergunta:** "Por que usam BufferedInputStream/BufferedOutputStream?"

**Resposta Esperada:**

- Sem buffer: cada byte é uma syscall (lento)
- Com buffer: lê/escreve em blocos (rápido)
- Melhora performance significativamente

**Código:**

```java
try (DataInputStream in = new DataInputStream(
        new BufferedInputStream(socket.getInputStream())
     );
     DataOutputStream out = new DataOutputStream(
        new BufferedOutputStream(socket.getOutputStream())
     )) {
    // Agora lê/escreve em blocos
}
```

---

### Q28: Shutdown Gracioso

**Pergunta:** "Como implementam shutdown gracioso? Qual é o procedimento?"

**Resposta Esperada:**

1. Servidor marca ativo=false (para de aceitar clientes)
2. ClientHandlerPool.shutdown() (aceita tasks restantes)
3. RequestPool.shutdown() (aceita tasks restantes)
4. Aguarda termination (bloqueia até terminar)
5. Fecha ServerSocket
6. Fecha sockets de clientes

**Vantagens:**

- Requests em progresso completam
- Não perde dados
- Encerramento controlado

---

### Q29: Tratamento de Erros

**Pergunta:** "Como tratam erros? Qual é a estratégia?"

**Resposta Esperada:**

- ErrorLogger: centraliza logging
- DTOs com mensagens de erro
- Try-catch em camadas críticas
- Logging sem crash
- Recuperação quando possível

---

### Q30: Validações

**Pergunta:** "Que validações fazem nos inputs?"

**Resposta Esperada:**

- EventoDTO: quantidade > 0, preço > 0, produtoID válido
- UsuarioDTO: username/password tamanho mínimo, caracteres válidos
- AgregacaoRequestDTO: produtoID válido, dias > 0
- Tamanho de mensagens: <= 10 MB
- Autenticação: obrigatória para operações sensíveis

---

## Questões de Cenários

### Q31: Cenário de Carga

**Pergunta:** "Como o sistema se comporta sob carga (100+ clientes simultâneos)?"

**Resposta Esperada:**

- ThreadPool limita threads ativas
- RequestQueue distribui load
- Cache reduz I/O
- Algumas requisições podem ser rejeitadas (fila cheia)
- Métricas mostram degradação graceful

---

### Q32: Cenário de Falha de Cliente

**Pergunta:** "O que acontece se cliente fechar conexão abruptamente?"

**Resposta Esperada:**

- SocketException ou EOFException em ClientHandler
- ClientHandler cleanup (finally)
- Socket fechado
- Cliente removido de clientesAtivos
- Servidor continua operacional

---

### Q33: Cenário de Múltiplos Clients Agregação

**Pergunta:** "Se 10 clients pedem agregação do mesmo dia simultaneamente, o que acontece?"

**Resposta Esperada:**

- 1º client: cache miss, calcula, armazena
- Computation lock (produtoID:dia) evita múltiplos cálculos
- 2-10: bloqueiam em computation lock
- Quando 1º termina, 2-10 leem do cache (hit!)
- Muito eficiente

---

### Q34: Cenário de Novo Dia

**Pergunta:** "Se clientes enviam eventos enquanto novoDia() está em execução, o que acontece?"

**Resposta Esperada:**

- novoDia() adquire write lock
- ClientHandlers bloqueiam em read lock
- Não há race condition
- Após novoDia(), eventos vão para novo dia

---

## Questões Analíticas

### Q35: Que Métricas Consideram Mais Importantes?

**Resposta Esperada:**

- Latência: indica responsividade
- Taxa de erro: indica confiabilidade
- Cache hit rate: indica eficiência
- Throughput: indica capacidade
- Uptime: indica estabilidade

---

### Q36: Se Tivessem Mais Tempo...

**Resposta Esperada:**

- BD relacional em vez de ficheiros
- Replicação de dados (HA)
- Criptografia de comunicação (TLS)
- Autenticação tokens (JWT)
- Monitoramento mais sofisticado
- Testes de carga automatizados
- API REST em vez de custom protocol

---

### Q37: Qual Parte Mais Difícil?

**Resposta Esperada:**

- Sincronização de threads
- Correlação de respostas assíncronas
- Otimização de cache com LRU
- Handling de edge cases

---

### Q38: Como Testariam Deadlocks?

**Resposta Esperada:**

- Intencionalmente criar cenários de deadlock
- Ajustar timeouts
- DeadlockMonitor registava detecção
- Testes de stress (muitas threads)

---

### Q39: Como Garantem Integridade de Dados?

**Resposta Esperada:**

- ReadWriteLock em repositórios
- Atomic operations (sem raceconditions)
- Teste de concorrência (múltiplas threads)
- Validações antes de persistir

---

### Q40: Que Mudanças Fariam para Escalabilidade Horizontal?

**Resposta Esperada:**

- Replicação de estado (todos servers sincronizados)
- Load balancer (distribui clients)
- Cache distribuído (Redis/Memcached)
- Shared storage (NFS/cloud)
- Message queue (Kafka/RabbitMQ) para comunicação

---

## Questões Específicas do Enunciado

### Q41: Requisito de Uma Única Conexão por Cliente

**Pergunta:** "O enunciado exige 'Para cada cliente, deve haver apenas uma única conexão com o servidor.' Como implementaram isto?"

**Resposta Esperada:**

- Cliente abre apenas uma conexão TCP na inicialização (ClienteMiddleware)
- Todas as requisições usam essa mesma conexão
- Thread cliente-lado da Middleware envia, aguarda resposta
- Demultiplexer correlaciona respostas via tags
- Se desconectar, reconecta (com re-autenticação)

**Código:**

```java
// ClienteMiddleware.java
private Socket socket;  // UMA SÓ conexão

public void conectar(String host, int port) {
    this.socket = new Socket(host, port);  // Cria conexão
    // Inicia Demultiplexer em thread separada
    new Thread(demultiplexer).start();
}

// Todas as operações usam esta conexão
public RespostaAutenticacao autenticar(String username, String password) {
    Message request = new Message(tag++, SERVICE_AUTH, METHOD_LOGIN, ...);
    socket.getOutputStream().write(request.serialize());
    // Aguarda resposta nesta mesma conexão
    return demultiplexer.aguardar(tag);
}
```

---

### Q42: Agregações Lazy On-Demand

**Pergunta:** "O enunciado pede agregações 'lazy, on demand'. O que implementaram?"

**Resposta Esperada:**

- Não pré-calculam nada
- Primeira solicitação de agregação: calcula e armazena em cache
- Próximas solicitações: devolvem do cache
- Cache invalidado no novoDia()
- Economia de CPU e memória

**Exemplo:**

```
Dia 1: Client pede quantidade_vendas(produtoID=5, dias=7)
  └─ Cache miss
  └─ CacheManager calcula (lê ficheiros de 7 dias)
  └─ Armazena em cache[produtoID=5] = Agregacao(...)
  └─ Retorna resultado

Dia 1: Outro client pede quantidade_vendas(produtoID=5, dias=7)
  └─ Cache hit
  └─ Retorna imediatamente de cache[5]

Dia 1: novoDia() invocado
  └─ Cache.invalidate()
  └─ Todos os caches descartados
```

---

### Q43: Limite S de Séries em Memória

**Pergunta:** "Como implementaram o limite de máximo S séries em memória durante agregações?"

**Resposta Esperada:**

- Parameter D = dias históricos, S = séries em memória (S < D)
- Cada dia anterior está persistido em ficheiro
- CacheManager mantém máximo S séries em RAM
- Se agregação precisa de mais: streaming + LRU (descarta séries antigas)
- RandomAccessFile permite ler sequencialmente sem carregar tudo

**Código Conceitual:**

```java
// CacheManager.java
private int seriesEmMemoria = 0;
private final int MAX_SERIES = S;  // Parâmetro servidor

public Agregacao calcularAgregacao(int produtoID, int dias) {
    Agregacao resultado = new Agregacao();

    for (int i = 0; i < dias; i++) {
        if (seriesEmMemoria >= MAX_SERIES) {
            // Estratégia: streaming
            // Lê ficheiro.dat dia i
            try (RandomAccessFile raf = new RandomAccessFile(ficheiro(i), "r")) {
                raf.seek(posicaoProduto(produtoID));  // Skip até produto
                resultado.agregar(ler(raf));  // Lê e agrega
                // Não armazena em memória
            }
        } else {
            // Carrega série em memória
            EventosSerie serie = carregarEmMemoria(i);
            seriesEmMemoria++;
            resultado.agregar(serie.filtrar(produtoID));
        }
    }

    return resultado;
}
```

---

### Q44: Persistência com D Dias

**Pergunta:** "O sistema mantém D dias anteriores. Como garantem que apenas D dias existem e não mais?"

**Resposta Esperada:**

- Parâmetro D configurado na inicialização do servidor
- novoDia() incrementa diaAtual
- Ficheiro do dia (diaAtual - D) é deletado (ou sobrescrito)
- Impede crescimento ilimitado de disco
- LRU em memória também respeita esta janela

**Código:**

```java
// Server.java
private int diaAtual = 0;
private final int D;  // Dias históricos

public void novoDia() {
    writeLock.lock();
    try {
        // Persiste eventos do dia atual
        repository.salvarEventosDia(diaAtual);

        // Remove dia fora da janela
        int diaRemove = diaAtual - D;
        if (diaRemove >= 0) {
            new File("eventos_" + diaRemove + ".dat").delete();
        }

        diaAtual++;
        eventosDiaAtual.clear();
    } finally {
        writeLock.unlock();
    }
}
```

---

### Q45: Notificações Síncronas vs Assíncronas

**Pergunta:** "O enunciado pede notificações bloqueantes (vendas simultâneas, vendas consecutivas). Como implementaram?"

**Resposta Esperada:**

- Cliente chama notificacaoVendasSimultaneas(p1, p2)
- Bloqueia INDEFINIDAMENTE até ocorrência
- Se novo dia iniciar enquanto bloqueado: retorna false
- Não há timeout (ou timeout configurável)
- Usa Conditions para sinalizar quando eventos ocorrem

**Código:**

```java
// ServicoNotificacoes.java
private Condition condicaoVendasSimultaneas = lock.newCondition();

public synchronized boolean aguardarVendasSimultaneas(
    int produtoID1, int produtoID2, int diaAtual) throws InterruptedException {

    while (!foramVendidos(produtoID1, produtoID2)) {
        // Se novo dia foi invocado, retorna false
        if (dia > diaAtual) {
            return false;
        }

        // Bloqueia até signal()
        condicaoVendasSimultaneas.await();
    }

    return true;
}

// Quando evento registado
public void notificarEvento(String produto) {
    condicaoVendasSimultaneas.signalAll();  // Acorda quem aguarda
}
```

---

### Q46: Filtrar Eventos com Serialização Eficiente

**Pergunta:** "O enunciado pede serialização compacta da lista de eventos. Como o fizeram?"

**Resposta Esperada:**

- Lista de eventos pode ter 100s de milhões de registos
- Produtos repetem-se muito
- Implementação customizada: não usam ObjectOutputStream
- Formato compacto: [numEventos][produtoID1:quantidade:preço1, ...]
- Compressão de IDs de produtos (cache local)

**Exemplo de Compactação:**

```
Ingénuo: [Evento(produtoID=12345, qtd=1, preco=9.99),
          Evento(produtoID=12345, qtd=3, preco=9.99),
          ...]
Bytes: 50 + 50 + 50 + ... = milhões de bytes

Otimizado: [numProdutos=2]
           [produtoID=12345][nomeLen=3][nome="ABC"]
           [numEventosProdutoA=2][qtd1=1][preco1=9.99][qtd2=3][preco2=9.99]
           [produtoID=67890][nomeLen=3][nome="XYZ"]
           [numEventosProdutoB=1][qtd1=5][preco1=19.99]
Bytes: 5 + (4+3+3) + (2 + 4 + 4 + 4 + 4) + (4+3+3) + (2 + 4 + 4) = ~50 bytes
```

---

### Q47: Protocol Binário com Limite de 10MB

**Pergunta:** "O enunciado proíbe APIs além de DataInputStream/OutputStream. Como implementaram?"

**Resposta Esperada:**

- Formato: [tamanho:4bytes][dados:N bytes]
- Validação: tamanho <= 10 MB (proteção DoS)
- ReadFully() para garantir leitura completa
- WriteInt() e WriteBytes() manualmente

**Protocolo:**

```java
// Protocolo.java
private static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024;  // 10 MB

public static byte[] serialize(Message msg) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    // Escreve campos
    dos.writeLong(msg.tag);
    dos.writeByte(msg.serviceId);
    dos.writeByte(msg.methodId);
    dos.writeInt(msg.payload.length);
    dos.write(msg.payload);

    byte[] result = baos.toByteArray();

    // [tamanho da mensagem][mensagem]
    ByteArrayOutputStream final = new ByteArrayOutputStream();
    DataOutputStream df = new DataOutputStream(final);
    df.writeInt(result.length);  // 4 bytes
    df.write(result);

    return final.toByteArray();
}

public static Message deserialize(DataInputStream dis) {
    int tamanho = dis.readInt();  // Lê tamanho

    if (tamanho > MAX_MESSAGE_SIZE) {
        throw new ProtocolException("Mensagem muito grande: " + tamanho);
    }

    byte[] dados = new byte[tamanho];
    dis.readFully(dados);  // Garante leitura completa

    // Parse campos
    long tag = dis.readLong();
    byte serviceId = dis.readByte();
    // ...
}
```

---

### Q48: Autenticação Obrigatória

**Pergunta:** "O enunciado proíbe operações sem autenticação. Como implementam isto?"

**Resposta Esperada:**

- Primeira operação: pedido autenticação/registo
- Servidor mantém mapa [clientID -> authenticated]
- RequestDispatcher valida status antes de processar
- Se não autenticado: retorna erro
- Só após sucesso: permite outras operações

**Código:**

```java
// RequestDispatcher.java
private Map<Long, Boolean> clientesAutenticados = new ConcurrentHashMap<>();

public RespostaDTO despachar(Message request, Long clientID) {
    // Operações permitidas sem auth
    if (request.serviceId == SERVICE_AUTH) {
        return processarAutenticacao(request);
    }

    // Outras operações: valida auth
    if (!clientesAutenticados.getOrDefault(clientID, false)) {
        return Resposta.erro("Não autenticado");
    }

    // Processa operação
    return processarOperacao(request);
}
```

---

### Q49: Teste de Escalabilidade (Enunciado)

**Pergunta:** "O enunciado pede testes de escalabilidade. Como fazem?"

**Resposta Esperada:**

- Teste com número crescente de clients: 1, 5, 10, 50, 100
- Mede: latência, throughput, taxa de sucesso
- Verifica degradação: throughput não cai drasticamente
- ThreadPool mantém performance até limite de threads
- Cache amortiza I/O

**Teste:**

```java
// EscalabilidadeTest.java
@Test
public void testeEscalabilidade() {
    for (int numClients : new int[]{1, 5, 10, 50, 100}) {
        long inicio = System.currentTimeMillis();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < numClients; i++) {
            threads.add(new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    cliente.registrarEvento(...);
                }
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try { t.join(); } catch (Exception e) {}
        });

        long tempo = System.currentTimeMillis() - inicio;
        double throughput = (numClients * 1000) / (tempo / 1000.0);

        System.out.println("Clients=" + numClients +
                         ", Throughput=" + throughput + " req/s");
    }
}
```

---

### Q50: Teste de Robustez (Enunciado)

**Pergunta:** "O enunciado pede testes de robustez. Como verificam comportamento quando cliente não consome respostas?"

**Resposta Esperada:**

- Cliente não lê responses (ou lê muito lentamente)
- Socket buffer enche-se
- Servidor bloqueia ao escrever
- ThreadPool esgota-se (todos threads bloqueadas)
- Sistema recupera quando cliente reconecta/desconecta
- Não há crash, degradação graceful

**Teste:**

```java
// RobustezTest.java
@Test
public void testeClienteNaoConsomeRespostas() throws Exception {
    Socket socket = new Socket("localhost", 8000);

    // Envia 100 requisições rápido
    for (int i = 0; i < 100; i++) {
        Message msg = new Message(...);
        socket.getOutputStream().write(msg.serialize());
    }

    // NÃO LÊ respostas!
    // Socket buffer enche
    // Servidor bloqueia em write()

    Thread.sleep(5000);

    // Desconecta
    socket.close();

    // Servidor continua funcional para outros clients
    Socket socket2 = new Socket("localhost", 8000);
    // Funciona normalmente
}
```

---

### Q51: Operação NovoDia - Sincronização

**Pergunta:** "A operação novoDia() é crítica (muda contador de dias, invalida cache). Como sincronizam?"

**Resposta Esperada:**

- Write lock exclusivo
- Ninguém lê/escreve enquanto executando
- Serialização: apenas 1 novoDia() por vez
- Outros clients aguardam
- Cachemanager invalida atomicamente

**Código:**

```java
// ServicoEventos.java
public void novoDia() {
    // Adquire lock EXCLUSIVO
    writeLock.lock();
    try {
        // 1. Persiste dia atual
        repository.salvarEventosDia(diaAtual, eventosDiaAtual);

        // 2. Limpa memória
        eventosDiaAtual.clear();

        // 3. Incrementa contador
        diaAtual++;

        // 4. Invalida cache
        cacheManager.invalidate();

        // 5. Notifica clients bloqueados em notificações
        condicaoNovoDia.signalAll();

    } finally {
        writeLock.unlock();
    }
}

// Durante novoDia(): nenhuma operação consegue progresso
// Tudo espera pelo write lock
```

---

### Q52: DTOs para Serialização

**Pergunta:** "Usam DTOs (Data Transfer Objects) para serializar dados. Porquê?"

**Resposta Esperada:**

- Isolam entidades de negócio da serialização
- Permitem transformação de dados
- Controlo sobre o que é enviado (security)
- Fácil manutenção: mudança no DTO não afeta domínio
- Serialização customizada (compacta)

**Exemplo:**

```java
// Entidade de domínio (não serializa diretamente)
public class Evento {
    private int id;
    private String produto;
    private int quantidade;
    private double preco;
    // ... lógica de negócio
}

// DTO para cliente
public class EventoDTO {
    public int produtoID;
    public int quantidade;
    public double preco;

    // Serializar compacto
    public byte[] serialize() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(produtoID);
        dos.writeInt(quantidade);
        dos.writeDouble(preco);
        return baos.toByteArray();
    }
}
```

---

### Q53: Impacto de D (dias históricos)

**Pergunta:** "Como muda comportamento com D=2 vs D=30?"

**Resposta Esperada:**

- D=2: apenas 2 dias em histórico (pequeno)
  - Menos ficheiros
  - Agregações rápidas
  - Menos espaço disco
- D=30: 30 dias em histórico
  - Mais ficheiros
  - Agregações mais lentas
  - Mais espaço disco
  - Cache mais importante

**Exemplo:**

```
D=2, S=1:
- Dia 0 em memória (eventos novos)
- Dia -1 em ficheiro (d1.dat)
- Dia -2 em ficheiro (d2.dat)
- Dia -3: DELETADO

Agregação(dias=7): erro (d=7 > D=2)

D=30, S=5:
- Dia 0 em memória
- Dia -1 a -5 em memória (cache)
- Dia -6 a -29 em ficheiro
- Agregação(dias=7): lê 7 ficheiros
```

---

### Q54: Impacto de S (séries em memória)

**Pergunta:** "Como varia comportamento com S=1 vs S=100?"

**Resposta Esperada:**

- S=1: apenas 1 série cabe em memória
  - Streaming quase sempre
  - Lento (RandomAccessFile)
  - Memória baixa
  - LRU invalida constantemente
- S=100: 100 séries cabem em memória
  - Mais cache hits
  - Mais rápido
  - Mais memória usada
  - Melhor throughput

**Fórmula:**

```
Memoria_usada = S * tamanho_media_serie
Throughput ~ 1 / (taxa_I/O_streaming)

S=1:  Memoria=10MB,  Throughput=100 req/s (muita I/O)
S=10: Memoria=100MB, Throughput=500 req/s
S=100: Memoria=1GB,  Throughput=1000 req/s
```

---

### Q55: Validações de Input (Enunciado)

**Pergunta:** "O enunciado pede validação. Quais validam?"

**Resposta Esperada:**

- Username: min 3 chars, alfanuméricos
- Password: min 3 chars, alfanuméricos
- Quantidade: > 0
- Preço: > 0
- ProdutoID: > 0
- Dias: 0 < d <= D
- Tamanho mensagem: <= 10 MB

**Código:**

```java
// ValidadorEntrada.java
public static void validarCredenciais(String username, String password) {
    if (username == null || username.length() < 3) {
        throw new IllegalArgumentException("Username inválido");
    }
    if (!username.matches("[a-zA-Z0-9]+")) {
        throw new IllegalArgumentException("Username deve ser alfanumérico");
    }
    // Similar para password
}

public static void validarEvento(int produtoID, int qtd, double preco) {
    if (produtoID <= 0) throw new IllegalArgumentException("ProdutoID <= 0");
    if (qtd <= 0) throw new IllegalArgumentException("Quantidade <= 0");
    if (preco <= 0) throw new IllegalArgumentException("Preço <= 0");
}

public static void validarAgregacao(int produtoID, int dias, int D) {
    if (dias <= 0 || dias > D) {
        throw new IllegalArgumentException("Dias fora de range [1, " + D + "]");
    }
}
```

### Q59: Cache LRU - Estrutura e Algoritmo

**Pergunta:** "Como implementaram a cache LRU? Explique a estrutura de dados e algoritmo."

**Resposta Esperada:**

- LRU = Least Recently Used
- Estrutura: `ordemAcesso = List<Integer>` (dias ordenados por uso)
- Mapa: `seriesEmMemoria = Map<Integer, Map<Integer, List<Evento>>>`
- Quando acessa dia: move para final da lista (mais recente)
- Quando cache cheio: remove primeiro da lista (menos recente)

**Código Detalhado:**

```java
// CacheManager.java
private final List<Integer> ordemAcesso = new ArrayList<>();
private final Map<Integer, Map<Integer, List<Evento>>> seriesEmMemoria = new HashMap<>();
private final int S;  // Tamanho máximo

public Agregacao calcularComMemoria(int produtoID, int dia) {
    writeLock.lock();
    try {
        // Se série já está em memória
        if (seriesEmMemoria.containsKey(dia)) {
            // Move para final (mais recente)
            ordemAcesso.remove(Integer.valueOf(dia));
            ordemAcesso.add(dia);

            List<Evento> eventos = seriesEmMemoria.get(dia).get(produtoID);
            return agregarEventos(eventos);
        }

        // Se cache está cheio
        if (seriesEmMemoria.size() >= S) {
            // Remove menos recente (primeiro da lista)
            Integer diaRemover = ordemAcesso.remove(0);  // Remove
            seriesEmMemoria.remove(diaRemover);          // Delete
            System.out.println("LRU: removida série dia " + diaRemover);
        }

        // Carrega série em memória
        Map<Integer, List<Evento>> seriesDia = eventoRepository.carregarEventosDia(dia);
        seriesEmMemoria.put(dia, seriesDia);
        ordemAcesso.add(dia);  // Adiciona ao final

        System.out.println("Carregada série dia " + dia + " (" + seriesEmMemoria.size() + "/" + S + ")");

        // Agrega eventos
        List<Evento> eventos = seriesDia.get(produtoID);
        return agregarEventos(eventos);
    } finally {
        writeLock.unlock();
    }
}
```

**Exemplo de Execução:**

```
S = 3 (cache tem espaço para 3 dias)

Estado inicial:
  ordemAcesso: []
  seriesEmMemoria: {}

1. Acesso dia 5:
  ordemAcesso: [5]
  seriesEmMemoria: {5 -> {...}}

2. Acesso dia 3:
  ordemAcesso: [5, 3]
  seriesEmMemoria: {5 -> {...}, 3 -> {...}}

3. Acesso dia 7:
  ordemAcesso: [5, 3, 7]
  seriesEmMemoria: {5 -> {...}, 3 -> {...}, 7 -> {...}}

4. Acesso dia 2 (cache cheio!):
  - Remove primeiro: dia 5 (menos recente)
  ordemAcesso: [3, 7, 2]
  seriesEmMemoria: {3 -> {...}, 7 -> {...}, 2 -> {...}}

5. Acesso dia 3 novamente:
  - Já está em memória
  - Move para final
  ordemAcesso: [7, 2, 3]
  seriesEmMemoria: {7 -> {...}, 2 -> {...}, 3 -> {...}}

6. Acesso dia 1 (cache cheio!):
  - Remove primeiro: dia 7 (menos recente)
  ordemAcesso: [2, 3, 1]
  seriesEmMemoria: {2 -> {...}, 3 -> {...}, 1 -> {...}}
```

---

### Q60: Double-Check Locking na Cache

**Pergunta:** "A cache usa double-check locking. Porque essa otimização?"

**Resposta Esperada:**

- 1ª verificação: sem lock (read lock rápido)
  - Se hit: retorna imediatamente
- 2ª verificação: com computation lock
  - Se miss: evita múltiplos cálculos simultâneos

**Benefício:**

- Reduz contenção: miss é raro, maioria são hits
- Hits retornam rápido (sem computation lock)
- Misses sincronizam apenas uma thread por chave

**Código:**

```java
public Agregacao obterAgregacaoDia(int produtoID, int dia) {
    // ===== 1ª Verificação (rápida) =====
    readLock.lock();
    try {
        Map<Integer, Agregacao> porProduto = cacheAgregacoes.get(produtoID);
        if (porProduto != null) {
            Agregacao existente = porProduto.get(dia);
            if (existente != null) {
                metrics.recordCacheHit();
                return existente;  // ✅ Cache hit, retorna rápido!
            }
        }
    } finally {
        readLock.unlock();
    }

    metrics.recordCacheMiss();

    // ===== Miss: sincroniza cálculo =====
    String computationKey = produtoID + ":" + dia;
    ReentrantLock compLock = getComputationLock(computationKey);

    compLock.lock();
    try {
        // ===== 2ª Verificação (thread-safe) =====
        readLock.lock();
        try {
            Map<Integer, Agregacao> porProduto = cacheAgregacoes.get(produtoID);
            if (porProduto != null) {
                Agregacao existente = porProduto.get(dia);
                if (existente != null) {
                    return existente;  // Outra thread calculou
                }
            }
        } finally {
            readLock.unlock();
        }

        // ===== Calcula (seguro, apenas 1 thread por chave) =====
        Agregacao calculada = calcularComMemoria(produtoID, dia);

        // ===== Armazena no cache =====
        writeLock.lock();
        try {
            cacheAgregacoes.computeIfAbsent(produtoID, k -> new HashMap<>())
                           .put(dia, calculada);
        } finally {
            writeLock.unlock();
        }

        return calculada;
    } finally {
        compLock.unlock();
        releaseComputationLock(computationKey);
    }
}
```

---

### Q61: Memory Leak em Tags do Demultiplexer

**Pergunta:** "O Demultiplexer pode ter memory leak de tags? Explique."

**Resposta Esperada:**

- ❌ **Sim, existe potencial leak**
- Se exceção é lançada em `aguardar()` antes de `remove()`:
  - Tag fica em `respostas` indefinidamente
  - Thread fica em `threadsEspera` indefinidamente
- Após 1000+ requests com erros: thousands de tags vazadas

**Problema Identificado:**

```java
// PROBLEMA: se exceção antes de remove()
public byte[] aguardar(long tag) throws Exception {
    lock.lock();
    try {
        verificarErro();  // ← Pode lançar exceção
        if (serverShutdown) throw new IOException(...);  // ← Exception!
        if (!ativo) throw new IOException(...);          // ← Exception!

        threadsEspera.put(tag, condicao);

        while (!respostas.containsKey(tag) && ...) {
            condicao.await();  // ← Pode ter InterruptedException
        }

        if (!ativo) throw new IOException(...);  // ← Exception!
        verificarErro();  // ← Exception!

        threadsEspera.remove(tag);  // ← Nunca executa se exception acima!
        return respostas.remove(tag);  // ← Nunca executa!
    } finally {
        lock.unlock();
    }
}
```

**Impacto:**

```
Scenario: 100 requests com servidor desligando

1. Client 1: tag=1, enviado
   - Demux está a ler
   - Server envia shutdown (-1)
   - demux.aguardar(1) → serverShutdown=true → lança IOException
   - tag=1 FICA EM respostas e threadsEspera ❌

2. Client 2: tag=2, igual ao anterior ❌
...
100. Client 100: tag=100, igual ❌

Resultado:
- respostas.size() = 100 (100 tags vazadas)
- threadsEspera.size() = 100 (100 Conditions vazadas)
- Memória: 100 * (8 + 48) = 5.6 KB + overhead
- Com 1000+ requests: ~50 KB de leak
- Com 1 milhão requests: ~50 MB de leak
```

**Solução: Try-Finally:**

```java
public byte[] aguardar(long tag) throws Exception {
    lock.lock();
    Condition condicao = lock.newCondition();
    threadsEspera.put(tag, condicao);

    try {
        verificarErro();
        if (serverShutdown) throw new IOException("Servidor encerrado");
        if (!ativo) throw new IOException("Demultiplexer parado");

        while (!respostas.containsKey(tag) && erro == null && !serverShutdown && ativo) {
            condicao.await();
        }

        if (!ativo) throw new IOException("Demultiplexer parado durante espera");
        if (serverShutdown) throw new IOException("Servidor encerrado durante espera");
        verificarErro();

        return respostas.remove(tag);
    } finally {
        // ✅ SEMPRE executa, mesmo com exceção
        threadsEspera.remove(tag);
        respostas.remove(tag);
        lock.unlock();
    }
}
```

---

### Q62: Computation Locks para Evitar Duplicação

**Pergunta:** "O Cache usa computation locks por chave. Qual é o objetivo?"

**Resposta Esperada:**

- Múltiplas threads podem pedir mesma agregação simultaneamente
- Sem computation lock: múltiplos cálculos redundantes
- Com computation lock: apenas 1 calcula, outros esperam
- Depois todas usam resultado do cache

**Exemplo:**

```java
private final Map<String, ReentrantLock> computationLocks = new HashMap<>();

String computationKey = produtoID + ":" + dia;  // Ex: "5:10"
ReentrantLock compLock = getComputationLock(computationKey);

compLock.lock();
try {
    // Só 1 thread por (produtoID:dia) calcula aqui
    Agregacao calculada = calcularComMemoria(produtoID, dia);

    // Armazena no cache
    writeLock.lock();
    try {
        cacheAgregacoes.computeIfAbsent(produtoID, k -> new HashMap<>())
                       .put(dia, calculada);
    } finally {
        writeLock.unlock();
    }
} finally {
    compLock.unlock();
}
```

**Cenário com múltiplos clients:**

```
Dia 5, ProdutoID 10, cache miss:

Thread 1: compLock("10:5").lock()  ← Adquire
  └─ Calcula agregação (demora 2s)
  └─ Armazena no cache
  └─ compLock.unlock()

Thread 2: compLock("10:5").lock()  ← Bloqueia enquanto T1 calcula!
  └─ Aguarda T1
  └─ Quando T1 termina: adquire lock
  └─ Verifica: já está no cache (T1 colocou lá)
  └─ Retorna do cache (2ª verificação no código)
  └─ compLock.unlock()

Thread 3: compLock("10:5").lock()  ← Também bloqueia
  └─ Igual ao Thread 2

Resultado:
- 1 cálculo para 3 threads ✅
- Sem duplicação de trabalho ✅
- Throughput: 3x melhor que sem lock
```

---

### Q63: Streaming vs Memória - Decisão Automática

**Pergunta:** "Como decide automaticamente se usa streaming ou carrega em memória?"

**Resposta Esperada:**

- Se série não está em memória E cache está cheio:
  - Usa streaming (RandomAccessFile.skip)
  - Lê apenas dados do produto necessário
  - Não aloca memória adicional
- Senão:
  - Carrega série em memória
  - Cálculo rápido em RAM
  - Próximas consultas usam cache

**Lógica:**

```java
boolean usarStreaming;
readLock.lock();
try {
    usarStreaming = seriesEmMemoria.size() >= S &&      // Cache cheio?
                    !seriesEmMemoria.containsKey(dia);  // Série não em memória?
} finally {
    readLock.unlock();
}

if (usarStreaming) {
    // RandomAccessFile: lê ficheiro, faz skip eficiente
    calculada = eventoRepository.agregarEventosDia(produtoID, dia);
    // Não aloca memória! Dados lidos e imediatamente descartados
} else {
    // Memória: carrega série inteira, cálculo rápido
    calculada = calcularComMemoria(produtoID, dia);
    // Série agora em memória para próximas consultas
}
```

**Performance Comparison:**

```
Cenário: 30 dias em disco, S=5, ProdutoID=7

Agregação(dias=7):

╔════════════════════════════════════════════╗
║ Com Streaming                              ║
├────────────────────────────────────────────┤
║ Dia 0-4: em memória (cache hits)          ║
║ Dia 5-6: streaming (RandomAccessFile)     ║
║ Memória usada: 5 * 10MB = 50 MB          ║
║ Tempo: ~500ms                             ║
║ I/O: ~2 MB (streaming skip)               ║
╚════════════════════════════════════════════╝

╔════════════════════════════════════════════╗
║ Sem Streaming (Load All)                  ║
├────────────────────────────────────────────┤
║ Dia 0-6: temos de carregar 7 em memória  ║
║ Problema: S=5, precisamos 7!             ║
║ Memória: 7 * 10MB = 70 MB                ║
║ LRU: remove 2 dias (complicado)          ║
║ Tempo: ~1000ms (mais carregamentos)       ║
║ I/O: ~70 MB (lê ficheiros inteiros)      ║
╚════════════════════════════════════════════╝

✅ Streaming: mais rápido, menos memória!
```

---

### Q64: Invalidação de Cache em NovoDia

**Pergunta:** "Por que é necessário invalidar todo o cache em novoDia()?"

**Resposta Esperada:**

- Agregações são calculadas com diaAtual como referência
- Quando novoDia() muda diaAtual, agregações ficam inválidas
- Ex: "últimos 7 dias" é diferente antes/depois de novoDia()
- Sem invalidar: clients receberiam dados errados

**Exemplo:**

```
Dia 1, ProdutoID 5, últimos 3 dias:
  Agregação: dias [0, 1, 2]
  Cache: agregacoes[5][0] = Agregacao(...)

novoDia() é chamado:
  diaAtual = 2

Dia 2, Client pede últimos 3 dias:
  Deveria ser: dias [-1, 0, 1]

Mas se cache não foi invalidado:
  Retorna: dias [0, 1, 2] ❌ ERRADO!

Solução: invalidarCache() em novoDia()
  Cache.clear()
  Cliente recalcula: dias [-1, 0, 1] ✅
```

**Código:**

```java
public void novoDia() {
    writeLock.lock();
    try {
        // 1. Persiste eventos do dia atual
        repository.salvarEventosDia(diaAtual);

        // 2. Limpa eventos em memória
        eventosDiaAtual.clear();

        // 3. Muda dia
        diaAtual++;

        // 4. ✅ CRÍTICO: Invalida cache
        cacheManager.invalidarCache();

        // 5. Notifica clients bloqueados
        condicaoNovoDia.signalAll();
    } finally {
        writeLock.unlock();
    }
}
```

---

## Questões de Comparação

### Q56: ThreadPool vs ExecutorService

**Pergunta:** "Diferenciem ThreadPool customizado vs ExecutorService padrão."

| Aspecto       | CustomThreadPool | ExecutorService          |
| ------------- | ---------------- | ------------------------ |
| Controlo      | Total            | Limitado                 |
| Dependências  | Nenhumas         | Framework Java           |
| Learning      | Alto             | Baixo                    |
| Rejeição      | Customizável     | RejectedExecutionHandler |
| Monitoramento | Customizado      | Padrão                   |

---

### Q57: ReentrantLock vs Synchronized

**Pergunta:** "Quando usariam cada um?"

| Situação       | ReentrantLock  | Synchronized    |
| -------------- | -------------- | --------------- |
| Leitura pesada | Read/WriteLock | Não recomendado |
| Trylock        | Sim            | Não             |
| Conditions     | Sim            | wait/notify     |
| Timeout        | Sim            | Não             |
| Reentrant      | Sim            | Sim             |

---

### Q58: Streaming vs Memória

**Pergunta:** "Trade-offs entre streaming (RandomAccessFile) e carregar em memória?"

| Aspecto        | Streaming     | Memória |
| -------------- | ------------- | ------- |
| Memória        | Baixa         | Alta    |
| Velocidade     | Lenta         | Rápida  |
| I/O            | Sim           | Não     |
| Escalabilidade | Melhor        | Pior    |
| Código         | Mais complexo | Simples |

---

## Sugestões para Respostas Fortes

1. **Sempre cite código**: "Veja em ThreadPoolImpl.java linha X..."
2. **Explique trade-offs**: "Escolhemos isto porque... alternativa seria..."
3. **Mencione padrões**: "Usamos Factory Pattern aqui porque..."
4. **Cite razões de performance**: "Isto melhora performance porque..."
5. **Reconheça limitações**: "Com mais tempo, faríamos..."
6. **Dê exemplos concretos**: "Considere o cenário onde 100 clients..."
7. **Relate ao enunciado**: "O enunciado pedia X, implementamos Y porque..."
8. **Mostre números**: "Cache hit rate atingiu 80% em testes"

---

## Checklist para Defesa

### Conhecimentos Base

- [ ] Threads, Locks, Conditions: conceitos e código
- [ ] ThreadPool: criação, fila, workers, shutdown
- [ ] Demultiplexer: tags, mapa de respostas, Conditions
- [ ] Cache LRU: estrutura de dados, eviction policy
- [ ] Persistência: ficheiros binários, RandomAccessFile, seek/skip
- [ ] Stubs/Skeleton: padrão RPC, serialização, type-safe

### Sobre Enunciado

- [ ] Memorizar D (dias), S (séries em memória)
- [ ] Requisitos obrigatórios: 1 conexão/client, protocolo binário, etc
- [ ] 6 funcionalidades: autenticação, registo, agregações, filtro, notificações, multi-threaded
- [ ] Persistência: D dias, máximo S em memória
- [ ] Validações: campos, tipos, ranges
- [ ] Testes: escalabilidade, robustez

### Cenários Práticos

- [ ] Simular 100 clients registando eventos
- [ ] Simular agregação com cache miss
- [ ] Simular novo dia com múltiplos clients a interagir
- [ ] Simular cliente que não consome respostas
- [ ] Simular notificação bloqueante

### Diagramas Prontos

- [ ] Arquitetura (Cliente → Middleware → Demultiplexer vs Server → ClientHandler → Dispatcher)
- [ ] Fluxo de registo de evento (passo a passo)
- [ ] Fluxo de agregação (cache hit vs miss)
- [ ] Fluxo novoDia (sincronização)

### Código na Ponta

- [ ] ThreadPoolImpl: linha de queue, workers, await/signal
- [ ] CacheManager: LRU eviction, computation locks
- [ ] EventoFileRepository: formato binário, skip
- [ ] Demultiplexer: aguardar(tag), entregarResposta(tag, data)
- [ ] RequestDispatcher: despachar(message)

### Métricas & Resultados

- [ ] Throughput máximo atingido (req/s)
- [ ] Cache hit rate típico (%)
- [ ] Latência média (ms)
- [ ] Escalabilidade até quantos clients
- [ ] Comportamento em robustez

### Preparação Final

- [ ] Conhecer limitações do projeto
- [ ] Preparar sugestões de melhoria (BD, replicação, TLS)
- [ ] Ter analogias simples para conceitos complexos
- [ ] Praticar explicação concisa (resposta < 2 min)
- [ ] Estar confortável com perguntas "e se..."
