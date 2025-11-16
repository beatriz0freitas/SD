# Notas rápidas de desenvolvimento

## Concorrência

- **GestorUtilizadores**

  - Estrutura: `HashMap<String, String> utilizadores`.
  - Lock: `ReentrantReadWriteLock`.
  - `readLock`: `autenticar`, `existeUtilizador`, `getNumUtilizadores`, `listarUtilizadores`.
  - `writeLock`: `registar`, `carregarUtilizadores`.
  - Motivo: muitos logins/consultas, poucas escritas.

- **GestorEventos**

  - Estrutura: apenas eventos do dia atual em memória:
    - `int diaAtual`.
    - `Map<Integer, List<Evento>> eventosDiaAtualPorProduto`.
  - Lock: `ReentrantReadWriteLock`.
  - `writeLock`: `adicionarEvento`, `iniciarNovoDia` (grava dia em disco + limpa memória + avança `diaAtual`).
  - `readLock`: `listarEventosDiaAtual` e futuras agregações.

- **ClienteHandler**

  - Um `ExecutorService` por cliente para processar pedidos em paralelo.
  - Escrita no socket protegida por:
    - `ReentrantLock outputLock` → só uma thread escreve no `DataOutputStream` de cada vez.
  - Motivo: evitar mistura de respostas no stream.

- **BibliotecaCliente**
  - Uma ligação TCP por cliente.
  - `ReentrantLock` único a proteger:
    - `conectar` / `desconectar` / `isConectado`.
    - `enviarPedido` (escrever pedido + ler resposta).
  - Motivo: garantir que a resposta lida corresponde ao pedido enviado (modelo 1 pedido → 1 resposta).

---

## Eventos e persistência

- Em memória:
  - Só eventos do **dia atual**, por produto:
    - `Map<Integer, List<Evento>> eventosDiaAtualPorProduto`.
- Em disco:
  - Ao `iniciarNovoDia()`:
    - `PersistenciaEventos.guardarEventosDia(diaAtual, eventosDiaAtualPorProduto)` em `dados/eventos/eventos_dia_<dia>.dat`.
    - Depois limpa o mapa e incrementa `diaAtual`.

---

## Protocolo

- `Mensagem`: representa pedidos/respostas (tipo de operação + payload).
- `Protocolo`:
  - `escreverMensagem(Mensagem, DataOutputStream)` / `lerMensagem(DataInputStream)`.
  - Formato binário simples: tipo + tamanho do payload + payload.
- `PayloadParser`: extrai dados do payload:
  - credenciais, dados do evento, password admin, etc.

---

## TODOs principais

- Implementar agregações em `GestorEventos`:
  - quantidade total, volume, preço médio, máximo, filtros.
- Ligar essas operações no `ClienteHandler` (cases que hoje dizem “não implementado”) e no cliente.
- (Opcional) Persistir `diaAtual` para retomar estado depois de reboot.
