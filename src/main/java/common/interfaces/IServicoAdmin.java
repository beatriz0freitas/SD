package common.interfaces;

import common.dto.RespostaDTO;
import common.exceptions.AdminException;


public interface IServicoAdmin {
    
    
    RespostaDTO listarClientes() throws AdminException;
    
    
    RespostaDTO obterEstatisticas() throws AdminException;

    
    RespostaDTO obterMetricas() throws AdminException;
}