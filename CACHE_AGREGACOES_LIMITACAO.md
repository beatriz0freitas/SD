# Cache de Agregações - Explicação Detalhada

## 🎯 Pergunta: A Cache é Infinita?

**Resposta: NÃO! É limitada, mas de forma estratégica em 2 camadas.**

```
┌─────────────────────────────────────────────────────────┐
│         CACHE DE AGREGAÇÕES (2 Camadas)                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  CAMADA 1: cacheAgregacoes (INFINITA)                  │
│  ├─ Map<produtoID, Map<dia, Agregacao>>               │
│  ├─ Armazena agregações já calculadas                  │
│  └─ Sem limite explícito!                              │
│     └─ Pode crescer enquanto houver memória            │
│                                                         │
│  CAMADA 2: seriesEmMemoria (LIMITADA a S)             │
│  ├─ Map<dia, Map<produtoID, List<Evento>>>            │
│  ├─ Armazena EVENTOS em memória (dados brutos)         │
│  ├─ Máximo de S séries (paramétro do servidor)         │
│  ├─ Ex: S=10 → máximo 10 dias em RAM                   │
│  └─ Com LRU evicção (remove menos usadas)              │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 📊 Estrutura de Dados

### cacheAgregacoes (Agregações Calculadas)

```
cacheAgregacoes:

produto 1 → {
  dia 1 → Agregacao(qtd=100, vol=500€, preço_médio=5€, preço_máx=6€)
  dia 2 → Agregacao(qtd=150, vol=750€, preço_médio=5€, preço_máx=6€)
  dia 3 → Agregacao(qtd=200, vol=1000€, preço_médio=5€, preço_máx=6€)
  dia 4 → Agregacao(...)
  ...
}

produto 2 → {
  dia 1 → Agregacao(...)
  dia 2 → Agregacao(...)
  ...
}

produto 3 → {
  dia 1 → Agregacao(...)
  ...
}

Características:
├─ Sem limite de tamanho
├─ Cresce enquanto cliente pedir agregações diferentes
├─ Armazena valores já calculados
├─ Rápido: O(1) lookup
└─ Pode consumir muita memória se não cuidado!
```

### seriesEmMemoria (Eventos Brutos)

```
seriesEmMemoria:

dia 1 → {
  produto 1 → [Evento(qty=10, preço=5€), Evento(qty=20, preço=5€), ...]
  produto 2 → [Evento(qty=30, preço=3€), ...]
  produto 3 → [Evento(...)]
  ...
}

dia 2 → {
  produto 1 → [Evento(...), ...]
  ...
}

dia 3 → {
  ...
}

Características:
├─ LIMITE: máximo S séries (ex: S=10)
├─ Quando S+1: remove série menos recentemente usada (LRU)
├─ Cada série = todos eventos de 1 dia
├─ Usado para calcular agregações rápido
└─ Se série removida: pode ser recalculada ou feito streaming
```

---

## 🔄 Estratégia de Cache em Ação

### Cenário: Servidor com S=10 (máximo 10 dias em memória)

```
Clientes pedem agregações para diferentes dias...

T0: Cliente A pede agregação(produto 5, dias 1-7)
    ├─ Carrega série dia 1 em memória
    │  ├─ seriesEmMemoria[1] = dados brutos dia 1
    │  ├─ ordemAcesso = [1]
    │  └─ Calcula agregação, armazena em cacheAgregacoes
    │
    ├─ Carrega série dia 2 em memória
    │  ├─ seriesEmMemoria[2] = dados brutos dia 2
    │  ├─ ordemAcesso = [1, 2]
    │  └─ Calcula agregação
    │
    └─ ... (dias 3-7 similar)
       └─ seriesEmMemoria.size() = 7
       └─ ordemAcesso = [1, 2, 3, 4, 5, 6, 7]

T1: Cliente B pede agregação(produto 3, dias 5-14)
    ├─ Dias 5-7: CACHE HIT (séries já em memória)
    │  ├─ Usa seriesEmMemoria[5], [6], [7]
    │  ├─ Atualiza ordemAcesso:
    │  │  └─ Remove 5 do início, adiciona ao fim
    │  │  └─ ordemAcesso = [1, 2, 3, 4, 6, 7, 5]
    │  └─ (série 5 agora é "mais recente")
    │
    ├─ Dia 8: CACHE MISS
    │  ├─ seriesEmMemoria.size() = 7 < 10
    │  ├─ Carrega série 8 em memória
    │  ├─ ordemAcesso = [1, 2, 3, 4, 6, 7, 5, 8]
    │  └─ seriesEmMemoria.size() = 8
    │
    ├─ Dia 9: CACHE MISS
    │  ├─ seriesEmMemoria.size() = 8 < 10
    │  ├─ Carrega série 9 em memória
    │  ├─ ordemAcesso = [1, 2, 3, 4, 6, 7, 5, 8, 9]
    │  └─ seriesEmMemoria.size() = 9
    │
    ├─ Dia 10: CACHE MISS
    │  ├─ seriesEmMemoria.size() = 9 < 10
    │  ├─ Carrega série 10 em memória
    │  ├─ ordemAcesso = [1, 2, 3, 4, 6, 7, 5, 8, 9, 10]
    │  └─ seriesEmMemoria.size() = 10 (CHEIO!)
    │
    ├─ Dia 11: CACHE MISS E EVICÇÃO!
    │  ├─ seriesEmMemoria.size() = 10 >= S
    │  ├─ LRU: remove dia menos recentemente usado
    │  │  └─ ordemAcesso[0] = dia 1 (menos recente!)
    │  │  └─ Remove seriesEmMemoria[1]
    │  │  └─ ordemAcesso = [2, 3, 4, 6, 7, 5, 8, 9, 10]
    │  │
    │  ├─ Carrega série 11 em memória
    │  ├─ ordemAcesso = [2, 3, 4, 6, 7, 5, 8, 9, 10, 11]
    │  ├─ seriesEmMemoria.size() = 10 (sempre CHEIO agora)
    │  └─ Log: "LRU: removida série dia 1"
    │
    ├─ Dia 12: CACHE MISS E EVICÇÃO
    │  ├─ Remove dia 2 (menos recente)
    │  ├─ Carrega série 12
    │  ├─ ordemAcesso = [3, 4, 6, 7, 5, 8, 9, 10, 11, 12]
    │  └─ seriesEmMemoria.size() = 10
    │
    ├─ Dia 13: CACHE MISS E EVICÇÃO
    │  ├─ Remove dia 3
    │  ├─ Carrega série 13
    │  └─ seriesEmMemoria.size() = 10
    │
    └─ Dia 14: CACHE MISS E EVICÇÃO
       ├─ Remove dia 4
       ├─ Carrega série 14
       └─ seriesEmMemoria.size() = 10

Resultado Final:
  ├─ cacheAgregacoes: têm agregações para dias 1-14 (produto 5 e 3)
  ├─ seriesEmMemoria: contém apenas dias [6, 7, 5, 8, 9, 10, 11, 12, 13, 14]
  │  └─ (dias 1-4 foram removidos)
  │
  └─ Se alguém pedir agregação dia 1 novamente:
     ├─ cacheAgregacoes HIT! (se mesmo produto)
     ├─ Ou STREAMING se produto diferente
     └─ (não recarrega série em memória, só processa ficheiro)
```

---

## 💡 Decisão: Camada 2 Limitada (seriesEmMemoria)

### Por quê Limitar seriesEmMemoria?

```
Problema: SEM limite
  ├─ Cada dia carregado = ~100MB em memória
  ├─ Com 365 dias = 36.5GB de RAM!
  ├─ Servidor com 4GB morre
  └─ Memory leak efetivo

Solução: Limitar a S séries
  ├─ Servidor com S=10 dias em memória
  ├─ 10 × 100MB = 1GB máximo
  ├─ Cabe confortavelmente em servidor típico
  ├─ Dias menos usados removidos (LRU)
  └─ Ainda rápido: 90% dos acessos hit nas 10 dias recentes
```

### Por quê NÃO Limitar cacheAgregacoes?

```
cacheAgregacoes armazena:
  ├─ Apenas agregações (não dados brutos)
  ├─ Agregacao ≈ 100 bytes
  │  └─ (4 números: qtd, volume, preço_médio, preço_máx)
  │
  └─ Vs Eventos ≈ 24 bytes cada, mas 1000s por dia
     └─ 1 dia = 1000 eventos × 24 bytes = 24KB mínimo

Custo:
  ├─ Agregacao para 1 dia × 10 produtos = 1KB
  ├─ 365 dias × 10 produtos = 3.65MB
  ├─ Vs série em memória = 3650MB
  └─ 1000x mais eficiente!

Conclusão:
  cacheAgregacoes é "gratuita" em memória!
  Pode crescer sem preocupação (até certo ponto)
```

---

## 🔍 Dois Cenários: Com e Sem Cache

### Cenário 1: Cliente Pede Mesma Agregação 2x

```
SEM seriesEmMemoria:
  ├─ Pedido 1: dias 1-7
  │  ├─ Abre ficheiro dia 1, lê eventos, calcula
  │  ├─ Abre ficheiro dia 2, lê eventos, calcula
  │  └─ ... (7 ficheiros abertos)
  │  └─ Tempo: ~50ms × 7 = 350ms
  │
  ├─ Pedido 2: mesmos dias 1-7
  │  ├─ Repete tudo (sem cache!)
  │  └─ Tempo: ~350ms novamente!
  │
  └─ Total: 700ms para 2 pedidos idênticos

COM seriesEmMemoria:
  ├─ Pedido 1: dias 1-7
  │  ├─ Abre ficheiro dia 1, carrega em memória
  │  ├─ Armazena em seriesEmMemoria[1]
  │  ├─ Calcula agregação, armazena em cacheAgregacoes
  │  └─ ... (7 ficheiros, tudo armazenado)
  │  └─ Tempo: ~350ms
  │
  ├─ Pedido 2: mesmos dias 1-7
  │  ├─ Séries já em memória!
  │  ├─ Apenas olha cacheAgregacoes (memory lookup)
  │  └─ Tempo: ~1ms (300x mais rápido!)
  │
  └─ Total: 351ms para 2 pedidos idênticos

Ganho: 700ms → 351ms = 2x mais rápido
```

### Cenário 2: Cliente Pede Intervalo Largo (30 dias)

```
Setup: Servidor S=10 (máximo 10 dias)

COM Estratégia Adaptativa (ServicoAgregacoes):
  ├─ Tenta cache: dias 1-30
  │  └─ Primeiros 10 hits, rest misses
  │  └─ diasNaoCache = 20 (> 5)
  │  └─ Ativa STREAMING!
  │
  ├─ STREAMING (1 leitura de ficheiro para todos dias)
  │  ├─ eventoRepository.agregarEventosMultiDia(1, 30)
  │  └─ Lê dias 1-30 de uma vez
  │  └─ Tempo: ~50ms (skip direto no ficheiro)
  │
  ├─ NÃO armazena em seriesEmMemoria (já tem 10 dias)
  │  └─ Poupança de memória!
  │
  └─ Total: ~50ms para 30 dias

SEM Estratégia (ingénuo):
  ├─ Tenta cache: dias 1-30
  │  ├─ Hit nos 10 primeiros
  │  ├─ Miss nos outros 20
  │
  ├─ Para cada miss:
  │  ├─ Abre ficheiro
  │  ├─ Lê eventos
  │  ├─ Calcula agregação
  │  └─ ~50ms cada
  │
  ├─ 20 misses × 50ms = 1000ms
  │
  └─ Total: 50ms (hits) + 1000ms (misses) = 1050ms

Ganho: 1050ms → 50ms = 20x mais rápido!
       (Graças ao STREAMING adaptativo)
```

---

## 📈 Memória Consumida

### Estimativa (S=10)

```
seriesEmMemoria (10 dias máximo):
  ├─ Dia = 1000 eventos × 24 bytes
  ├─ 1 dia = 24KB
  ├─ 10 dias = 240KB
  └─ Total: ~240KB para seriesEmMemoria

cacheAgregacoes (número de agregações pedidas):
  ├─ Agregação = 4 números (int, double, double, double)
  ├─ = ~24 bytes
  ├─ 100 agregações = 2.4KB
  ├─ 1000 agregações = 24KB
  └─ Total: ~24KB típico (pode variar)

Consumo Total: ~264KB (muito baixo!)

Comparação:
  ├─ SEM cache: seria necessário abrir N ficheiros (I/O overhead)
  └─ COM cache: ~264KB memória (negligenciável em 2026)
```

---

## 🎯 Invalidação e Limpeza

### Quando Dia Muda: invalidarCache()

```
Cenário: Servidor muda de dia (novoDia())

Antes:
  ├─ diaAtual = 1
  ├─ cacheAgregacoes: tem agregações dia 1
  ├─ seriesEmMemoria: tem série dia 1
  └─ Clientes continuam consultando...

Chamada: servicoEventos.novoDia()
  ├─ Persiste eventos_dia_1.dat
  ├─ Limpa eventosDiaAtual (memória RAM)
  ├─ diaAtual = 2
  ├─ cacheManager.invalidarCache()
  │
  └─ invalidarCache() é vazio no código!
     └─ (NOTA: Poderia limpar agregações antigas)

Depois:
  ├─ cacheAgregacoes: AINDA tem agregações dia 1
  │  └─ Não são mais precisas? (dias passados, sem novos eventos)
  │  └─ Tecnicamente OK, mas podia limpar
  │
  ├─ seriesEmMemoria: AINDA tem série dia 1
  │  └─ Podia ser removida (menos acesso)
  │  └─ LRU fará isso naturalmente com tempo
  │
  └─ Clientes podem ainda consultar dia 1 (OK!)

Melhoramento Potencial:
  ├─ invalidarCache() poderia fazer:
  │  ├─ limparDia(diaAtual - 1)
  │  │  └─ Remove agregações e série do dia passado
  │  │  └─ Poupa memória proactivamente
  │  └─ Mas não crítico (LRU fará após tempo)
```

---

## 🚀 Performance Comparação

### 3 Clientes, 100 Requests Cada

```
Setup: S=10

┌─ SEM Cache (apenas ficheiros)
│  ├─ Cada request: abre ficheiro (~50ms)
│  ├─ 100 × 3 = 300 requests
│  ├─ 300 × 50ms = 15,000ms = 15 segundos
│  └─ Muito lento!
│
├─ COM Cache (2 camadas)
│  ├─ Pedidos 1-10 (cache miss):
│  │  └─ Carrega séries em memória
│  │  └─ ~50ms cada × 10 = 500ms
│  │
│  ├─ Pedidos 11-100 (cache hit):
│  │  └─ Tudo em memória ou STREAMING
│  │  └─ ~1ms cada × 90 = 90ms
│  │
│  └─ Total: 500ms + 90ms = 590ms (25x mais rápido!)
│
└─ Ganho: 15 segundos → 0.6 segundos!

Métrica: throughput
  ├─ SEM cache: 300 requests / 15s = 20 req/s
  └─ COM cache: 300 requests / 0.6s = 500 req/s (25x!)
```

---

## 📋 Métodos de Gestão de Cache

### `obterAgregacaoDia(produtoID, dia)`
```
├─ Tenta read-only lookup em cacheAgregacoes
├─ Se hit: retorna (fast path)
├─ Se miss:
│  ├─ Computation lock para evitar duplicação
│  ├─ Decisão: memória vs streaming
│  ├─ Calcula agregação
│  └─ Armazena em cacheAgregacoes
└─ LRU evicção automática em seriesEmMemoria
```

### `limparDia(dia)`
```
├─ Remove agregações daquele dia de cacheAgregacoes
├─ Remove série daquele dia de seriesEmMemoria
└─ Útil para: invalidação manual ou teste
```

### `limparTudo()`
```
├─ Limpa cacheAgregacoes completamente
├─ Limpa seriesEmMemoria completamente
├─ Reset de ordemAcesso (LRU)
└─ Útil para: shutdown ou reset
```

### `obterEstatisticas()`
```
├─ Retorna:
│  ├─ Número de produtos em cache
│  ├─ Total de agregações
│  ├─ Séries em memória (X/S)
│  └─ Total de eventos em RAM
└─ Exemplo: "Cache: 5 produtos, 150 agregações, 8/10 séries (80000 eventos)"
```

---

## 🎓 Conclusão

### A Cache é Infinita?

```
┌─────────────────────────────────────────────┐
│ CAMADA 1: cacheAgregacoes                   │
│ ├─ Armazena agregações calculadas           │
│ ├─ Sem limite explícito                     │
│ ├─ Muito eficiente (100 bytes cada)         │
│ └─ Pode crescer, mas raramente problema     │
│                                              │
│ CAMADA 2: seriesEmMemoria                   │
│ ├─ Armazena eventos brutos                  │
│ ├─ LIMITADO a S séries (ex: S=10)           │
│ ├─ Com LRU evicção                          │
│ └─ Mantém memória constante                 │
│                                              │
│ ESTRATÉGIA:                                  │
│ ├─ Eventos "quentes" (recentes) em RAM      │
│ ├─ Eventos "frios" (antigos) em disc        │
│ ├─ Agregações cacheadas (não re-calculam)   │
│ └─ STREAMING para intervalos largos         │
└─────────────────────────────────────────────┘
```

**Resposta Final:**
- ✅ cacheAgregacoes: Pode crescer (mas é pequena)
- ✅ seriesEmMemoria: Limitada a S (ex: 10 dias)
- ✅ LRU evicção: Remove dias menos usados
- ✅ STREAMING: Fallback para intervalos largos
- ✅ Resultado: 25x mais rápido com mesma memória!
