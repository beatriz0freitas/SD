#!/bin/bash

# Pasta de destino das classes compiladas
BIN_DIR="bin"

# Nomes dos JARs
JAR_SERVIDOR="Servidor.jar"
JAR_CLIENTE="Cliente.jar"

# Limpar compilação antiga
rm -rf $BIN_DIR $JAR_SERVIDOR $JAR_CLIENTE
mkdir -p $BIN_DIR

# Compilar todas as classes
javac -d $BIN_DIR src/common/*.java src/server/*.java src/client/*.java

# Criar manifests apontando para o package completo
echo "Main-Class: src.server.Servidor" > manifest_server.txt
echo "Main-Class: src.client.Cliente" > manifest_cliente.txt

# Criar os JARs
jar cfm $JAR_SERVIDOR manifest_server.txt -C $BIN_DIR .
jar cfm $JAR_CLIENTE manifest_cliente.txt -C $BIN_DIR .

# Limpar manifests temporários
rm manifest_server.txt manifest_cliente.txt

echo "Build concluído!"
echo "Execute: java -jar $JAR_SERVIDOR para iniciar o servidor"
echo "         java -jar $JAR_CLIENTE para iniciar o cliente"
