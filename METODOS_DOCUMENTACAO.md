# Documentação Detalhada dos Métodos - Guia Intuitivo

## 📚 Índice Rápido

1. [ClienteMiddleware](#cliente-middleware) - Comunicação do cliente
2. [Demultiplexer](#demultiplexer) - Correlação de respostas
3. [Server](#servidor) - Aceitar e gerir clientes
4. [ThreadPoolImpl](#threadpool) - Pool de threads customizado
5. [ServicoEventos](#servico-eventos) - Gestão de eventos
6. [ServicoAgregacoes](#servico-agregacoes) - Cálculos de agregações
7. [CacheManager](#cache-manager) - Cache LRU

---

## Cliente Middleware

**Ficheiro:** `src/main/java/client/ClienteMiddleware.java`

### O que faz?

Middleware que abstrai a comunicação remota. Cliente não precisa conhecer detalhes de serialização, tags, ou threads de demultiplexing.

### Principais Métodos

#### `void conectar()`

```
Responsabilidade: Estabelecer conexão ao servidor

Fluxo:
1. Lock na escrita (evita race conditions)
2. Se já conectado, retorna
3. Se usePool=false:
   ├─ Cria PooledConnection (dedicada)
   ├─ Cria Demultiplexer (thread que lê respostas)
   ├─ Inicia thread Demultiplexer em background
   └─ Marca conectado=true
4. Senão (usePool=true):
   └─ Usa ConnectionPool (múltiplas conexões)

Thread safety: lockEscrita protege state

Complexidade: O(1)
```

#### `RespostaDTO enviar(byte serviceId, byte methodId, Object parametros)`

```
Responsabilidade: Enviar request e aguardar resposta

Fluxo:
1. Gera TAG único (++contadorPedidos, thread-safe)
   └─ Ex: request 1 tem tag=1, request 2 tem tag=2
   └─ Permite múltiplos requests simultâneos!

2. Serializa parametros em DTO
   └─ EventoDTO.serialize() → byte[]

3. Cria Message com:
   ├─ tag (único)
   ├─ serviceId (qual serviço: AUTENTICACAO, EVENTOS, etc)
   ├─ methodId (qual método no serviço)
   └─ payload (parametros serializados)

4. Envia ao servidor (com lockEscrita)
   └─ Garante que multiplas threads não escrevem simultâneas

5. BLOQUEIA em demux.aguardar(tag)
   └─ Thread bloqueia aqui até resposta chegar
   └─ Demultiplexer acorda quando resposta está pronta

6. Desserializa resposta
   └─ RespostaDTO.deserialize(byte[]) → resultado

7. Retorna resultado ou lança exceção

Exemplo Timeline:
---------
T0: Thread1 chama enviar(...) → tag=1
T1: Thread2 chama enviar(...) → tag=2
T2: Servidor processa tag=1, envia resposta
T3: Demultiplexer recebe resposta tag=1
T4: Demultiplexer acorda Thread1
T5: Thread1 retorna com resposta 1
T6: Servidor processa tag=2, envia resposta
T7: Demultiplexer recebe resposta tag=2
T8: Demultiplexer acorda Thread2
T9: Thread2 retorna com resposta 2
---------

Note: Respostas podem chegar fora de ordem!
      Demultiplexer garante correlação correta.

Complexidade: O(1) no cliente (blocking I/O no servidor)
```

#### `long nextTag()`

```
Responsabilidade: Gerar tag único thread-safe

Implementação:
lock (lockTags)
  contadorPedidos++
  return contadorPedidos
unlock

Por quê lock?
- Sem lock: 2 threads fazem ++contadorPedidos
  Thread1: lê 5, incrementa 6 → escreve 6
  Thread2: lê 5, incrementa 6 → escreve 6
  └─ PROBLEMA: 2 requests com tag=6!

- Com lock: apenas 1 thread por vez
  Thread1: lê 5, incrementa 6 → escreve 6 (lock held)
  Thread2: aguarda lock
  Thread2: lê 6, incrementa 7 → escreve 7 (lock released)
  └─ OK: tags únicos!

Complexidade: O(1)
```

---

## Demultiplexer

**Ficheiro:** `src/main/java/client/Demultiplexer.java`

### O que faz?

Thread de fundo que **lê respostas do servidor em loop** e as **distribui para threads cliente** que aguardam (por tag).

Problema que resolve:

- Cliente envia 3 requests rapidamente com tags 1, 2, 3
- Servidor responde fora de ordem: 3, 1, 2
- Como saber qual resposta é para qual request?
- Solução: usar tags para correlacionar!

### Principais Métodos

#### `void run()`

```
Responsabilidade: Loop infinito de leitura de respostas

Fluxo:
while (servidor ainda ativo):
    1. protocolo.receber(entrada)
       └─ BLOQUEIA até receber Message completa
       └─ Desbloqueia quando chega Message
       └─ Importante: 1 thread dedicada apenas para isto

    2. if (msg.tag == -1):
       └─ Sinal especial: servidor a encerrar
       └─ Acorda TODAS threads à espera
       └─ Marca serverShutdown=true
       └─ Sai do loop

    3. Senão: entregarResposta(msg.tag, msg.payload)
       └─ Processa resposta normal
       └─ Ver método entregarResposta()

Exceções:
- IOException: conexão perdida
  └─ Chama tratarErroConexao()
  └─ Notifica TODAS threads à espera
  └─ Sai do loop

Timeline (exemplo com 2 requests):
---------
T0: Demux aguarda em protocolo.receber()
T1: Servidor envia resposta tag=1
T2: Demux acorda, processa tag=1
T3: Demux aguarda em protocolo.receber() novamente
T4: Demux entrega resposta para Thread1
T5: Thread1 desbloqueia, retorna
T6: Servidor envia resposta tag=2
T7: Demux acorda, processa tag=2
T8: Demux entrega resposta para Thread2
T9: Thread2 desbloqueia, retorna
---------

Por quê thread dedicada?
- Não pode ser no mesmo thread que envia requests
  (bloquearia enquanto aguarda resposta)
- Demux é assíncrono: pode receber respostas
  enquanto cliente está enviando novos requests

Complexidade: O(tamanho_message) por iteração
```

#### `byte[] aguardar(long tag)`

```
Responsabilidade: Cliente BLOQUEIA aqui até resposta chegar

Fluxo:
1. Lock adquirido (proteção contra race conditions)

2. if (resposta já existe no mapa):
   └─ Devolve imediatamente (fast path)
   └─ respostas.remove(tag) → recolhe resposta

3. Senão (resposta ainda não chegou):
   ├─ Cria Condition nova
   ├─ Armazena em threadsEspera[tag] = condition
   │
   └─ while (resposta não chegou):
      └─ condition.await()  ← BLOQUEIA AQUI
         └─ Libertina CPU
         └─ Acorda quando signal() é chamado
         └─ Checks de novo: é a resposta dele?

   ├─ Resposta chegou!
   └─ Recolhe do mapa e retorna

Cenário: 2 threads simultâneos
---------
Thread A:
  enviar(tag=1)
    → aguardar(1)
       → lock adquirido
       → resposta não existe
       → Condition cond_A = lock.newCondition()
       → threadsEspera[1] = cond_A
       → cond_A.await()  ← BLOQUEIA AQUI

Thread B:
  enviar(tag=2)
    → aguardar(2)
       → lock adquirido (espera enquanto A tem)
       → resposta não existe
       → Condition cond_B = lock.newCondition()
       → threadsEspera[2] = cond_B
       → cond_B.await()  ← BLOQUEIA AQUI

Demultiplexer thread (background):
  run()
    → protocolo.receber() ← RECEBE resposta tag=2
    → entregarResposta(2, payload)
       → lock adquirido
       → respostas[2] = payload
       → cond_B = threadsEspera[2]
       → cond_B.signal()  ← ACORDA Thread B
       → unlock

    → protocolo.receber() ← RECEBE resposta tag=1
    → entregarResposta(1, payload)
       → lock adquirido
       → respostas[1] = payload
       → cond_A = threadsEspera[1]
       → cond_A.signal()  ← ACORDA Thread A
       → unlock

Thread B (continua):
  cond_B.signal() acordou Thread B!
    → while verifica: resposta[2] existe? SIM
    → retorna resposta[2]

Thread A (continua):
  cond_A.signal() acordou Thread A!
    → while verifica: resposta[1] existe? SIM
    → retorna resposta[1]
---------

Notar:
- Demultiplexer entregou resposta 2 primeiro
- Mas ambas threads desbloqueiam corretamente
- Sem confusão! (Graças aos tags)

Complexidade: O(1) se resposta já existe, O(blocking) senão
```

#### `void entregarResposta(long tag, byte[] payload)`

```
Responsabilidade: Chamado quando resposta chega

Fluxo (rápido):
1. Lock adquirido
2. respostas[tag] = payload
   └─ Armazena resposta no mapa
3. Cond cond = threadsEspera[tag]
   └─ Encontra Condition da thread que aguarda
4. if (cond != null):
   └─ cond.signal()
      └─ Acorda APENAS 1 thread
      └─ (Outras threads ainda dormem)
5. Unlock

Por quê signal() e não signalAll()?
- signal() acorda apenas 1 thread específica
  (aquela que aguarda Este tag)
- Mais eficiente: não acorda threads desnecessárias
- signalAll() acordaria TODAS threads, desperdício

Complexidade: O(1)
```

---

## Servidor

**Ficheiro:** `src/main/java/server/Server.java`

### O que faz?

Ponto de entrada do servidor. Aceita clientes, cria handlers, coordena pools de threads.

### Principais Métodos

#### `void iniciar()`

```
Responsabilidade: Loop principal do servidor

Fluxo:
1. Cria ServerSocket na porta 5001
   └─ serverSocket.accept() bloqueia até cliente conectar

2. Marca ativo=true

3. Inicia DeadlockMonitor
   └─ Verifica deadlocks a cada 30 segundos

4. While (servidor ativo):
       1. ServerSocket.accept()  ← BLOQUEIA aqui
          └─ Aguarda cliente conectar
          └─ Retorna Socket do cliente

       2. adicionarCliente(socket)
          └─ Armazena em Set<Socket> clientesAtivos
          └─ Protegido por clientesLock (thread-safe)

       3. Cria ClientHandler
          └─ Responsável por ler requests deste cliente
          └─ Será executado em thread do pool

       4. clientHandlerPool.submit(handler)
          └─ Submete para ser executado
          └─ Pool tem 20 threads (N_CLIENT_HANDLERS)
          └─ Se pool cheio: retorna false

       5. Se submit falhou:
          └─ Fecha socket
          └─ Remove cliente

Exemplo Timeline (3 clientes conectam):
---------
T0: accept() aguarda
T1: Cliente 1 conecta
    → Cria ClientHandler1
    → submit(ClientHandler1) → worker thread A
T2: accept() aguarda de novo
T3: Cliente 2 conecta
    → Cria ClientHandler2
    → submit(ClientHandler2) → worker thread B
T4: accept() aguarda de novo
T5: Cliente 3 conecta
    → Cria ClientHandler3
    → submit(ClientHandler3) → worker thread C
T6: accept() aguarda de novo (enquanto A, B, C processam clientes)
---------

Estrutura de threads no servidor:
┌─ Main thread (Server.iniciar)
│  └─ accept() aguarda clientes
│
├─ DeadlockMonitor thread (daemon)
│  └─ Verifica deadlocks a cada 30s
│
├─ clientHandlerPool (20 threads)
│  ├─ Worker A: lê requests do Cliente 1
│  ├─ Worker B: lê requests do Cliente 2
│  └─ Worker C: lê requests do Cliente 3
│
└─ requestPool (50 threads)
   ├─ Worker 1: processa request de Cliente 1
   ├─ Worker 2: processa request de Cliente 2
   └─ ...

Complexidade: O(1) por cliente (blocking I/O)
```

#### `void parar()`

```
Responsabilidade: Shutdown gracioso do servidor

Fluxo:
1. Marca ativo=false
2. Fecha ServerSocket
   └─ accept() vai lançar exceção
   └─ Loop principal termina
3. Parar DeadlockMonitor
4. Shutdown gracioso dos pools
   ├─ clientHandlerPool.shutdown()
   │  └─ Rejeita novos tasks
   │  └─ Processa tarefas pendentes
   │  └─ Workers terminam
   │
   └─ requestPool.shutdown()
      └─ Similar ao handler pool
5. Aguarda até terminar
   └─ clientHandlerPool.awaitTermination()
   └─ requestPool.awaitTermination()
   └─ Bloqueia até todos threads morrerem

Tempo estimado: 1-5 segundos (depende de requests pendentes)

Complexidade: O(N) onde N = número de requests pendentes
```

---

## ThreadPoolImpl

**Ficheiro:** `src/main/java/common/concurrency/ThreadPoolImpl.java`

### O que faz?

Pool reutilizável de threads. Evita overhead de criar/destruir threads.

### Principais Métodos

#### `boolean submit(Runnable task)`

```
Responsabilidade: Adicionar tarefa ao pool

Fluxo:
1. Lock adquirido (proteção de estado)

2. Validações:
   ├─ if (shutdown || shutdownNow): return false
   │  └─ Pool já está encerrado, rejeita
   │
   └─ if (fila cheia): return false
      └─ taskQueue.size() >= maxQueueSize
      └─ Backpressure: força cliente a aguardar
      └─ Evita memory leak (fila infinita)

3. taskQueue.add(task)
   └─ Adiciona à fila

4. if (workerCount < maxThreads):
   └─ startWorker()
   └─ Cria nova thread worker se necessário
   └─ Ex: 5 tasks + 0 workers → cria 1 worker
   └─ Ex: 20 tasks + 10 workers → cria 10 workers

5. notEmpty.signal()
   └─ Acorda 1 worker ocioso
   └─ "Há tarefas na fila!"

6. Unlock
7. return true

Cenário: progressão de 5 tasks
---------
Task 1: submit(task1)
  → queue vazia, 0 workers
  → add(task1)
  → startWorker() → Worker A criado
  → signal() acorda Worker A
  → return true

Task 2: submit(task2)
  → queue: [task1 (A está executando)]
  → add(task2)
  → workerCount=1, maxThreads=20: startWorker() → Worker B
  → signal() acorda Worker B
  → return true

Task 3: submit(task3)
  → queue: [task2 (A e B executando)]
  → add(task3)
  → startWorker() → Worker C
  → return true

Task 4: submit(task4)
  → queue: [task2, task3, task4]
  → add(task4)
  → workerCount=3, pode criar mais
  → startWorker() → Worker D
  → return true

Task 5: submit(task5)
  → queue: [task3, task4, task5]
  → add(task5)
  → startWorker() → Worker E
  → return true

Resultado: 5 workers executando tasks em paralelo

Complexidade: O(1)
```

#### `void runWorkerLoop()`

```
Responsabilidade: Thread worker executa tasks em loop

Fluxo:
while (servidor ativo):
    1. Lock adquirido

    2. while (fila vazia && !shutdown):
       └─ notEmpty.await()  ← BLOQUEIA AQUI
          └─ Worker dorme (liberta CPU)
          └─ Acorda quando signal()

    3. Validações:
       ├─ if (shutdownNow): return
       │  └─ Shutdown forçado, sai imediatamente
       │
       └─ if (fila vazia): return
          └─ Shutdown gracioso, sem tarefas

    4. task = taskQueue.poll()
       └─ Remove 1 tarefa da fila

    5. Unlock  ← IMPORTANTE! Executa SEM lock

    6. task.run()
       └─ Executa tarefa (pode ser lenta!)
       └─ SEM lock: outras threads podem submit/shutdown

    7. Volta ao loop

Finalmente:
    finalizarWorker()
    └─ workerCount--
    └─ workers.remove(current thread)
    └─ if (workerCount == 0): termination.signalAll()
       └─ "Pool totalmente vazio"

Timeline (Worker A com 3 tasks):
---------
T0: Worker A iniciado
T1: Aguarda notEmpty
T2: Task 1 adicionado, Worker A acordado
T3: Worker A executa Task 1 (2ms)
T4: Worker A volta ao loop, aguarda notEmpty
T5: Task 2 adicionado, Worker A acordado
T6: Worker A executa Task 2 (5ms)
T7: Worker A volta ao loop, aguarda notEmpty
T8: Task 3 adicionado, Worker A acordado
T9: Worker A executa Task 3 (3ms)
T10: Worker A volta ao loop, fila vazia
T11: shutdown() chamado
T12: Worker A acorda de await (shutdown=true)
T13: Worker A sai do loop
T14: finalizarWorker(): workerCount=0
T15: Acorda awaitTermination()
---------

Por quê unlock antes de task.run()?
- Task pode ser lenta (1s)
- Se holded lock: outras threads ficariam bloqueadas
- Resultado: "congelamento" aparente
- Solução: unlock antes de executar

Complexidade: O(execution_time_of_task)
```

#### `void shutdown()`

```
Responsabilidade: Gracioso shutdown

Fluxo:
1. Lock adquirido
2. shutdown = true
   └─ Marca para parar
3. notEmpty.signalAll()
   └─ Acorda TODAS workers
   └─ Para que verifiquem shutdown flag
4. if (workerCount == 0):
   └─ termination.signalAll()
   └─ Todos workers já terminaram
5. Unlock

Resultado:
- Rejeita novos tasks
- Processa tarefas pendentes
- Workers terminam naturalmente

Tempo: até que fila fique vazia
```

---

## ServicoEventos

**Ficheiro:** `src/main/java/server/business/services/ServicoEventos.java`

### O que faz?

Gestão de eventos de vendas. Registra eventos, lista, filtra, e gerencia transição entre dias.

### Principais Métodos

#### `RespostaDTO registrarEvento(EventoDTO dto)`

```
Responsabilidade: Registar evento de venda

Fluxo:
1. validarEvento(dto)
   ├─ Verifica produtoID > 0
   ├─ Verifica quantidade > 0
   └─ Verifica preço > 0

2. Cria Evento(produtoID, quantidade, preço)

3. lock.writeLock().lock()  ← ESCRITA EXCLUSIVA
   └─ Nenhuma outra thread pode ler ou escrever

4. eventosDiaAtual.computeIfAbsent(produtoID, k → nova lista)
   └─ Pega lista de eventos deste produto
   └─ Se não existe, cria lista nova

5. lista.add(evento)
   └─ Adiciona evento à lista do produto

6. Atualiza notificação:
   ├─ if (lastProductID == produtoID):
   │  └─ consecutiveCount++
   │     └─ Evento consecutivo do mesmo produto
   │
   └─ Senão:
      ├─ lastProductID = produtoID
      └─ consecutiveCount = 1

7. notificationManager.notificarEvento(...)
   └─ Verifica se alguma notificação foi satisfeita
   └─ Exemplo: notificação "3 eventos do produto X"

8. lock.writeLock().unlock()

9. Log do evento

10. return RespostaDTO.sucesso()

Estrutura de dados (em memória):
┌─ eventosDiaAtual
│  ├─ produto 1 → [evento, evento, evento]
│  ├─ produto 2 → [evento, evento]
│  └─ produto 3 → [evento]
│
└─ Dia = 1
   Quando novoDia() → dia = 2

Exemplo (3 eventos):
---------
registrarEvento(produto=1, qtd=10, preço=5€)
  → eventosDiaAtual[1] = [Evento(1, 10, 5€)]
  → lastProductID=1, consecutiveCount=1

registrarEvento(produto=1, qtd=20, preço=5€)
  → eventosDiaAtual[1] = [Evento(1, 10, 5€), Evento(1, 20, 5€)]
  → lastProductID=1, consecutiveCount=2

registrarEvento(produto=2, qtd=30, preço=3€)
  → eventosDiaAtual[2] = [Evento(2, 30, 3€)]
  → lastProductID=2, consecutiveCount=1
---------

Thread safety:
- writeLock: garante exclusividade
- Sem writeLock: 2 threads poderiam incrementar contador simultâneos
  Thread A: lê consecutiveCount=1, incrementa 2, escreve 2
  Thread B: lê consecutiveCount=1, incrementa 2, escreve 2 (ERRO!)

Complexidade: O(1) para add, O(D) para notificações
```

#### `RespostaDTO novoDia()`

```
Responsabilidade: Transição para novo dia

Fluxo:
1. lock.writeLock().lock()  ← ESCRITA EXCLUSIVA
   └─ Ninguém pode registar eventos durante isto

2. eventoRepository.salvarEventosDia(diaAtual, eventosDiaAtual)
   └─ Persiste eventos atuais ao ficheiro
   └─ Ficheiro: dados/eventos/eventos_dia_N.dat
   └─ Formato binário ordenado por produtoID

3. eventosDiaAtual.clear()
   └─ Limpa memória
   └─ Memória em RAM é "efêmera" por dia

4. diaAtual++
   └─ Incrementa dia para próximo

5. cacheManager.invalidarCache()
   └─ Limpa cache de agregações
   └─ Por quê? Agregações antigas não fazem sentido
   └─ Dia novo = contexto novo

6. lastProductID = -1
   └─ Reset do contador de consecutivos

7. lock.writeLock().unlock()

8. Log de mudança de dia

9. return RespostaDTO.sucesso()

Timeline (servidor em produção):
---------
Dia 1:
  ├─ 10:00: registrarEvento(produto 1)
  ├─ 10:05: registrarEvento(produto 2)
  ├─ 10:10: registrarEvento(produto 1)
  └─ Eventos: {1: [ev, ev], 2: [ev]}

  22:00: novoDia()
    → Salva eventos_dia_1.dat
    → Limpa memória
    → diaAtual = 2

Dia 2 (novo dia começa):
  ├─ 00:00: eventos_dia_atual = {} (vazio)
  ├─ 10:00: registrarEvento(produto 3)
  └─ Eventos: {3: [ev]}

Persistência:
  ├─ eventos_dia_1.dat: contém eventos do dia 1
  ├─ eventos_dia_2.dat: contém eventos do dia 2
  └─ etc...

Agregações:
  Para calcular "últimos 7 dias":
  ├─ Dia 1: ler eventos_dia_1.dat (arquivo)
  ├─ Dia 2: ler eventos_dia_2.dat (arquivo)
  ├─ Dia 3: ler eventos_dia_3.dat (arquivo)
  └─ etc...

Complexidade: O(N) onde N = número de eventos do dia
```

---

## ServicoAgregacoes

**Ficheiro:** `src/main/java/server/business/services/ServicoAgregacoes.java`

### O que faz?

Calcula agregações: quantidade, volume, preço médio, preço máximo.

### Principais Métodos

#### `RespostaDTO obterQuantidadeVendas(int produtoID, int dias)`

```
Responsabilidade: Retorna quantidade de vendas dos últimos N dias

Fluxo:
1. validarParametros(produtoID, dias)
   ├─ produtoID > 0? SIM
   ├─ dias > 0? SIM
   └─ dias <= D (máximo do servidor)? SIM
      └─ Se falhar: lança AgregacaoException

2. calcularAgregacao(produtoID, dias)
   └─ Ver método calcularAgregacao()

3. Formata resultado
   └─ "Quantidade de Vendas nos últimos 7 dias: 450"

4. return RespostaDTO.sucesso(mensagem)

Exemplo (cliente pede últimas 7 dias):
---------
Dia atual = 10
Pedido: obterQuantidadeVendas(produtoID=5, dias=7)

Valida:
  ├─ produtoID=5 > 0? SIM
  ├─ dias=7 > 0? SIM
  └─ dias=7 <= D=30? SIM

Calcula:
  └─ Agregação dos dias 4 a 10 (7 dias)

Responde:
  └─ "Quantidade: 450 unidades"
---------

Complexidade: O(dias) ou O(log dias) com cache
```

#### `Agregacao calcularAgregacao(int produtoID, int dias)`

```
Responsabilidade: Calcular agregação para intervalo de dias

Fluxo:
1. Calcula intervalo de dias:
   ultimoDia = eventoRepository.obterUltimoDia()
   diasReais = min(dias, ultimoDia + 1)
   diaInicio = ultimoDia - diasReais + 1
   diaFim = ultimoDia

2. ESTRATÉGIA ADAPTATIVA:

   Passo 1: Tenta obter do cache (rápido)
   ├─ Para cada dia no intervalo:
   │  └─ tentarCache(produtoID, dia)
   │     └─ Se existe no cache: OK
   │     └─ Se não existe: conta como "miss"
   │
   └─ Conta diasNaoCache

   Passo 2: Decisão
   ├─ if (diasNaoCache > 5):
   │  └─ Muitos misses!
   │  └─ Usa STREAMING (lê ficheiros direto)
   │  └─ Mais eficiente que calcular dia-a-dia
   │
   └─ Senão:
      └─ Poucos misses
      └─ Usa CACHE para cada dia
      └─ Preenche cache conforme calcula

Exemplo (7 dias solicitados):
---------
Cenário A: Cache com muitos hits
  ├─ Dia 1: HIT (cache tem)
  ├─ Dia 2: HIT
  ├─ Dia 3: MISS (primeiro miss)
  ├─ Dia 4: HIT
  ├─ Dia 5: HIT
  ├─ Dia 6: HIT
  └─ Dia 7: HIT

  diasNaoCache = 1 (< 5)
  → Usa CACHE para todos
  → Calcula Dia 3 (miss), armazena no cache
  → Tempo: ~5ms (5 cache hits + 1 I/O hit)

Cenário B: Cache com muitos misses
  ├─ Dia 1: MISS
  ├─ Dia 2: MISS
  ├─ Dia 3: MISS
  ├─ Dia 4: MISS
  ├─ Dia 5: MISS
  ├─ Dia 6: MISS
  └─ Dia 7: MISS

  diasNaoCache = 7 (> 5)
  → Usa STREAMING para todo o intervalo
  → 1 única leitura de ficheiro (lê dias 1-7)
  → Tempo: ~20ms (1 I/O grande)

  Nota: STREAMING é mais rápido que 7 I/Os individuais!
---------

Por quê estratégia adaptativa?
- Cache fast path: se maioria em cache, usa cache
- Streaming fast path: se maioria missing, lê tudo de uma vez
- Evita pior cenário: 7 misses = 7 I/O operations

Complexidade:
  - Cache hits: O(hits) = O(1-5ms)
  - Streaming: O(dias) = O(20ms) para 7 dias
  - Bem melhor que O(7 * 100ms) = 700ms sem adaptação!
```

#### `private Agregacao tentarCache(int produtoID, int dia)`

```
Responsabilidade: Tentar obter do cache sem calcular

Implementação:
  return cacheManager.getAgregacaoSeExistir(produtoID, dia)

Por quê "Se Existir"?
- NÃO calcula se não existe
- Apenas lê se já está em cache
- Usado para contar misses na estratégia adaptativa

Complexidade: O(1)
```

---

## CacheManager

**Ficheiro:** `src/main/java/server/data/cache/CacheManager.java`

### O que faz?

Cache de agregações com LRU. Armazena até S séries em memória. Quando S+1, remove a menos recentemente usada.

### Principais Métodos

#### `Agregacao obterAgregacaoDia(int produtoID, int dia)`

```
Responsabilidade: Obter agregação com cache automático

Arquitetura LRU:
┌─ cacheAgregacoes: Map<produtoID, Map<dia, Agregacao>>
│  └─ Armazena agregações já calculadas
│
├─ seriesEmMemoria: Map<dia, Map<produtoID, List<Evento>>>
│  └─ Armazena até S séries completas em memória
│  └─ "Série" = todos os eventos de um dia para todos produtos
│
├─ ordemAcesso: List<dia>
│  └─ Ordem de acesso dos dias (para LRU)
│  └─ Ex: [5, 3, 7, 1] significa dia 5 é o mais recente
│
└─ computationLocks: Map<"produtoID:dia", ReentrantLock>
   └─ Evita múltiplos cálculos simultâneos da mesma agregação
   └─ "Double-computation prevention"

Fluxo:

PASSO 1: READ-LOCK (verificação rápida)
---------
readLock.lock()
  if (cacheAgregacoes[produtoID][dia] existe):
    → metrics.recordCacheHit()  ← CACHE HIT!
    → return agregacao (rápido!)
readLock.unlock()

PASSO 2: CACHE MISS (precisa calcular)
---------
metrics.recordCacheMiss()

Obtém computation lock para este (produtoID, dia):
  compLock = getComputationLock("5:7")
  compLock.lock()
    → Apenas 1 thread calcula esta combinação

PASSO 3: DOUBLE-CHECK (verificação com write lock)
---------
readLock.lock()
  if (cacheAgregacoes[5][7] agora existe):
    → Outra thread já calculou!
    → return agregacao (rápido!)
readLock.unlock()

PASSO 4: DECISÃO DE STRATEGY (streaming vs memória)
---------
readLock.lock()
  usarStreaming = (seriesEmMemoria.size() >= S) &&
                  (!seriesEmMemoria.containsKey(dia))
readLock.unlock()

STRATEGY:
  A. Se série ESTÁ em memória:
     ├─ Calcula rápido em RAM
     ├─ Procura Evento de produto 5 no dia 7
     └─ Tempo: ~1ms (memória é rápida)

  B. Se série NÃO está em memória e cache está cheio:
     ├─ Usa STREAMING (RandomAccessFile.skip)
     ├─ Lê ficheiro eventos_dia_7.dat
     ├─ Skip produtos 1-4, lê produto 5
     └─ Tempo: ~10-50ms (I/O mas eficiente)

  C. Se série NÃO está em memória e cache não está cheio:
     ├─ Carrega série em memória
     ├─ Para próximas consultas: cache rápido!
     └─ Tempo: ~100ms (I/O + memória) primeira vez, ~1ms depois

PASSO 5: CALCULAR E ARMAZENAR
---------
calculada = calcularComMemoria(5, 7) OU
            eventoRepository.agregarEventosDia(5, 7)

writeLock.lock()
  cacheAgregacoes[5][7] = calculada  ← Armazena

  if (seriesEmMemoria.size() >= S):
    → removeNaoUsada()
      └─ Remove dia menos recentemente usado
      └─ LRU = Least Recently Used
writeLock.unlock()

compLock.unlock()

PASSO 6: RETORNA
---------
return calculada

Timeline (exemplo com 2 clientes, cache com S=2):
---------
T0: Cliente A pede agregação(5, 7) [miss]
    ├─ Calcula, armazena
    ├─ Dias em memória: [7]
    └─ seriesEmMemoria.size() = 1

T1: Cliente B pede agregação(5, 7) [hit]
    ├─ Encontra no cache
    ├─ return imediatamente (~1ms)
    └─ ordemAcesso atualizado: [7, 7] (mais recente à frente)

T2: Cliente A pede agregação(3, 5) [miss]
    ├─ Calcula, armazena
    ├─ Dias em memória: [7, 5]
    └─ seriesEmMemoria.size() = 2 (CHEIO!)

T3: Cliente B pede agregação(2, 3) [miss]
    ├─ seriesEmMemoria.size() = 2 >= S
    ├─ removeNaoUsada() → remove dia 5 (menos recente)
    ├─ Carrega dia 3 em memória
    ├─ Dias em memória: [7, 3]
    └─ seriesEmMemoria.size() = 2 (ainda CHEIO)

T4: Cliente A pede agregação(5, 7) [hit]
    ├─ Encontra no cache
    ├─ return imediatamente
    └─ ordemAcesso atualizado: [3, 7] (7 agora mais recente)

T5: Cliente B pede agregação(5, 5) [miss]
    ├─ Dia 5 foi removido da memória
    ├─ seriesEmMemoria não tem dia 5
    ├─ Usa STREAMING (RandomAccessFile)
    ├─ Lê eventos_dia_5.dat direto
    └─ Não armazena em memória (cache cheio)

Notar:
- Memória limitada a S=2 dias
- Dias "quentes" (muito acesso) ficam em memória
- Dias "frios" (pouco acesso) são removidos
- STREAMING eficiente para dias removidos
---------

Complexidade:
- Cache HIT: O(1) ~1-5ms
- Cache MISS com memória: O(tamanho_serie) ~100-500ms
- Cache MISS com streaming: O(skip_size) ~10-50ms
```

#### `ReentrantLock getComputationLock(String key)`

```
Responsabilidade: Obter lock por chave (lazy creation)

Problema que resolve:
- Aggregation(5, 7) é custosa (~100ms)
- 2 threads solicitam ao mesmo tempo
- Sem computation lock:
  ├─ Thread A: começa a calcular
  ├─ Thread B: também começa a calcular (em paralelo!)
  └─ Resultado: 2 cálculos desnecessários (desperdício de CPU!)
- Com computation lock:
  ├─ Thread A: adquire lock("5:7"), calcula
  ├─ Thread B: aguarda lock("5:7")
  ├─ Thread A: completa, liberta lock
  ├─ Thread B: adquire lock, verifica cache, encontra!
  └─ Resultado: 1 cálculo apenas (eficiente!)

Implementação (lazy):
  lock (computationLocksLock)
    if (!computationLocks.containsKey(key)):
      └─ computationLocks[key] = new ReentrantLock()
    return computationLocks[key]
  unlock

Por quê lazy?
- Não precisa pré-criar locks para todas combinações
- Criado apenas quando usado
- Economiza memória

Complexidade: O(1)
```

---

## 📊 Matriz de Métodos e Responsabilidades

| Classe                | Método                    | Responsabilidade                   | Complexidade            | Thread-Safe                |
| --------------------- | ------------------------- | ---------------------------------- | ----------------------- | -------------------------- |
| **ClienteMiddleware** | `conectar()`              | Estabelecer conexão ao servidor    | O(1)                    | Sim (lockEscrita)          |
|                       | `enviar()`                | Enviar request e aguardar resposta | O(1) + blocking         | Sim (locks)                |
|                       | `nextTag()`               | Gerar tag único                    | O(1)                    | Sim (lockTags)             |
| **Demultiplexer**     | `run()`                   | Loop de leitura de respostas       | O(msg_size)             | Sim (lock)                 |
|                       | `aguardar()`              | Cliente bloqueia até resposta      | O(1) + blocking         | Sim (condition)            |
|                       | `entregarResposta()`      | Entregar resposta para thread      | O(1)                    | Sim (signal)               |
| **Server**            | `iniciar()`               | Loop principal aceitar clientes    | O(1) + blocking         | Sim (locks)                |
|                       | `parar()`                 | Shutdown gracioso                  | O(tasks_pendentes)      | Sim (locks)                |
| **ThreadPoolImpl**    | `submit()`                | Adicionar tarefa                   | O(1)                    | Sim (lock)                 |
|                       | `runWorkerLoop()`         | Executar tasks em loop             | O(task_time)            | Sim (lock + unlock)        |
|                       | `shutdown()`              | Gracious shutdown                  | O(1)                    | Sim (lock)                 |
| **ServicoEventos**    | `registrarEvento()`       | Registar evento em memória         | O(1)                    | Sim (writeLock)            |
|                       | `novoDia()`               | Transição para novo dia            | O(eventos)              | Sim (writeLock)            |
| **ServicoAgregacoes** | `obterQuantidadeVendas()` | Retorna agregação de quantidade    | O(dias) ou O(log dias)  | Sim (cache)                |
|                       | `calcularAgregacao()`     | Calcula agregação adaptativa       | O(dias)                 | Sim (estratégia)           |
| **CacheManager**      | `obterAgregacaoDia()`     | Obter agregação com cache LRU      | O(1) hit / O(dias) miss | Sim (rwLock + comp)        |
|                       | `getComputationLock()`    | Obter lock lazy por chave          | O(1)                    | Sim (computationLocksLock) |

---

## 🎯 Fluxo Completo: Cliente Registra Evento

```
Cliente (Thread UI):
  │
  └─ UI.registrarEvento(produto=5, qtd=10, preço=3€)
     │
     ├─ EventoDTO dto = new EventoDTO(...)
     │
     ├─ stub.registrarEvento(dto)  ← Chamada remota (Stub)
     │
     └─ middleware.enviar(SERVICO_EVENTOS, METODO_REGISTRAR, dto)
        │
        ├─ tag = nextTag() = 1  ← Tag único
        │
        ├─ serializar dto → byte[]
        │
        ├─ msg = Message(tag=1, serviceId=EVENTOS, methodId=REGISTRAR, payload)
        │
        ├─ lockEscrita.lock()
        │  └─ protocolo.enviar(msg)  ← Envia ao servidor
        │  └─ lockEscrita.unlock()
        │
        └─ demux.aguardar(1)  ← BLOQUEIA AQUI
           │
           └─ [Espera até resposta chegar]

---

Servidor (aceitação):
  │
  ├─ Server.accept()  ← Aguarda cliente
  │
  ├─ Socket clientSocket = nova conexão
  │
  ├─ ClientHandler handler = new ClientHandler(socket)
  │
  └─ clientHandlerPool.submit(handler)
     │
     └─ Worker thread A
        │
        └─ ClientHandler.run()
           │
           └─ while (cliente ativo):
              │
              └─ protocolo.receber(socket)  ← Lê Message(tag=1, ...)
                 │
                 ├─ requestPool.submit(RequestProcessor(msg))
                 │
                 └─ [Handler volta a receber próximas mensagens]

---

Servidor (processamento):
  │
  └─ RequestPool Worker B
     │
     └─ RequestProcessor.run()
        │
        ├─ dispatcher.despachar(msg)
        │
        ├─ skeleton = skeletons[SERVICO_EVENTOS]
        │
        ├─ dto = decodificar payload
        │
        ├─ resposta = skeleton.processarRequisicao(REGISTRAR, dto)
        │
        └─ ServicoEventos.registrarEvento(dto)
           │
           ├─ lock.writeLock()
           │
           ├─ eventosDiaAtual[5].add(evento)
           │
           ├─ notificationManager.verificar()
           │
           ├─ lock.writeLock().unlock()
           │
           └─ return RespostaDTO.sucesso()
        │
        ├─ Response msg = Message(tag=1, response)
        │
        ├─ lockEscrita.lock()
        │  └─ protocolo.enviar(msg)  ← Envia resposta
        │  └─ lockEscrita.unlock()
        │
        └─ [Worker B fica livre para novo task]

---

Cliente (recebimento):
  │
  └─ Demultiplexer thread
     │
     └─ protocolo.receber()  ← Lê Message(tag=1, response)
        │
        ├─ entregarResposta(1, payload)
        │
        ├─ lock.lock()
        │
        ├─ respostas[1] = payload
        │
        ├─ condition[1].signal()  ← ACORDA thread cliente
        │
        ├─ lock.unlock()
        │
        └─ [Demux volta a receber próximas respostas]

---

Cliente (resposta recebida):
  │
  └─ condition.signal() acordou demux.aguardar(1)
     │
     ├─ resposta = respostas.remove(1)
     │
     ├─ return resposta
     │
     └─ UI.registrarEvento() retorna
        │
        └─ "Evento registado com sucesso!"

---

Timeline Total:
  ├─ 0ms: Cliente inicia chamada
  ├─ 1ms: Envia Message ao servidor
  ├─ 2ms: Servidor recebe em ClientHandler
  ├─ 3ms: RequestProcessor começa execução
  ├─ 4ms: ServicoEventos.registrarEvento() executa
  ├─ 5ms: Resposta pronta, envia
  ├─ 6ms: Demultiplexer recebe resposta
  ├─ 7ms: Cliente acorda, retorna resposta
  └─ Total: ~5-7ms (latência total)
```

---

## 🏁 Conclusão

Cada método tem:

- **Responsabilidade clara** (1 coisa bem feita)
- **Thread safety** (locks quando necessário)
- **Performance** (sem overhead desnecessário)
- **Simplicidade** (intuitivo de seguir)

Juntos formam um sistema distribuído robusto de gestão de eventos de vendas! 🎉
