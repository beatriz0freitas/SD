# 🎯 Cache LRU - Explicação Completa

## O Que É LRU?

**LRU = Least Recently Used**

É uma estratégia de eviction (remoção) para caches quando chegam ao limite de tamanho:

- Quando o cache está **cheio** e precisa de espaço para uma **nova série**
- **Remove a série menos usada recentemente** (que há mais tempo não foi acedida)
- Mantém as séries mais "quentes" (usadas frequentemente) em memória

---

## Estrutura de Dados

```java
// CacheManager.java

// 1. Ordem de acesso (LRU)
private final List<Integer> ordemAcesso = new ArrayList<>();

// 2. Dados em memória
private final Map<Integer, Map<Integer, List<Evento>>> seriesEmMemoria = new HashMap<>();

// 3. Agregações cacheadas
private final Map<Integer, Map<Integer, Agregacao>> cacheAgregacoes = new HashMap<>();

// 4. Parâmetro: tamanho máximo
private final int S;  // Máximo S séries em memória
```

### O que significa cada estrutura?

```
ordemAcesso: [5, 3, 7, 2]
  └─ Dia 5 foi acedido primeiro (menos recente)
  └─ Dia 2 foi acedido último (mais recente)
  └─ Quando cache cheio: remove dia 5 (primeira posição)

seriesEmMemoria:
  Dia 5 → {ProdutoA: [Evento1, Evento2, ...],
           ProdutoB: [Evento3, Evento4, ...]}
  Dia 3 → {ProdutoA: [Evento5, ...], ...}
  Dia 7 → {...}
  Dia 2 → {...}

cacheAgregacoes:
  ProdutoID 10 → {Dia 5: Agregacao(...),
                  Dia 3: Agregacao(...)}
  ProdutoID 20 → {Dia 7: Agregacao(...)}
```

---

## Algoritmo LRU: Passo-a-Passo

### 1. Acesso a uma série (já em memória)

```java
if (seriesEmMemoria.containsKey(dia)) {
    // Move para final (mais recente)
    ordemAcesso.remove(Integer.valueOf(dia));  // Remove de posição anterior
    ordemAcesso.add(dia);                       // Adiciona ao final

    // Usa dados
    List<Evento> eventos = seriesEmMemoria.get(dia).get(produtoID);
    return agregarEventos(eventos);
}
```

**Exemplo:**

```
Acesso dia 3 (série em memória):

Antes:  ordemAcesso = [5, 3, 7, 2]
        ↓ remove(3)
        ordemAcesso = [5, 7, 2]
        ↓ add(3)
Depois: ordemAcesso = [5, 7, 2, 3]
                           └─ Dia 3 agora é o mais recente
```

### 2. Nova série (cache não cheio)

```java
if (seriesEmMemoria.size() < S) {  // Ainda há espaço
    Map<Integer, List<Evento>> seriesDia = carregarEventosDia(dia);
    seriesEmMemoria.put(dia, seriesDia);
    ordemAcesso.add(dia);  // Adiciona ao final (mais recente)
}
```

**Exemplo:**

```
S = 5 (cache pode ter máximo 5 séries)
Adicionar dia 2 (cache com 4 séries):

Antes:  ordemAcesso = [5, 3, 7] (size=3 < 5)
Depois: ordemAcesso = [5, 3, 7, 2] (size=4 < 5)
        ✅ Espaço ainda disponível
```

### 3. Nova série (cache CHEIO - LRU Eviction)

```java
if (seriesEmMemoria.size() >= S) {  // Cache está cheio!
    // Remove menos recente (primeira posição)
    Integer diaRemover = ordemAcesso.remove(0);
    seriesEmMemoria.remove(diaRemover);
    System.out.println("LRU: removida série dia " + diaRemover);
}

// Carrega nova série
Map<Integer, List<Evento>> seriesDia = carregarEventosDia(dia);
seriesEmMemoria.put(dia, seriesDia);
ordemAcesso.add(dia);  // Adiciona ao final
```

**Exemplo:**

```
S = 5 (cache cheio com 5 séries)
Adicionar dia 9 (cache com 5 séries):

Antes:  ordemAcesso = [5, 3, 7, 2, 1] (size=5, CHEIO!)
        └─ Dia 5 é menos recente

        remove(0) → remove dia 5

        ordemAcesso = [3, 7, 2, 1]
        seriesEmMemoria.remove(5)

        add(9)

Depois: ordemAcesso = [3, 7, 2, 1, 9] (size=5)
        └─ Dia 9 agora é o mais recente
        └─ Dia 5 foi deletado para liberar espaço
```

---

## Cenário Prático Completo

```
Configuração: D=30 dias, S=3 séries em memória, ProdutoID=7

Dia 1:
  Client 1 pede: agregacao(dias=3)
    └─ Dias necessários: [-1, 0, 1]
    └─ Carrega dia -1: ordemAcesso=[−1], size=1/3 ✅
    └─ Carrega dia  0: ordemAcesso=[−1, 0], size=2/3 ✅
    └─ Carrega dia  1: ordemAcesso=[−1, 0, 1], size=3/3 ✅
    └─ Cache: cheio!

Dia 1 (continuação):
  Client 2 pede: agregacao(dias=5)
    └─ Dias necessários: [-3, -2, -1, 0, 1]
    └─ Dia -1 já em memória: ordemAcesso=[0, 1, -1]
    └─ Dia  0 já em memória: ordemAcesso=[1, -1, 0]
    └─ Dia  1 já em memória: ordemAcesso=[-1, 0, 1]
    └─ Dia -2 não está: cache cheio (size=3)
       ├─ Remove: dia -1 (menos recente, primeiro)
       ├─ LRU eviction: remove(-1)
       ├─ ordemAcesso=[0, 1]
       ├─ Carrega dia -2: ordemAcesso=[0, 1, -2], size=3/3
    └─ Dia -3 não está: cache cheio (size=3)
       ├─ Remove: dia 0 (menos recente)
       ├─ ordemAcesso=[1, -2]
       ├─ Carrega dia -3: ordemAcesso=[1, -2, -3], size=3/3

Resultado:
  ordemAcesso = [1, -2, -3]  (dias em memória)
  Dias com 1, -2, -3 (Client 2 completou!)
  Dias -1 e 0 foram removidos (menos usados)

Dia 1 (continuação):
  Client 1 novamente: agregacao(dias=3)
    └─ Dias necessários: [-1, 0, 1]
    └─ Dia 1 em memória: ordemAcesso=[-2, -3, 1] ✅
    └─ Dia -1 NÃO em memória: cache cheio
       ├─ Remove: dia -2 (menos recente)
       ├─ Carrega dia -1: ordemAcesso=[-3, 1, -1]
    └─ Dia 0 NÃO em memória: cache cheio
       ├─ Remove: dia -3 (menos recente)
       ├─ Carrega dia 0: ordemAcesso=[1, -1, 0]
    └─ Dias em memória agora: [1, -1, 0]
```

---

## Streaming vs Memória

### Quando usar Streaming?

```java
boolean usarStreaming;
readLock.lock();
try {
    // Condição: cache CHEIO e série NÃO em memória
    usarStreaming = seriesEmMemoria.size() >= S &&
                    !seriesEmMemoria.containsKey(dia);
} finally {
    readLock.unlock();
}

if (usarStreaming) {
    // RandomAccessFile: lê direto do ficheiro, sem alocar memória
    calculada = eventoRepository.agregarEventosDia(produtoID, dia);
    // Lê sequencialmente, descarta dados depois
    // Não aloca em seriesEmMemoria!
} else {
    // Carrega série em memória, cálculo rápido
    calculada = calcularComMemoria(produtoID, dia);
    // Armazena em seriesEmMemoria para próximas consultas
}
```

**Por quê?**

- **Streaming**: Quando cache está cheio, não há espaço. Lê do ficheiro sem alocar.
- **Memória**: Quando há espaço, carrega série em memória. Próximas consultas usam cache (hit).

---

## Memory Leak em Tags (Demultiplexer)

### O Problema

```java
public byte[] aguardar(long tag) throws Exception {
    lock.lock();
    try {
        verificarErro();  // ← PODE LANÇAR EXCEPTION!
        if (serverShutdown) throw new IOException(...);  // ← EXCEPTION!
        if (!ativo) throw new IOException(...);          // ← EXCEPTION!

        threadsEspera.put(tag, condicao);
        respostas.put(tag, resposta);

        while (...) {
            condicao.await();  // ← PODE TER InterruptedException!
        }

        // SE EXCEPTION ACIMA, NUNCA CHEGA AQUI:
        threadsEspera.remove(tag);  // ← NÃO EXECUTA ❌
        return respostas.remove(tag);  // ← NÃO EXECUTA ❌

    } finally {
        lock.unlock();  // ← Apenas unlock, mas tag fica armazenada!
    }
}
```

**Cenário:**

```
Cliente 1: tag=1, envia
           await() bloqueado

Servidor encerra: setServerShutdown(true)

Demultiplexer:
  aguardar(1) → throw IOException("Servidor encerrado")
  ← Exception ANTES de remove()

Resultado:
  respostas[1] = resposta ← FICA LÁ ❌
  threadsEspera[1] = condition ← FICA LÁ ❌

Após 1000 requests com erros:
  respostas.size() = 1000 (tags vazadas)
  threadsEspera.size() = 1000 (Conditions vazadas)
  Memória: ~100 KB de leak
```

### A Solução

```java
public byte[] aguardar(long tag) throws Exception {
    lock.lock();
    Condition condicao = lock.newCondition();
    threadsEspera.put(tag, condicao);  // ← ANTES do try

    try {
        verificarErro();  // ← Pode lançar exception
        if (serverShutdown) throw new IOException(...);
        if (!ativo) throw new IOException(...);

        while (!respostas.containsKey(tag) && erro == null && !serverShutdown && ativo) {
            condicao.await();
        }

        if (!ativo) throw new IOException(...);
        if (serverShutdown) throw new IOException(...);
        verificarErro();

        return respostas.remove(tag);

    } finally {
        // ✅ SEMPRE executa, mesmo com exception acima!
        threadsEspera.remove(tag);
        respostas.remove(tag);
        lock.unlock();
    }
}
```

**Resultado:**

```
Cliente 1: tag=1, envia
           await() bloqueado

Servidor encerra:

Demultiplexer:
  aguardar(1) → throw IOException
  ← Exception interceptada
  → finally: threadsEspera.remove(1) ✅
  → finally: respostas.remove(1) ✅

Resultado:
  tag=1 é SEMPRE removido ✅
  Sem leak!
```

---

## Double-Check Locking

### O Padrão

```java
// 1ª verificação SEM lock (rápido)
readLock.lock();
if (cacheAgregacoes.get(produtoID) != null) {
    return existente;  // Cache hit!
}
readLock.unlock();

// Cache miss: sincroniza
compLock.lock();
try {
    // 2ª verificação COM lock (thread-safe)
    readLock.lock();
    if (cacheAgregacoes.get(produtoID) != null) {
        return existente;  // Outra thread calculou enquanto aguardávamos
    }
    readLock.unlock();

    // Calcula (seguro: apenas 1 thread por chave)
    Agregacao calculada = calcular();

    // Armazena
    writeLock.lock();
    cacheAgregacoes.put(produtoID, calculada);
    writeLock.unlock();

    return calculada;
} finally {
    compLock.unlock();
}
```

### Por que é eficiente?

```
1000 requisições:
- 950 são cache hits (95%)
- 50 são cache misses (5%)

SEM double-check:
  Todas as 1000 adquirem locks
  Contenção máxima

COM double-check:
  Hits (950): readLock (compartilhável)
    └─ Múltiplas threads leem simultaneamente
    └─ Muito rápido

  Misses (50): computation lock (por chave)
    └─ Apenas 1 thread por (produtoID:dia)
    └─ Computations paralelas não se bloqueiam

Resultado:
  95% das reqs retornam rápido (sem contenção)
  5% sincronizam apenas quando necessário
  Performance: 10-25x melhor!
```

---

## Invalidação de Cache

### Por que é necessária?

```
Dia 1, ProdutoID 5, "últimos 3 dias":
  Cache: agregacoes[5] = Agregacao(dias=[−1, 0, 1])

novoDia() é chamado:
  diaAtual = 0 → diaAtual = 1

Dia 2, ProdutoID 5, "últimos 3 dias":
  Deveria ser: Agregacao(dias=[0, 1, 2])

  Mas se cache não foi invalidado:
    Retorna: Agregacao(dias=[−1, 0, 1]) ← ERRADO! ❌

Solução: invalidarCache() em novoDia()
  Cache.clear()
  Cliente recalcula: Agregacao(dias=[0, 1, 2]) ✅
```

### Código

```java
public void novoDia() {
    writeLock.lock();
    try {
        // 1. Persiste eventos
        repository.salvarEventosDia(diaAtual);

        // 2. Limpa memória
        eventosDiaAtual.clear();

        // 3. Muda dia
        diaAtual++;

        // 4. ✅ CRÍTICO: Invalida cache
        cacheManager.invalidarCache();

        // 5. Notifica clients
        condicaoNovoDia.signalAll();
    } finally {
        writeLock.unlock();
    }
}
```

---

## Computation Locks

### Por que separar por chave?

```
Múltiplos clients pedem mesma agregação:

SEM computation locks:
  Client 1: agreg(5, 10) → lock global
    ├─ Calcula (2 segundos)
  Client 2: agreg(5, 10) → bloqueia em lock global
  Client 3: agreg(5, 10) → bloqueia em lock global
  Client 4: agreg(7, 15) → bloqueia (diferente, mas mesmo lock!)

  Problema: Clientes de outras chaves (7,15) também bloqueiam!

COM computation locks (por chave):
  Client 1: agreg(5, 10) → compLock("5:10").lock()
    ├─ Calcula (2 segundos)
  Client 2: agreg(5, 10) → compLock("5:10").lock() (bloqueia)
  Client 3: agreg(5, 10) → compLock("5:10").lock() (bloqueia)
  Client 4: agreg(7, 15) → compLock("7:15").lock() (NÃO bloqueia!)
    ├─ Calcula em paralelo

  Resultado:
    (5, 10) bloqueiam uns aos outros (correto)
    (7, 15) calcula em paralelo (muito bem!)
```

---

## Resumo Final

| Conceito              | O que faz                                   | Benefício                   |
| --------------------- | ------------------------------------------- | --------------------------- |
| **LRU**               | Remove série menos usada quando cache cheio | Memória limitada a S séries |
| **Streaming**         | Lê ficheiro sem alocar memória              | Não sobrecarrega RAM        |
| **Memory Leak Fix**   | Try-finally para remover tags               | Sem acumulação de tags      |
| **Double-Check**      | 1ª rápido, 2ª sincronizado                  | Reduz contenção 10-25x      |
| **Computation Locks** | Locks por chave                             | Paralelismo entre chaves    |
| **Invalidação**       | Clear cache em novoDia                      | Dados sempre correctos      |

---

## Questões para Estudar

Agora que entende o Cache LRU, estude em [QUESTOES_DEFESA.md](QUESTOES_DEFESA.md):

- **Q59**: Cache LRU - Estrutura e Algoritmo
- **Q60**: Double-Check Locking na Cache
- **Q61**: Memory Leak em Tags do Demultiplexer
- **Q62**: Computation Locks para Evitar Duplicação
- **Q63**: Streaming vs Memória - Decisão Automática
- **Q64**: Invalidação de Cache em NovoDia

Mais contexto:

- **Q14-Q16**: Performance e Cache (conceitos gerais)
- **Q43**: Limite S de Séries em Memória
- **Q54**: Impacto de S em performance
