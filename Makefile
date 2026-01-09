.PHONY: all compile test test-fast test-single test-stress test-summary clean run-server run-client help

all: compile

compile:
	@echo "==> Compilar projeto "
	mvn clean compile

test:
	@echo "==> Executar TODOS os testes"
	mvn test

# Executar um teste específico
# uso: make test-single TEST=ThreadPoolTest
test-single:
	@echo "==> Executar teste: $(TEST)"
	mvn -Dtest=$(TEST) test

# Executar testes com resumo visual
test-summary:
	@bash scripts/clean-test-data.sh
	@bash scripts/test-summary.sh

run-server:
	@echo "==> Iniciar servidor"
	mvn -q exec:java -Dexec.mainClass="server.Server"

run-client:
	@echo "==> Iniciar cliente"
	mvn -q exec:java -Dexec.mainClass="client.Cliente"

clean:
	@echo "==> Limpar projeto"
	@bash scripts/clean-test-data.sh
	rm -f dados/test_usuarios*.dat
	rm -rf dados_teste_*
	mvn clean
