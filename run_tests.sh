#!/bin/bash

# Script para compilar e executar testes
# Uso: ./run_tests.sh [unit|integration|all]

set -e

# Modo de teste (padrão: all)
TEST_MODE=${1:-all}

# Validar modo
if [[ ! "$TEST_MODE" =~ ^(unit|integration|all)$ ]]; then
    echo -e "✗ Modo inválido: $TEST_MODE"
    echo "Uso: $0 [unit|integration|all]"
    exit 1
fi

echo -e "Modo de teste: $TEST_MODE"
echo ""

# Limpar compilações anteriores
echo "▶ Limpar builds anteriores..."
rm -rf bin
mkdir -p bin

# Compilar código
echo ""
echo "▶ Compilar código-fonte..."

# Encontrar todos os arquivos .java
SOURCES=$(find src -name "*.java")

if [ -z "$SOURCES" ]; then
    echo -e "✗ Nenhum arquivo .java encontrado em src/"
    exit 1
fi

# Compilar
javac -d bin -sourcepath src $SOURCES

if [ $? -eq 0 ]; then
    echo -e "✓ Compilação bem-sucedida"
else
    echo -e "✗ Erro na compilação"
    exit 1
fi

# Criar diretório de dados se não existir
mkdir -p dados

# Executar testes
echo ""
echo "─────────────────────────────────────────────────"
echo "▶ Executando testes..."
echo "─────────────────────────────────────────────────"
echo ""

java -cp bin tests.TestRunner $TEST_MODE

# Capturar código de saída
TEST_RESULT=$?

echo ""
echo "─────────────────────────────────────────────────"

if [ $TEST_RESULT -eq 0 ]; then
    echo -e "✓ Todos os testes passaram!"
    echo "─────────────────────────────────────────────────"
    exit 0
else
    echo -e "✗ Alguns testes falharam"
    echo "─────────────────────────────────────────────────"
    exit 1
fi