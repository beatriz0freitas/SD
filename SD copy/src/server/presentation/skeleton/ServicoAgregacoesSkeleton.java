package server.presentation.skeleton;

import common.dto.AgregacaoRequestDTO;
import common.dto.RespostaDTO;
import server.business.services.ServicoAgregacoes;

public class ServicoAgregacoesSkeleton implements ISkeleton {
    private final ServicoAgregacoes servico;
    
    public ServicoAgregacoesSkeleton(ServicoAgregacoes servico) {
        this.servico = servico;
    }
    
    @Override
    public RespostaDTO processarRequisicao(String operacao, Object parametros) {
        try {
            // Validação type-safe
            if (!(parametros instanceof AgregacaoRequestDTO)) {
                return RespostaDTO.erro("Parâmetros inválidos para agregação");
            }
            
            AgregacaoRequestDTO params = (AgregacaoRequestDTO) parametros;
            int produtoID = params.getProdutoID();
            int dias = params.getDias();
            
            switch (operacao) {
                case "AGREGACAO:QUANTIDADE":
                    return servico.obterQuantidadeVendas(produtoID, dias);
                    
                case "AGREGACAO:VOLUME":
                    return servico.obterVolumeVendas(produtoID, dias);
                    
                case "AGREGACAO:PRECO_MEDIO":
                    return servico.obterPrecoMedio(produtoID, dias);
                    
                case "AGREGACAO:PRECO_MAXIMO":
                    return servico.obterPrecoMaximo(produtoID, dias);
                    
                default:
                    return RespostaDTO.erro("Operação desconhecida: " + operacao);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}