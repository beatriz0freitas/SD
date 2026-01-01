package server.presentation.skeleton;

import static middleware.MessageTypes.*;

import common.dto.AgregacaoRequestDTO;
import common.dto.RespostaDTO;
import server.business.services.ServicoAgregacoes;

public class ServicoAgregacoesSkeleton implements ISkeleton {
    private final ServicoAgregacoes servico; 

    public ServicoAgregacoesSkeleton(ServicoAgregacoes servico) {
        this.servico = servico;
    }

    @Override
    public RespostaDTO processarRequisicao(byte methodId, Object parametros) {
        try {
            if (!(parametros instanceof AgregacaoRequestDTO)) {
                return RespostaDTO.erro("Parâmetros inválidos para agregação");
            }
            AgregacaoRequestDTO dto = (AgregacaoRequestDTO) parametros;
            int produtoID = dto.getProdutoID();
            int dias = dto.getDias();

            switch (methodId) {
                case AGREG_QTD:
                    return servico.obterQuantidadeVendas(produtoID, dias);
                case AGREG_VOLUME:
                    return servico.obterVolumeVendas(produtoID, dias);
                case AGREG_PRECO_MEDIO:
                    return servico.obterPrecoMedio(produtoID, dias);
                case AGREG_PRECO_MAXIMO:
                    return servico.obterPrecoMaximo(produtoID, dias);
                default:
                    return RespostaDTO.erro("Método desconhecido: " + methodId);
            }
        } catch (Exception e) {
            return RespostaDTO.erro("Erro no servidor: " + e.getMessage());
        }
    }
}