# Sistema de Gestão de Vendas - Arquitetura Distribuída

Sistema cliente-servidor com arquitetura em camadas seguindo padrões de sistemas distribuídos.

## Estrutura do Projeto

```
src/
├── common/              # Código compartilhado
│   ├── dto/             # Data Transfer Objects
│   ├── interfaces/      # Interfaces remotas (contratos)
│   └── exceptions/      # Exceções personalizadas
│
├── client/              # Lado do cliente
│   ├── stub/            # Stubs
│   ├── middleware/      # Camada de comunicação
│   └── ui/              # Interface de utilizador
│
├── server/              # Lado do servidor
│   ├── business/        # Camada de negócio
│   │   ├── domain/      # Entidades de domínio
│   │   └── services/    # Serviços de negócio
│   │
|   |
│   ├── data/            # Camada de dados
│   │   ├── repository/  # Data Access Objects
│   │   └── cache/       # Sistema de cache
|   |
│   └── presentation/    # Camada de apresentação
│       ├── skeleton/    # Skeletons (servidores de objetos)
│       └── handlers/    # Handlers de requisições
│
└── middleware/          # Middleware compartilhado
    └── proto/           # Protocolo de comunicação

```

## Arquitetura

### Padrões Implementados

1. **Repository Pattern**: Abstração de persistência
2. **Stub Pattern**: Representação remota (Stubs)
3. **Skeleton Pattern**: Servidor de objetos remotos
4. **Service Layer**: Lógica de negócio isolada
5. **DTO Pattern**: Transferência de dados
6. **Factory Pattern**: Criação de Repositorys e Stubs

### Fluxo de Comunicação

```
Cliente                    Servidor
-------                    --------

UI Layer
   ↓
Stub (Stub)
   ↓
Client Middleware  →→→  Server Handler
                         ↓
                    Dispatcher
                         ↓
                    Skeleton
                         ↓
                    Service Layer
                         ↓
                    Repository Layer
                         ↓
                    Persistência
```

## Compilação

```bash
chmod +x build.sh
./build.sh
```

## Execução

### Iniciar Servidor

```bash
# Porta padrão (5001), D=30, S=5
java -cp bin server.Server

# Porta customizada
java -cp bin server.Server 8080

# Porta + parâmetros D e S
java -cp bin server.Server 8080 30 5
```

**Parâmetros:**

- `porta`: Porta do servidor (padrão: 5001)
- `D`: Número de dias anteriores a considerar (padrão: 30)
- `S`: Séries em memória (padrão: 5, deve ser < D)

### Iniciar Cliente

```bash
# Host e porta padrão (localhost:5001)
java -cp bin client.Cliente

# Host e porta customizados
java -cp bin client.Cliente localhost 8080

# Numero de clientes em simultaneo
java -cp bin client.ClienteTeste 20
```

## Persistência

### Dados de Utilizadores

- Ficheiro: `dados/utilizadores.dat`
- Formato: Binário (DataOutputStream)

### Dados de Eventos

- Pasta: `dados/eventos/`
- Ficheiros: `eventos_dia_N.dat`
- Formato: Binário com estrutura:
  ```
  [numProdutos:int]
  Para cada produto:
    [produtoID:int]
    [numEventos:int]
    Para cada evento:
      [quantidade:int]
      [preco:double]
  ```

## Comparação

### Antes

```
Problemas:
- BibliotecaCliente fazia tudo (stub + middleware + protocolo)
- GestorUtilizadores misturava lógica + persistência
- ClientHandler processava tudo diretamente
- Protocolo manual complexo e frágil
- Difícil testar e manter
```

### Depois

```
Vantagens:
-Separação clara de responsabilidades
-Stubs representam serviços remotos
-Skeletons delegam para Services
-Repositorys isolam persistência
-Services contêm apenas lógica de negócio
-Fácil testar cada camada isoladamente
-Fácil adicionar novos serviços
-Preparado para escalar
```

---

**Nota**: O sistema segue a arquitetura de sistemas distribuídos com Stubs/Skeletons, como a Java RMI mas com implementação do
protocolo

Uso de DTOs
Os DTOs são usados para desacoplar cliente e servidor, permitindo que cada lado evolua independentemente. Enviam apenas dados necessários pela rede (não estruturas internas completas), reduzindo o payload e protegendo informação sensível. Facilitam validação centralizada, tornam a API clara e autodocumentada, e garantem que mudanças no servidor não quebrem o cliente.

OBRIGATÓRIO
//TODO Falta a parte das notificações

Alta perioridade
//TODO melhorar tratamento de erros (criar mais exceções e guardar a stack tree )
//TODO mais concorrencia

Média perioridade
//TODO fazer o nosso proprio threadpoll (nao acho necessario )
//TODO Pool de conexões reutilizáveis (cada midleware do cliente gera uma nova conexcao)
//TODO Logging estruturado
//TODO ajustar interface (tem muitas responsabilidades)
