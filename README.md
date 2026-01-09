# Sistema Distribuído (SD)

## Descrição do Projeto

Este projeto consiste na implementação de um sistema distribuído para a gestão de eventos de vendas de produtos no tempo. A solução foi desenhada para suportar carga elevada de requisições, fornecendo ferramentas eficientes para registar eventos, consultar dados históricos, e realizar agregações sobre séries temporais.

O projeto adota uma arquitetura cliente-servidor distribuída, estruturada em camadas, para assegurar modularidade e escalabilidade. Além disso, padrões como \textit{Stub/Skeleton}, \textit{Repository} e \textit{Service Layer} foram usados para promover um design sólido e de fácil manutenção.

---

## Principais Funcionalidades

- **Registo de Eventos:** Permite o registo contínuo de eventos relacionados às vendas.
- **Consultas Históricas:** Suporte a consultas eficientes sobre dados de vendas passadas.
- **Agregações Estatísticas:** Cálculos como vendas totais ou médias em períodos arbitrários.
- **Notificações Assíncronas:** Sistema de notificações baseado em condições específicas (ex.: vendas consecutivas).
- **Gestão de Concorrência:** Implementação de um \textit{ThreadPool} personalizado para gerenciar múltiplas requisições.
- **Cache Multi-Nível:** Utilização de políticas como LRU para otimizar leitura e escrita.

---

### Compilação
- **Compilar o projeto:**
  ```bash
  make compile
  ```
  Este comando utiliza o Maven para realizar a compilação e limpeza do histórico.

### Testes
- **Executar todos os testes automatizados:**
  ```bash
  make test
  ```

- **Executar um teste específico:**
  ```bash
  make test-single TEST=NomeDoTeste
  ```
  Substitua `NomeDoTeste` pelo nome exato do teste (ex.: `ThreadPoolTest`).

- **Resumo visual dos testes:**
  ```bash
  make test-summary
  ```

### Execução
- **Iniciar o servidor:**
  ```bash
  make run-server
  ```
- **Iniciar o cliente:**
  ```bash
  make run-client
  ```

### Limpeza
- **Limpar o histórico e os arquivos temporários do projeto:**
  ```bash
  make clean
  ```

---

## Contribuidores

- Ana Beatriz Freitas
- Lucas André Dias Fernandes
- João Azevedo
- José Miguel Cação