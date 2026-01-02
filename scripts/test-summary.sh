#!/bin/bash

# Script simples para executar testes e mostrar resumo visual

echo ""
echo "════════════════════════════════════════════════════════════"
echo "        EXECUTANDO TESTES DO SISTEMA"
echo "════════════════════════════════════════════════════════════"
echo ""

# Executar testes capturando output
mvn test > test-output.log 2>&1
EXIT_CODE=$?

# Extrair números do output do Maven
TESTS=$(grep "Tests run:" test-output.log | tail -1)

if [ -z "$TESTS" ]; then
    echo "⚠ Não foi possível obter resultados dos testes"
    echo "Verifique test-output.log para detalhes"
    exit 1
fi

# Parse dos números
RUN=$(echo "$TESTS" | grep -oP 'Tests run: \K\d+')
FAILURES=$(echo "$TESTS" | grep -oP 'Failures: \K\d+')
ERRORS=$(echo "$TESTS" | grep -oP 'Errors: \K\d+')
SKIPPED=$(echo "$TESTS" | grep -oP 'Skipped: \K\d+')

# Cálculos
PASSED=$((RUN - FAILURES - ERRORS - SKIPPED))
if [ "$RUN" -gt 0 ]; then
    SUCCESS_RATE=$(awk "BEGIN {printf \"%.1f\", ($PASSED * 100.0) / $RUN}")
else
    SUCCESS_RATE="0.0"
fi

# Mostrar resumo
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    RESUMO DOS TESTES                       ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
printf "  Total:        %4d testes\n" "$RUN"
printf "  ✓ Passou:     %4d  (%.1f%%)\n" "$PASSED" "$SUCCESS_RATE"
printf "  ✗ Falhou:     %4d\n" "$FAILURES"
printf "  ⚠ Erros:      %4d\n" "$ERRORS"
printf "  ⊘ Saltado:    %4d\n" "$SKIPPED"
echo ""

# Classes testadas
echo "Classes testadas:"
grep "Running testes\." test-output.log | sed 's/.*Running testes\./  • /'
echo ""

# Se houve falhas, mostrar quais
if [ "$FAILURES" -gt 0 ] || [ "$ERRORS" -gt 0 ]; then
    echo "════════════════════════════════════════════════════════════"
    echo "  ✗ Testes com falhas:"
    echo ""
    
    # Mostrar nome dos testes falhados
    grep -B 2 "<<< FAILURE\|<<< ERROR" test-output.log | grep "Running testes\." | sed 's/.*Running testes\./    - /' | sort -u
    
    echo ""
    echo "  Ver detalhes completos em: test-output.log"
    echo "════════════════════════════════════════════════════════════"
else
    echo "════════════════════════════════════════════════════════════"
    echo "             ✓✓✓ TODOS OS TESTES PASSARAM! ✓✓✓"
    echo "════════════════════════════════════════════════════════════"
fi

echo ""

# Retornar código de erro se testes falharam
exit $EXIT_CODE