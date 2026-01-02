#!/bin/bash

# Script para limpar dados de testes anteriores
# Evita conflitos entre execuções de teste

echo "Limpando dados de testes anteriores..."

# Ficheiros de teste do UsuarioRepository
rm -f dados/test_*.dat 2>/dev/null
rm -f dados/utilizadores.dat 2>/dev/null

# Diretórios de teste do EventoRepository  
rm -rf dados_teste_* 2>/dev/null

# Logs de teste
rm -f test-*.log 2>/dev/null

# Relatórios de teste antigos
rm -rf test-reports 2>/dev/null

echo "✓ Dados limpos!"