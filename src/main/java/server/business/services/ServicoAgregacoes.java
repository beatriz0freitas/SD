package server.business.services;

import common.dto.RespostaDTO;
import common.exceptions.AgregacaoException;
import common.interfaces.IServicoAgregacoes;
import server.business.domain.Agregacao;
import server.data.cache.CacheManager;
import server.data.repository.IEventoRepository;

public class ServicoAgregacoes implements IServicoAgregacoes {
    private final CacheManager cacheManager;
    private final ServicoEventos servicoEventos;
    private final IEventoRepository eventoRepository;
    private final int D;

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
        return RespostaDTO.sucesso(String.format(
                "Quantidade de Vendas nos últimos %d dias: %d",
                dias, agregacao.getQuantidadeVendas()
        ));
    }

    @Override
    public RespostaDTO obterVolumeVendas(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        return RespostaDTO.sucesso(String.format(
                "Volume de Vendas nos últimos %d dias: %.2f€",
                dias, agregacao.getVolumeVendas()
        ));
    }

    @Override
    public RespostaDTO obterPrecoMedio(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        return RespostaDTO.sucesso(String.format(
                "Preço Médio nos últimos %d dias: %.2f€",
                dias, agregacao.getPrecoMedio()
        ));
    }

    @Override
    public RespostaDTO obterPrecoMaximo(int produtoID, int dias) throws AgregacaoException {
        validarParametros(produtoID, dias);
        Agregacao agregacao = calcularAgregacao(produtoID, dias);
        return RespostaDTO.sucesso(String.format(
                "Preço Máximo nos últimos %d dias: %.2f€",
                dias, agregacao.getPrecoMaximo()
        ));
    }

    
    private Agregacao calcularAgregacao(int produtoID, int dias) {
        int ultimoDia = eventoRepository.obterUltimoDia();
        if (ultimoDia < 0) return new Agregacao();

        int diasReais = Math.min(dias, ultimoDia + 1);
        int diaInicio = ultimoDia - diasReais + 1;
        int diaFim = ultimoDia;

        Agregacao resultado = new Agregacao();

        int diasNaoCache = 0;
        for (int dia = diaInicio; dia <= diaFim; dia++) {
            Agregacao cached = tentarCache(produtoID, dia);
            if (cached != null) resultado.acumular(cached);
            else diasNaoCache++;
        }

        if (diasNaoCache > 5) {
            Agregacao streamingResult = eventoRepository.agregarEventosMultiDia(produtoID, diaInicio, diaFim);
            streamingResult.updatePrecoMedio();
            return streamingResult;
        }

        for (int dia = diaInicio; dia <= diaFim; dia++) {
            Agregacao cached = tentarCache(produtoID, dia);
            if (cached == null) {
                cached = cacheManager.obterAgregacaoDia(produtoID, dia);
                resultado.acumular(cached);
            }
        }

        resultado.updatePrecoMedio();
        return resultado;
    }

    private Agregacao tentarCache(int produtoID, int dia) {
        return cacheManager.getAgregacaoSeExistir(produtoID, dia);
    }

    private void validarParametros(int produtoID, int dias) throws AgregacaoException {
        if (produtoID <= 0) throw new AgregacaoException("ID de produto inválido");
        if (dias <= 0) throw new AgregacaoException("Número de dias inválido");
        if (dias > D) throw new AgregacaoException("Número de dias excede configuração do servidor (máx: " + D + ")");

        int diaAtual = servicoEventos.getDiaAtual();
        int diasDisponiveis = diaAtual;
        if (dias > diasDisponiveis) {
            throw new AgregacaoException("Número de dias excede histórico disponível (máx: " + diasDisponiveis + ")");
        }
    }
}