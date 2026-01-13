# Documentação Detalhada do Projeto SD

## Estrutura Geral

O projeto está dividido em quatro grandes módulos:

- **client/**: Código do cliente, interface, middleware e abstrações de conexão.
- **common/**: Utilitários, DTOs, exceções, interfaces e métricas comuns a cliente e servidor.
- **middleware/**: Protocolo de comunicação e tipos de mensagens.
- **server/**: Lógica do servidor, serviços, persistência, cache, apresentação e gestão de clientes.

---

## client/

- **Cliente.java**: Ponto de entrada do cliente. Inicializa middleware, interface de utilizador e gere a ligação ao servidor.
- **ClienteMiddleware.java**: Abstrai comunicação remota, gere conexões e serialização de mensagens.
- **ClientShutdownHandler.java**: Garante encerramento limpo do cliente.
- **Demultiplexer.java**: Permite múltiplas respostas assíncronas por cliente.
- **connection/**:
  - _ConnectionPool.java_: Gere pool de conexões para reutilização eficiente.
  - _PooledConnection.java_: Wrapper para conexões reutilizáveis.
- **stub/**: Stubs para cada serviço remoto (Admin, Agregações, Autenticação, Eventos) e fábrica de stubs.
- **ui/**: Interface de utilizador (ex: InterfaceUtilizador.java) para interação via terminal.

---

## common/

- **Logger.java / ErrorLogger.java**: Logging centralizado para debug e registo de erros.
- **PerformanceMetrics.java**: Mede latência, throughput, cache hits/misses, uptime. Singleton para métricas globais.
- **concurrency/**: ThreadPool customizado para gestão eficiente de threads.
- **dto/**: Objetos de transferência de dados (DTOs) para eventos, agregações, notificações, utilizadores, respostas, etc.
- **exceptions/**: Exceções customizadas para cada tipo de erro (ex: EventoException, ServicoException).
- **interfaces/**: Interfaces dos serviços (IServicoAdmin, IServicoAgregacoes, IServicoAutenticacao, IServicoEventos).

---

## middleware/

- **Message.java**: Estrutura de mensagem para requests/responses entre cliente e servidor.
- **MessageTypes.java**: Enumera todos os tipos de mensagens suportadas.
- **Protocolo.java**: Serialização/deserialização de mensagens, validação de tamanho, robustez contra corrupção.

---

## server/

- **Server.java**: Ponto de entrada do servidor. Inicializa pools de threads, dispatcher, monitor de deadlocks, aceita clientes e gere handlers.
- **DeadlockMonitor.java**: Deteta e resolve deadlocks periodicamente.
- **config/ServerConfig.java**: Centraliza parâmetros de configuração (portas, tamanhos de pools, passwords, etc).
- **business/domain/**: Modelos de domínio (Evento.java, Usuario.java, Agregacao.java).
- **business/services/**: Lógica de negócio dos serviços (Admin, Agregações, Autenticação, Eventos, NotificationManager).
- **data/cache/CacheManager.java**: Implementa cache de eventos para acelerar agregações e consultas.
- **data/repository/**: Persistência de dados em ficheiros. Repositórios para eventos e utilizadores, interfaces e fábrica de repositórios.
- **presentation/handlers/ClientHandler.java**: Gere cada cliente conectado, processa requests, autenticação e sincronização.
- **presentation/skeleton/**: Skeletons para cada serviço, processam requests e interagem com serviços reais. RequestDispatcher mapeia pedidos para skeletons.

---

## Fluxo Principal

1. **Cliente** inicia, conecta ao servidor via middleware, usa stubs para invocar serviços.
2. **Servidor** aceita clientes, cada um tratado por um ClientHandler numa thread do pool.
3. **RequestDispatcher** encaminha pedidos para o skeleton do serviço correto.
4. **Skeletons** processam pedidos, interagem com serviços, cache e repositórios.
5. **Serviços** executam lógica de negócio, persistem dados, atualizam cache e métricas.
6. **PerformanceMetrics** regista estatísticas globais.

---

## Decisões de Design

- **Pools de threads**: Escalabilidade e controlo de concorrência.
- **Locks**: Segurança em acesso concorrente.
- **Dispatcher + Skeletons**: Modularidade e fácil extensão.
- **Configuração centralizada**: Facilita tuning e manutenção.
- **Protocolo customizado**: Mais controlo sobre serialização, segurança.
- **Repository Pattern**: Isola persistência, facilita troca de backend.
- **Service Layer**: Centraliza lógica de negócio.
- **DTOs**: Separação clara entre dados e lógica.

---

## Possíveis Perguntas de Defesa

- Porquê usar pools de threads?
- Como é garantida a segurança concorrente?
- Qual o papel do dispatcher e dos skeletons?
- Como é feita a autenticação?
- Como o servidor lida com deadlocks?
- Porquê centralizar a configuração?
- Como é feita a monitorização de performance?
- Porquê usar cache?
- Como é feita a persistência?

---

## Sugestão de Estudo

- Analise cada ficheiro pelo seu papel na arquitetura.
- Foque nos fluxos de dados: cliente → middleware → servidor → dispatcher → skeleton → serviço → repositório/cache.
- Entenda como cada camada abstrai e protege a anterior.
- Prepare respostas para as perguntas acima, justificando cada decisão técnica.
