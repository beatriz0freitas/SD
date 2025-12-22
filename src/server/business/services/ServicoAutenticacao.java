package server.business.services;

import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.exceptions.AutenticacaoException;
import common.exceptions.DadosInvalidosException; // Adicionar este import
import common.interfaces.IServicoAutenticacao;
import middleware.security.PasswordHasher;
import server.business.domain.Usuario;
import server.business.validators.UsuarioValidator;
import server.config.ServerConfig;
import server.data.repository.IUsuarioRepository;
import server.data.repository.RepositoryFactory;

/**
 * Serviço de negócio para autenticação
 */
public class ServicoAutenticacao implements IServicoAutenticacao {
    private final IUsuarioRepository usuarioRepository;
    private final UsuarioValidator validator;
    private final PasswordHasher hasher;
    private final String ADMIN_PASSWORD = ServerConfig.getAdminPassword();

    public ServicoAutenticacao() {
        this.usuarioRepository = RepositoryFactory.getInstance().getUsuarioRepository();
        this.validator = new UsuarioValidator();
        this.hasher = new PasswordHasher();
    }

    // Para testes - permite injetar dependências
    public ServicoAutenticacao(IUsuarioRepository repository, UsuarioValidator validator, PasswordHasher hasher) {
        this.usuarioRepository = repository;
        this.validator = validator;
        this.hasher = hasher;
    }

    @Override
    public RespostaDTO registrar(UsuarioDTO dto) throws AutenticacaoException {
        try {
            // 1. Validar entrada
            validator.validarRegistro(dto);
            
            // 2. Verificar se já existe
            if (usuarioRepository.existe(dto.getUsername())) {
                throw new AutenticacaoException("Username já existe");
            }
            
            // 3. Criar entidade de domínio
            String hash = hasher.hash(dto.getPassword());
            Usuario usuario = new Usuario(dto.getUsername(), hash);
            
            // 4. Persistir
            usuarioRepository.salvar(usuario);
            System.out.println("Novo utilizador registado: " + dto.getUsername());
            
            return RespostaDTO.sucesso("Utilizador registado com sucesso");
            
        } catch (DadosInvalidosException e) {
            // Converter exceção de validação em exceção de autenticação
            throw new AutenticacaoException(e.getMessage());
        }
    }

    @Override
    public RespostaDTO autenticar(UsuarioDTO dto) throws AutenticacaoException {
        try {
            // 1. Validar entrada
            validator.validarAutenticacao(dto);
            
            // 2. Buscar usuário
            Usuario usuario = usuarioRepository.buscar(dto.getUsername());
            if (usuario == null) {
                throw new AutenticacaoException("Credenciais inválidas");
            }
            
            // 3. Verificar senha
            String hash = hasher.hash(dto.getPassword());
            if (!usuario.getPasswordHash().equals(hash)) {
                throw new AutenticacaoException("Credenciais inválidas");
            }
            
            System.out.println("Utilizador autenticado: " + dto.getUsername());
            return RespostaDTO.sucesso("Autenticação bem-sucedida", dto.getUsername());
            
        } catch (DadosInvalidosException e) {
            // Converter exceção de validação em exceção de autenticação
            throw new AutenticacaoException(e.getMessage());
        }
    }

    @Override
    public RespostaDTO autenticarAdmin(String password) throws AutenticacaoException {
        if (password == null || password.isBlank()) {
            throw new AutenticacaoException("Password inválida");
        }
        
        if (!ADMIN_PASSWORD.equals(password)) {
            throw new AutenticacaoException("Senha de administrador inválida");
        }
        
        System.out.println("Administrador autenticado");
        return RespostaDTO.sucesso("Login de administrador bem-sucedido", "ADMIN");
    }
}