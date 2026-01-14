# 📊 Agregações - Explicação Completa

## O Que São Agregações?

Agregações são **cálculos de resumo** sobre os eventos de venda de um produto num intervalo de dias.

**Tipos de Agregações:**

1. **Quantidade de Vendas**: Total de unidades vendidas
2. **Volume de Vendas**: Valor total em euros
3. **Preço Médio**: Média de preço por venda
4. **Preço Máximo**: Preço mais alto registado

---

## Fluxo Completo de Uma Agregação

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. CLIENT: Invoca stub                                          │
│    cliente.obterQuantidadeVendas(produtoID=10, dias=7)        │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. MIDDLEWARE: Serializa request                                │
│    - Tag = 123 (sequencial)                                     │
│    - Cria AgregacaoRequestDTO(produtoID=10, dias=7)           │
│    - Envia Message(tag=123, METHOD_AGREGACOES, payload)        │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. REDE: Socket TCP → Servidor                                  │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. SERVIDOR: ClientHandler lê request                           │
│    - Lê Message(tag=123, METHOD_AGREGACOES, ...)              │
│    - Submete RequestProcessor ao threadPool                     │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 5. DISPATCHER: Roteia para ServicoAgregacoes                     │
│    - Desserializa payload → AgregacaoRequestDTO                │
│    - Chama servicoAgregacoes.obterQuantidadeVendas(10, 7)      │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 6. CALCULO: ServicoAgregacoes.calcularAgregacao()              │
│                                                                 │
│    a) VALIDAÇÃO:                                                │
│       - produtoID > 0? ✓                                        │
│       - dias > 0 e dias <= D? ✓                                │
│       - Há histórico disponível? ✓                             │
│                                                                 │
│    b) DEFINIR INTERVALO:                                        │
│       - ultimoDia = 150 (ex)                                   │
│       - diasReais = min(7, 151) = 7                            │
│       - diaInicio = 150 - 7 + 1 = 144                          │
│       - diaFim = 150                                            │
│       - Dias a agregar: [144, 145, 146, 147, 148, 149, 150]   │
│                                                                 │
│    c) ESTRATÉGIA:                                               │
│       ├─ Contar cache hits (dias em cache)                      │
│       ├─ Se cache hits >= 2: calcular dia-a-dia (LRU)         │
│       └─ Senão: usar streaming multi-dia                       │
│                                                                 │
│    d) ACUMULAR AGREGACOES:                                      │
│       ├─ Para cada dia [144..150]:                             │
│       │  ├─ Dia em cache? SIM:                                │
│       │  │  └─ resultado.acumular(cached)                     │
│       │  └─ Dia em cache? NÃO:                                │
│       │     └─ Carrega via CacheManager                       │
│       │        └─ resultado.acumular(calculada)               │
│       └─ resultado.updatePrecoMedio()                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 7. RESULTADO: Agregacao com valores calculados                  │
│                                                                 │
│    Agregacao {                                                  │
│      quantidadeVendas = 523        (sum de qty de todos dias) │
│      volumeVendas = 12340.50€      (sum de qty*preco)         │
│      precoMaximo = 45.99€          (max de todos os preços)   │
│      precoMedio = 23.60€           (soma_precos / eventos)    │
│      numeroEventos = 523            (total de eventos)        │
│    }                                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 8. SERIALIZAR: Agregacao → AgregacaoDTO                        │
│                                                                 │
│    AgregacaoDTO {                                               │
│      produtoID = 10                                             │
│      dias = 7                                                   │
│      quantidadeVendas = 523                                    │
│      volumeVendas = 12340.50                                   │
│      precoMedio = 23.60                                        │
│      precoMaximo = 45.99                                       │
│    }                                                            │
│                                                                 │
│    serialize() → byte[]                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 9. RESPOSTA: Message(tag=123, response, payload)               │
│    - Envia de volta ao cliente                                  │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 10. DEMULTIPLEXER: Correlaciona tag=123 com resposta           │
│     - Entrega resposta à thread que aguardava                   │
│     - Thread desbloqueia                                        │
└─────────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ 11. CLIENT: Recebe AgregacaoDTO                                │
│     cliente.obterQuantidadeVendas(10, 7) → 523 unidades       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Classe Agregacao (Domínio)

A classe `Agregacao` acumula valores de eventos:

```java
public class Agregacao {
    private int quantidadeVendas;      // sum quantidade
    private double volumeVendas;       // sum (quantidade * preco)
    private double precoMaximo;        // max preco
    private double somaPrecos;         // sum preco (para média)
    private int numeroEventos;         // count eventos
    private double precoMedio;         // somaPrecos / numeroEventos
}
```

### Método `update(quantidade, preco)`

```java
public void update(int quantidade, double preco) {
    quantidadeVendas += quantidade;           // Acumula qtd
    volumeVendas += quantidade * preco;       // Acumula valor

    if (preco > precoMaximo) {
        precoMaximo = preco;                  // Atualiza máximo
    }

    somaPrecos += preco;                      // Para cálculo de média
    numeroEventos++;                          // Conta eventos
}
```

**Exemplo:**

```
Eventos do dia para Produto 10:
- Evento 1: qtd=5, preço=10€
- Evento 2: qtd=3, preço=15€
- Evento 3: qtd=2, preço=10€

update(5, 10):
  quantidadeVendas = 0 + 5 = 5
  volumeVendas = 0 + (5*10) = 50
  precoMaximo = 10
  somaPrecos = 0 + 10 = 10
  numeroEventos = 1

update(3, 15):
  quantidadeVendas = 5 + 3 = 8
  volumeVendas = 50 + (3*15) = 95
  precoMaximo = 15 (atualizado)
  somaPrecos = 10 + 15 = 25
  numeroEventos = 2

update(2, 10):
  quantidadeVendas = 8 + 2 = 10
  volumeVendas = 95 + (2*10) = 115
  precoMaximo = 15 (sem mudança)
  somaPrecos = 25 + 10 = 35
  numeroEventos = 3

Final: {qtd=10, volume=115€, max=15€, nEventos=3}
```

### Método `acumular(outraAgregacao)`

```java
public void acumular(Agregacao outra) {
    quantidadeVendas += outra.quantidadeVendas;
    volumeVendas += outra.volumeVendas;

    if (outra.precoMaximo > precoMaximo) {
        precoMaximo = outra.precoMaximo;      // Máximo dos máximos
    }

    somaPrecos += outra.somaPrecos;
    numeroEventos += outra.numeroEventos;
}
```

**Exemplo (Múltiplos Dias):**

```
Agregação Dia 144:
  {qtd=10, volume=115€, max=15€, somaPrecos=35, eventos=3}

Agregação Dia 145:
  {qtd=20, volume=300€, max=25€, somaPrecos=100, eventos=10}

acumular(dia145):
  quantidadeVendas = 10 + 20 = 30
  volumeVendas = 115 + 300 = 415
  precoMaximo = max(15, 25) = 25
  somaPrecos = 35 + 100 = 135
  numeroEventos = 3 + 10 = 13

Final após 2 dias: {qtd=30, volume=415€, max=25€, eventos=13}
```

### Método `updatePrecoMedio()`

```java
public void updatePrecoMedio() {
    if (numeroEventos > 0) {
        precoMedio = somaPrecos / numeroEventos;
    } else {
        precoMedio = 0.0;
    }
}
```

**Exemplo:**

```
somaPrecos = 135
numeroEventos = 13

precoMedio = 135 / 13 = 10.38€
```

---

## Fluxo de Cálculo em ServicoAgregacoes

### 1. Validação

```java
private void validarParametros(int produtoID, int dias) {
    if (produtoID <= 0)
        throw new AgregacaoException("ID de produto inválido");

    if (dias <= 0)
        throw new AgregacaoException("Número de dias inválido");

    if (dias > D)
        throw new AgregacaoException("Número de dias excede configuração");

    int diaAtual = servicoEventos.getDiaAtual();
    int diasDisponiveis = diaAtual;
    if (dias > diasDisponiveis)
        throw new AgregacaoException("Dias excede histórico disponível");
}
```

### 2. Calcular Intervalo

```java
int ultimoDia = eventoRepository.obterUltimoDia();  // Ex: 150
if (ultimoDia < 0) return new Agregacao();         // Sem eventos

int diasReais = Math.min(dias, ultimoDia + 1);     // min(7, 151) = 7
int diaInicio = ultimoDia - diasReais + 1;         // 150 - 7 + 1 = 144
int diaFim = ultimoDia;                             // 150

// Dias a agregar: 144, 145, 146, 147, 148, 149, 150
```

**Visualização:**

```
Dias de 0 a 150 disponíveis:
┌─ 0 ──────────────────────────────────┬─ 144 ─ 145 ─ 146 ─ 147 ─ 148 ─ 149 ─ 150
└─ Histórico antigo                    └─ ÚLTIMOS 7 DIAS (intervalo agregação)
```

### 3. Estratégia: Cache vs Streaming

```java
// 1º: Contar quantos dias estão em cache
int diasNaoCache = 0;
for (int dia = diaInicio; dia <= diaFim; dia++) {
    Agregacao cached = tentarCache(produtoID, dia);
    if (cached != null) resultado.acumular(cached);    // Cache hit
    else diasNaoCache++;                               // Cache miss
}

// 2º: Decidir estratégia
if (diasNaoCache > 5) {
    // Muitos misses: use streaming multi-dia
    Agregacao streamingResult =
        eventoRepository.agregarEventosMultiDia(produtoID, diaInicio, diaFim);
    streamingResult.updatePrecoMedio();
    return streamingResult;
}

// 3º: Poucos misses: calcular dia-a-dia
for (int dia = diaInicio; dia <= diaFim; dia++) {
    Agregacao cached = tentarCache(produtoID, dia);
    if (cached == null) {
        // Cache miss: carrega via CacheManager
        cached = cacheManager.obterAgregacaoDia(produtoID, dia);
        resultado.acumular(cached);
    }
}

resultado.updatePrecoMedio();
return resultado;
```

**Lógica:**

```
Se diasNaoCache > 5:
  └─ Usar streaming (lê ficheiros todo de uma vez)
  └─ Mais eficiente para muitos misses

Senão:
  └─ Usar cache (dia-a-dia)
  └─ Aproveita hits existentes
  └─ Calcula misses sob demanda
```

---

## Exemplo de Execução Completa

```
Request: cliente.obterQuantidadeVendas(produtoID=10, dias=3)

SERVIDOR:
├─ ultimoDia = 150
├─ diasReais = min(3, 151) = 3
├─ diaInicio = 150 - 3 + 1 = 148
├─ diaFim = 150
├─ Dias a agregar: [148, 149, 150]
│
├─ LOOP 1: dia=148
│  ├─ tentarCache(10, 148)?
│  │  ├─ Sim (em cache): resultado.acumular(Agregacao{qtd=50})
│  │  └─ diasNaoCache = 0
│  └─ resultado: {qtd=50, volume=..., max=..., eventos=...}
│
├─ LOOP 2: dia=149
│  ├─ tentarCache(10, 149)?
│  │  ├─ Não (cache miss): diasNaoCache = 1
│  │  └─ Será carregado depois
│  └─ resultado: {qtd=50, volume=..., max=..., eventos=...}
│
├─ LOOP 3: dia=150
│  ├─ tentarCache(10, 150)?
│  │  ├─ Não (cache miss): diasNaoCache = 2
│  │  └─ Será carregado depois
│  └─ resultado: {qtd=50, volume=..., max=..., eventos=...}
│
├─ diasNaoCache = 2 (< 5, use cache dia-a-dia)
│
├─ LOOP FINAL:
│  ├─ dia=148: já em resultado (pulsa)
│  ├─ dia=149: cache miss
│  │  └─ cacheManager.obterAgregacaoDia(10, 149)
│  │     ├─ LRU eviction ou streaming
│  │     └─ Retorna Agregacao{qtd=40, ...}
│  │     └─ resultado.acumular() → {qtd=50+40=90, ...}
│  ├─ dia=150: cache miss
│  │  └─ cacheManager.obterAgregacaoDia(10, 150)
│  │     ├─ LRU eviction ou streaming
│  │     └─ Retorna Agregacao{qtd=60, ...}
│  │     └─ resultado.acumular() → {qtd=90+60=150, ...}
│
├─ resultado.updatePrecoMedio()
│
└─ FINAL: Agregacao{qtd=150, volume=3500€, max=30€, medio=20€}

Client recebe: "Quantidade de Vendas nos últimos 3 dias: 150"
```

---

## Métodos de ServicoAgregacoes

```java
// Quantidade total de unidades
obterQuantidadeVendas(produtoID, dias)
  └─ calcularAgregacao() → agregacao.getQuantidadeVendas()

// Valor total em euros
obterVolumeVendas(produtoID, dias)
  └─ calcularAgregacao() → agregacao.getVolumeVendas()

// Preço médio
obterPrecoMedio(produtoID, dias)
  └─ calcularAgregacao() → agregacao.getPrecoMedio()

// Preço máximo
obterPrecoMaximo(produtoID, dias)
  └─ calcularAgregacao() → agregacao.getPrecoMaximo()
```

Todos usam o mesmo método `calcularAgregacao()`, apenas retornam campos diferentes!

---

## AgregacaoDTO (Serialização)

```java
public class AgregacaoDTO {
    private int produtoID;
    private int dias;
    private int quantidadeVendas;
    private double volumeVendas;
    private double precoMedio;
    private double precoMaximo;

    public byte[] serialize() {
        // Escreve em ordem:
        // 1. produtoID (4 bytes)
        // 2. dias (4 bytes)
        // 3. quantidadeVendas (4 bytes)
        // 4. volumeVendas (8 bytes double)
        // 5. precoMedio (8 bytes double)
        // 6. precoMaximo (8 bytes double)
        // Total: 36 bytes
    }

    public static AgregacaoDTO deserialize(byte[] data) {
        // Lê na mesma ordem
    }
}
```

**Bytes:**

```
[ProdutoID: 4] [Dias: 4] [Qtd: 4] [Volume: 8] [Médio: 8] [Máximo: 8]
           10          7        150        3500.00    20.00     30.00
┌────────────────────────────────────────────────────────────────┐
│ Total: 36 bytes por agregação                                  │
└────────────────────────────────────────────────────────────────┘
```

---

## Otimizações Implementadas

### 1. Lazy Computation

```
Client A: obter agregação produto 5, dias 7
  └─ Primeira vez: calcula tudo
  └─ Armazena em cache

Client B: obter agregação produto 5, dias 7
  └─ Cache hit: retorna instantaneamente
  └─ Sem recalcular
```

### 2. Cache LRU

```
Se dias não estão em cache e cache está cheio:
  ├─ Streaming: lê ficheiro sem alocar memória
  ├─ Próximas consultas: ainda precisam ler (não em LRU)

Senão:
  ├─ Carrega em memória
  ├─ LRU: move para final (mais recente)
  ├─ Próximas consultas: cache hit rápido
```

### 3. Estratégia Adaptativa

```
Se muitos misses (> 5 dias):
  └─ Streaming multi-dia (uma única passagem no ficheiro)

Senão:
  └─ Dia-a-dia (aproveita cache hits)
```

---

## Exemplo de Performance

```
Configuração:
- D = 30 dias
- S = 5 séries em memória
- ProdutoID = 10

Cenário 1: Agregação de dias recentes

client.obterQuantidadeVendas(10, 3)  ← últimos 3 dias

├─ Dias 148, 149, 150
├─ Todos em cache LRU (dias recentes, quentes)
├─ Cache hits: 3/3 (100%)
├─ Latência: ~1-5 ms (memória)
└─ Sem I/O!

───────────────────────────────────────

Cenário 2: Agregação de dias antigos

client.obterQuantidadeVendas(10, 20)  ← últimos 20 dias

├─ Dias 131-150
├─ Cache hits: dias 146-150 (5 dias em cache)
├─ Cache misses: dias 131-145 (15 dias, > 5)
├─ Estratégia: Streaming multi-dia
├─ Streaming reads: 15 ficheiros em uma passagem
├─ Latência: ~100-200 ms (I/O)
└─ Memória: +0 (streaming não aloca)

───────────────────────────────────────

Cenário 3: Agregação normal

client.obterQuantidadeVendas(10, 7)  ← últimos 7 dias

├─ Dias 144-150
├─ Cache hits: dias 149, 150 (2/7, ~29%)
├─ Cache misses: dias 144-148 (5/7, ~71%)
├─ diasNaoCache = 5 (não > 5, use cache dia-a-dia)
├─ Carrega misses sob demanda:
│  ├─ dia 144: carrega, LRU remove antigo
│  ├─ dia 145: carrega, LRU remove antigo
│  └─ ... (5 novos em memória)
├─ Latência: ~50-100 ms
└─ Memória: 5 novos dias em cache
```

---

## Fluxo Resumido

```
CLIENT
  ↓ (Request com produtoID, dias)
MIDDLEWARE (Tag + Serializar)
  ↓
REDE
  ↓
SERVIDOR: ClientHandler
  ↓
ThreadPool: RequestProcessor
  ↓
Dispatcher → ServicoAgregacoes
  ↓
1. Validar parâmetros
2. Calcular intervalo [diaInicio, diaFim]
3. Contar cache hits
4. Decidir: Streaming ou Dia-a-Dia
5. Acumular Agregacoes (via cache ou I/O)
6. Calcular preço médio
  ↓
Resultado: Agregacao
  ↓
Serializar → AgregacaoDTO
  ↓
REDE (Response)
  ↓
DEMULTIPLEXER (Correlaciona tag)
  ↓
CLIENT (Recebe resultado)
```

---

## Resumo

| Conceito                  | O que faz                                    |
| ------------------------- | -------------------------------------------- |
| **Agregacao.update()**    | Acumula um evento (qtd, preco, max)          |
| **Agregacao.acumular()**  | Combina duas agregações                      |
| **ServicoAgregacoes**     | Coordena cálculo entre múltiplos dias        |
| **Estratégia adaptativa** | Streaming vs Dia-a-Dia baseado em cache hits |
| **CacheManager**          | Fornece agregações cacheadas                 |
| **AgregacaoDTO**          | Serializa/Desserializa para cliente          |
| **Lazy computation**      | Calcula apenas quando solicitado             |
| **LRU**                   | Mantém dias "quentes" em memória             |
