.PHONY: all compile test test-fast test-single test-stress clean run-server run-client help

all: compile

compile:
	@echo "==> Compilar projeto "
	mvn clean compile

test:
	@echo "==> Executar TODOS os testes"
	mvn test

# Testes rápidos (exclui stress e integração)
test-fast:
	@echo "==> Executar testes rápidos (sem Stress/Integration)"
	mvn test -DskipStressTests=stress

# Executar um teste específico
# uso: make test-single TEST=ThreadPoolTest
test-single:
	@echo "==> Executar teste: $(TEST)"
	mvn -Dtest=$(TEST) test

# Executar apenas testes de stress
test-stress:
	@echo "==> Executar testes de stress"
	mvn -Dtest=StressTest test


run-server:
	@echo "==> Iniciar servidor"
	mvn -q exec:java -Dexec.mainClass="server.Server"

run-client:
	@echo "==> Iniciar cliente"
	mvn -q exec:java -Dexec.mainClass="client.Cliente"

clean:
	@echo "==> Limpar projeto"
	mvn clean
