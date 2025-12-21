package server.business.validators;

import common.dto.EventoDTO;
import common.exceptions.DadosInvalidosException;

/**
 * Validador de regras de negócio para eventos
 */
public class EventoValidator {
    
    private static final int MIN_PRODUTO_ID = 1;
    private static final int MAX_PRODUTO_ID = 100000;
    private static final int MIN_QUANTIDADE = 1;
    private static final double MIN_PRECO = 0.01;
    private static final double MAX_PRECO = 1000000.0;
    
    /**
     * Valida dados de um evento
     */
    public void validar(EventoDTO dto) throws DadosInvalidosException {
        if (dto == null) {
            throw new DadosInvalidosException("Evento não fornecido");
        }
        
        validarProdutoID(dto.getProdutoID());
        validarQuantidade(dto.getQuantidade());
        validarPreco(dto.getPreco());
    }
    
    private void validarProdutoID(int produtoID) throws DadosInvalidosException {
        if (produtoID < MIN_PRODUTO_ID) {
            throw new DadosInvalidosException(
                "ID de produto deve ser pelo menos " + MIN_PRODUTO_ID);
        }
        
        if (produtoID > MAX_PRODUTO_ID) {
            throw new DadosInvalidosException(
                "ID de produto não pode exceder " + MAX_PRODUTO_ID);
        }
    }
    
    private void validarQuantidade(int quantidade) throws DadosInvalidosException {
        if (quantidade < MIN_QUANTIDADE) {
            throw new DadosInvalidosException(
                "Quantidade deve ser pelo menos " + MIN_QUANTIDADE);
        }
    }
    
    private void validarPreco(double preco) throws DadosInvalidosException {
        if (preco < MIN_PRECO) {
            throw new DadosInvalidosException(
                String.format("Preço deve ser pelo menos %.2f", MIN_PRECO));
        }
        
        if (preco > MAX_PRECO) {
            throw new DadosInvalidosException(
                String.format("Preço não pode exceder %.2f", MAX_PRECO));
        }
        
        // Verificar se é um número válido
        if (Double.isNaN(preco) || Double.isInfinite(preco)) {
            throw new DadosInvalidosException("Preço inválido");
        }
    }
}