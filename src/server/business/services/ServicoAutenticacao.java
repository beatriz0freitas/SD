package server.business.services;

import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.exceptions.AutenticacaoException;
import common.interfaces.IServicoAutenticacao;
import middleware.security.PasswordHasher;
import server.business.domain.Usuario;
import server.config.ServerConfig;
import server.data.repository.IUsuarioRepository;
import server.data.repository.RepositoryFactory;

/**
 * Serviço de negócio para autenticação
 */
public class ServicoAutenticacao implements IServicoAutenticacao {
    private final IUsuarioRepository usuarioRepository;
    private final PasswordHasher hasher;
    private final String ADMIN_PASSWORD = ServerConfig.getAdminPassword();
    
    private static final int MIN_LENGTH = 3;
    
    public ServicoAutenticacao() {
        this.usuarioRepository = RepositoryFactory.getInstance().getUsuarioRepository();
        this.hasher = new PasswordHasher();
    }
    
    // Para testes - permite injetar dependências
    public ServicoAutenticacao(IUsuarioRepository repository, PasswordHasher hasher) {
        this.usuarioRepository = repository;
        this.hasher = hasher;
    }
    
    @Override
    public RespostaDTO registrar(UsuarioDTO dto) throws AutenticacaoException {
        // Validar
        validarCredenciais(dto);
        
        // Verificar se já existe
        if (usuarioRepository.existe(dto.getUsername())) {
            throw new AutenticacaoException("Username já existe");
        }
        
        // Criar e persistir
        String hash = hasher.hash(dto.getPassword());
        Usuario usuario = new Usuario(dto.getUsername(), hash);
        usuarioRepository.salvar(usuario);
        
        System.out.println("Novo utilizador registado: " + dto.getUsername());
        return RespostaDTO.sucesso("Utilizador registado com sucesso");
    }
    
    @Override
    public RespostaDTO autenticar(UsuarioDTO dto) throws AutenticacaoException {
        // Validar
        validarCredenciais(dto);
        
        // Buscar usuário
        Usuario usuario = usuarioRepository.buscar(dto.getUsername());
        if (usuario == null) {
            throw new AutenticacaoException("Credenciais inválidas");
        }
        
        // Verificar senha
        String hash = hasher.hash(dto.getPassword());
        if (!usuario.getPasswordHash().equals(hash)) {
            throw new AutenticacaoException("Credenciais inválidas");
        }
        
        System.out.println("Utilizador autenticado: " + dto.getUsername());
        return RespostaDTO.sucesso("Autenticação bem-sucedida", dto.getUsername());
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
    
    /**
     * Valida credenciais: não vazio, sem símbolos, mínimo de caracteres
     */
    private void validarCredenciais(UsuarioDTO dto) throws AutenticacaoException {
        if (dto == null) {
            throw new AutenticacaoException("Dados não fornecidos");
        }
        
        validarCampo(dto.getUsername(), "Username");
        validarCampo(dto.getPassword(), "Password");
    }
    
    private void validarCampo(String valor, String nomeCampo) throws AutenticacaoException {
        if (valor == null || valor.isBlank()) {
            throw new AutenticacaoException(nomeCampo + " não pode estar vazio");
        }
        
        if (valor.length() < MIN_LENGTH) {
            throw new AutenticacaoException(
                nomeCampo + " deve ter pelo menos " + MIN_LENGTH + " caracteres");
        }
        
        if (!valor.matches("^[a-zA-Z0-9]+$")) {
            throw new AutenticacaoException(
                nomeCampo + " só pode conter letras e números");
        }
    }
}