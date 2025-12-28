package server.business.services;

import common.dto.RespostaDTO;
import common.exceptions.AgregacaoException;
import common.interfaces.IServicoAgregacoes;
import server.business.domain.Agregacao;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;

/**
 * Serviço de agregações de vendas
 * Responsabilidade: Lógica de negócio para cálculo de agregações
 */
public class ServicoAgregacoes implements IServicoAgregacoes {
    private final CacheManager cacheManager;
    private final ServicoEventos servicoEventos;
    private final IEventoRepository eventoRepository;
    private final int D; // Janela máxima de dias
    
    public ServicoAgregacoes(CacheManager cacheManager, ServicoEventos servicoEventos, 
                             IEventoRepository eventoRepository, int D) {
        this.cacheManager = cacheManager;
        this.servicoEventos = servicoEventos;
        this.eventoRepository = eventoRepository;
        this.D = D;
    }
    
    @Override
    public RespostaDTO obterQuantidadeVendas(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        int quantidade = agregacao.getQuantidadeVendas();
        
        String mensagem = String.format(
            "Quantidade de Vendas nos últimos %d dias: %d", 
            dias, quantidade
        );
        
        return RespostaDTO.sucesso(mensagem, quantidade);
    }
    
    @Override
    public RespostaDTO obterVolumeVendas(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        double volume = agregacao.getVolumeVendas();
        
        String mensagem = String.format(
            "Volume de Vendas nos últimos %d dias: %.2f€", 
            dias, volume
        );
        
        return RespostaDTO.sucesso(mensagem, volume);
    }
    
    @Override
    public RespostaDTO obterPrecoMedio(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        double medio = agregacao.getPrecoMedio();
        
        String mensagem = String.format(
            "Preço Médio nos últimos %d dias: %.2f€", 
            dias, medio
        );
        
        return RespostaDTO.sucesso(mensagem, medio);
    }
    
    @Override
    public RespostaDTO obterPrecoMaximo(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        double maximo = agregacao.getPrecoMaximo();
        
        String mensagem = String.format(
            "Preço Máximo nos últimos %d dias: %.2f€", 
            dias, maximo
        );
        
        return RespostaDTO.sucesso(mensagem, maximo);
    }
    
    /**
     * Calcula agregação acumulada dos últimos N dias
     * Usa cache quando possível via CacheManager
     */
    private Agregacao calcularAgregacao(int produtoID, int dias) {
        int ultimoDia = eventoRepository.obterUltimoDia();
        
        if (ultimoDia < 0) {
            return new Agregacao();
        }
        
        int diasReais = Math.min(dias, ultimoDia + 1);
        
        // Acumular agregações dos últimos N dias
        Agregacao resultado = new Agregacao();
        
        for (int i = 0; i < diasReais; i++) {
            int dia = ultimoDia - i;
            if (dia < 0) break;
            
            // Obter agregação do dia via cache
            Agregacao agregacaoDia = cacheManager.obterAgregacaoDia(produtoID, dia);
            if (agregacaoDia != null) {
                resultado.acumular(agregacaoDia);
            }
        }
        
        resultado.updatePrecoMedio();
        return resultado;
    }
    
    private void validarParametros(int produtoID, int dias) throws AgregacaoException {
        // Validar produto
        if (produtoID <= 0) {
            throw new AgregacaoException("ID de produto inválido");
        }
        
        // Validar dias
        if (dias <= 0) {
            throw new AgregacaoException("Número de dias inválido");
        }
        
        if (dias > D) {
            throw new AgregacaoException(
                "Número de dias excede configuração do servidor (máx: " + D + ")"
            );
        }
        
        // Verificar histórico disponível
        int diaAtual = servicoEventos.getDiaAtual();
        int diasDisponiveis = diaAtual; // dias 0 até diaAtual-1
        
        if (dias > diasDisponiveis) {
            throw new AgregacaoException(
                "Número de dias excede histórico disponível (máx: " + diasDisponiveis + ")"
            );
        }
    }
}