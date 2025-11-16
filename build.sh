#!/bin/bash

# Pasta de destino das classes compiladas - bin

# Limpar compilação antiga
rm -rf "bin" "Servidor.jar" "Cliente.jar"
mkdir -p "bin"

# Compilar todas as classes
javac -d "bin" src/uteis/*.java src/servidor/*.java src/cliente/*.java

# Criar manifests apontando para o package completo
echo "Main-Class: src.servidor.Servidor" > manifest_server.txt
echo "Main-Class: src.cliente.Cliente" > manifest_cliente.txt

# Criar os JARs
jar cfm "Servidor.jar" manifest_server.txt -C "bin" .
jar cfm "Cliente.jar" manifest_cliente.txt -C "bin" .

# Limpar manifests temporários
rm manifest_server.txt manifest_cliente.txt

echo "Build concluído!"
echo "Execute: java -jar "Servidor.jar" para iniciar o servidor"
echo "         java -jar "Cliente.jar" para iniciar o cliente"

#temos de arranjar forma de dar kill à porta utilizada - penso que está sempre a dar estrilho por causa disso