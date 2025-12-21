#!/bin/bash

echo "Limpando build anterior..."
rm -rf bin
mkdir -p bin
mkdir -p dados/eventos

echo "Compilando código..."
javac -d bin -sourcepath src $(find src -name "*.java")

if [ $? -eq 0 ]; then
    echo "✓ Compilação bem-sucedida!"
else
    echo "✗ Erro na compilação"
    exit 1
fi