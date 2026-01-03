package common.interfaces;

import common.dto.RespostaDTO;
import common.exceptions.AdminException;

/**
 * Interface remota para operações administrativas
 */
public interface IServicoAdmin {
    
    /**
     * Lista todos os clientes registados
     * @return lista de utilizadores
     * @throws AdminException se não autorizado
     */
    RespostaDTO listarClientes() throws AdminException;
    
    /**
     * Lista estatísticas do sistema
     * @return estatísticas gerais
     * @throws AdminException se não autorizado
     */
    RespostaDTO obterEstatisticas() throws AdminException;

    /**
     * Lista métricas de desempenho do sistema
     * @return métricas de desempenho
     * @throws AdminException se não autorizado
     */
    RespostaDTO obterMetricas() throws AdminException;
}