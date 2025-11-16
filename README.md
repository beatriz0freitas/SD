# Sistemas Distribuídos - Base de Dados para Séries Temporais

Implementação de um serviço de registo de eventos em séries temporais e de agregação de informação, relativos a venda de produtos, em que a informação é mantida num servidor e acedida remotamente. Clientes interagem com o servidor através de _sockets_ TCP, de forma a inserir e consultar informação. O servidor atende clientes concorrentemente e armazena a informação.

- a106853 | Ana Beatriz Ribeiro Freitas
- a107367 | João Paulo Batista Azevedo
- xxxx | xxxx
- xxxx | xxxx

[DOCS COM NOTAS](https://docs.google.com/document/d/1M32_J4vlkWb8rJwJ5daaxH544i71WUa1bKPcfZu8ftM/edit?usp=sharing) (TIRAR DEPOIS)

## Compilação e Execução

```bash
./build.sh
java -jar Servidor.jar
java -jar Cliente.jar
```

## Inicialização do Servidor

O servidor inicia automaticamente e retoma o estado de dias anteriores:
- **Com eventos em disco**: O `GestorEventos` inicia com o `diaAtual` definido como o dia seguinte ao último evento registado em disco (dados/eventos).
- **Sem eventos em disco**: O `diaAtual` começa em 0.

Isto garante que, após reinicialização do sistema, não existem duplicações ou inconsistências no fim de dia.
