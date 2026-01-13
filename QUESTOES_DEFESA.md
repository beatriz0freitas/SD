# Questões Extensivas para Defesa - Projeto SD

## Índice
1. [Questões sobre Threads e Concorrência](#threads-concorrência)
2. [Questões sobre Comunicação e Protocolo](#comunicação-protocolo)
3. [Questões sobre Arquitetura e Design](#arquitetura-design)
4. [Questões sobre Performance e Cache](#performance-cache)
5. [Questões sobre Persistência](#persistência)
6. [Questões de Implementação](#implementação)
7. [Questões de Decisões Técnicas](#decisões-técnicas)

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

## Questões de Comparação

### Q41: ThreadPool vs ExecutorService
**Pergunta:** "Diferenciem ThreadPool customizado vs ExecutorService padrão."

| Aspecto | CustomThreadPool | ExecutorService |
|---------|-----------------|-----------------|
| Controlo | Total | Limitado |
| Dependências | Nenhumas | Framework Java |
| Learning | Alto | Baixo |
| Rejeição | Customizável | RejectedExecutionHandler |
| Monitoramento | Customizado | Padrão |

---

### Q42: ReentrantLock vs Synchronized
**Pergunta:** "Quando usariam cada um?"

| Situação | ReentrantLock | Synchronized |
|----------|--------------|--------------|
| Leitura pesada | Read/WriteLock | Não recomendado |
| Trylock | Sim | Não |
| Conditions | Sim | wait/notify |
| Timeout | Sim | Não |
| Reentrant | Sim | Sim |

---

### Q43: Streaming vs Memória
**Pergunta:** "Trade-offs entre streaming (RandomAccessFile) e carregar em memória?"

| Aspecto | Streaming | Memória |
|---------|-----------|---------|
| Memória | Baixa | Alta |
| Velocidade | Lenta | Rápida |
| I/O | Sim | Não |
| Escalabilidade | Melhor | Pior |
| Código | Mais complexo | Simples |

---

## Sugestões para Respostas Fortes

1. **Sempre cite código**: "Veja em ThreadPoolImpl.java linha X..."
2. **Explique trade-offs**: "Escolhemos isto porque... alternativa seria..."
3. **Mencione padrões**: "Usamos Factory Pattern aqui porque..."
4. **Cite razões de performance**: "Isto melhora performance porque..."
5. **Reconheça limitações**: "Com mais tempo, faríamos..."
6. **Dê exemplos concretos**: "Considere o cenário onde 100 clients..."

---

## Checklist para Defesa

- [ ] Preparar exemplos de código na ponta da língua
- [ ] Praticar diagramas de fluxo no quadro
- [ ] Ter métricas de testes prontas
- [ ] Conhecer código de cor
- [ ] Preparar respostas para críticas
- [ ] Praticar explicações concisas
- [ ] Ter comparações com alternativas
- [ ] Conhecer limitações e melhorias futuras

