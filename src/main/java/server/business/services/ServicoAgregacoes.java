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
     * Agregação Multi-Dia usando Repository Streaming
     */
    private Agregacao calcularAgregacao(int produtoID, int dias) {
        int ultimoDia = eventoRepository.obterUltimoDia();
        
        if (ultimoDia < 0) {
            return new Agregacao();
        }
        
        int diasReais = Math.min(dias, ultimoDia + 1);
        int diaInicio = ultimoDia - diasReais + 1;
        int diaFim = ultimoDia;
        
        // ===== ESTRATÉGIA HÍBRIDA: Cache + Streaming =====
        Agregacao resultado = new Agregacao();
        
        // Tentar usar cache para dias individuais (se já calculado)
        int diasNaoCache = 0;
        for (int dia = diaInicio; dia <= diaFim; dia++) {
            Agregacao cached = tentarCache(produtoID, dia);
            if (cached != null) {
                resultado.acumular(cached);
            } else {
                diasNaoCache++;
            }
        }

        // Se muitos dias sem cache, usar streaming direto
        if (diasNaoCache > 5) {
            System.out.println("Muitos dias sem cache (" + diasNaoCache + ") - usando streaming multi-dia");

            // Streaming otimizado (não passa por cache)
            Agregacao streamingResult = eventoRepository.agregarEventosMultiDia(
                produtoID, diaInicio, diaFim
            );
            return streamingResult;
        }

        // Poucos dias sem cache - calcular individualmente e cachear
        for (int dia = diaInicio; dia <= diaFim; dia++) {
            Agregacao cached = tentarCache(produtoID, dia);
            if (cached == null) {
                // Calcular e cachear este dia
                cached = cacheManager.obterAgregacaoDia(produtoID, dia);
                resultado.acumular(cached);
            }
        }

        resultado.updatePrecoMedio();
        return resultado;
    }
    
    /**
     * Tenta obter agregação do cache SEM calcular
     */
    private Agregacao tentarCache(int produtoID, int dia) {
        return cacheManager.getAgregacaoSeExistir(produtoID, dia);
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