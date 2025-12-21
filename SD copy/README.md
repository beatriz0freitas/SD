# Sistema de Gestão de Vendas - Arquitetura Distribuída

Sistema cliente-servidor com arquitetura em camadas seguindo padrões de sistemas distribuídos.

## Estrutura do Projeto

```
src/
├── common/              # Código compartilhado
│   ├── dto/            # Data Transfer Objects
│   ├── interfaces/     # Interfaces remotas (contratos)
│   └── exceptions/     # Exceções personalizadas
│
├── client/             # Lado do cliente
│   ├── stub/         # Stubs 
│   ├── middleware/    # Camada de comunicação
│   └── ui/           # Interface de utilizador
│
├── server/            # Lado do servidor
│   ├── business/      # Camada de negócio
│   │   ├── domain/   # Entidades de domínio
│   │   ├── services/ # Serviços de negócio
│   │   └── validators/ # Validadores
│   ├── data/         # Camada de dados
│   │   ├── dao/     # Data Access Objects
│   │   └── cache/   # Sistema de cache
│   └── presentation/ # Camada de apresentação
│       ├── skeleton/ # Skeletons (servidores de objetos)
│       └── handlers/ # Handlers de requisições
│
└── middleware/        # Middleware compartilhado
    ├── protocol/     # Protocolo de comunicação
    └── security/     # Segurança (hash de senhas)
```

## Arquitetura

### Padrões Implementados

1. **DAO Pattern**: Abstração de persistência
2. **Stub Pattern**: Representação remota (Stubs)
3. **Skeleton Pattern**: Servidor de objetos remotos
4. **Service Layer**: Lógica de negócio isolada
5. **DTO Pattern**: Transferência de dados
6. **Factory Pattern**: Criação de DAOs e Stubs

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
                    DAO Layer
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
java -cp bin server.Servidor

# Porta customizada
java -cp bin server.Servidor 8080

# Porta + parâmetros D e S
java -cp bin server.Servidor 8080 30 5
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

## Testes

### Cliente de Teste de Concorrência

Criar `ClienteTeste.java` (a adaptar para nova arquitetura):

```java
public class ClienteTeste {
    public static void main(String[] args) {
        String host = "localhost";
        int porta = 5001;
        int numThreads = 10;

        for (int i = 0; i < numThreads; i++) {
            final int id = i;
            new Thread(() -> {
                ClienteMiddleware mw = new ClienteMiddleware(host, porta);
                StubFactory factory = new StubFactory(mw);

                try {
                    mw.conectar();

                    IServicoAgregacoes servico = factory.criarStubAgregacoes();

                    for (int j = 0; j < 100; j++) {
                        RespostaDTO resp = servico.obterQuantidadeVendas(1, 5);
                        System.out.println("Thread " + id + ": " + resp.getMensagem());
                        Thread.sleep(100);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mw.desconectar();
                }
            }).start();
        }
    }
}
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
-DAOs isolam persistência
-Services contêm apenas lógica de negócio
-Fácil testar cada camada isoladamente
-Fácil adicionar novos serviços
-Preparado para escalar
```

---

**Nota**: O sistema segue a arquitetura de sistemas distribuídos com Stubs/Skeletons, como a Java RMI mas com implementação do protocolo

//TODO guardar a stack tree nas excecoes
//TODO fazer o nosso proprio threadpoll (nao acho necessario )
