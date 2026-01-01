package server.business.services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import common.dto.RespostaDTO;
import common.dto.UsuarioDTO;
import common.exceptions.AutenticacaoException;
import common.interfaces.IServicoAutenticacao;
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

    private static final int MIN_LENGTH = ServerConfig.MIN_USERNAME_LENGTH;

    public ServicoAutenticacao() {
        this.usuarioRepository = RepositoryFactory.getInstance().getUsuarioRepository();
        this.hasher = new PasswordHasher();
    }

    @Override
    public RespostaDTO registrar(UsuarioDTO dto) throws AutenticacaoException {
        validarCredenciais(dto);
        if (usuarioRepository.existe(dto.getUsername())) {
            throw new AutenticacaoException("Username já existe");
        }
        String hash = hasher.hash(dto.getPassword());
        Usuario usuario = new Usuario(dto.getUsername(), hash);
        usuarioRepository.salvar(usuario);
        System.out.println("Novo utilizador registado: " + dto.getUsername());
        return RespostaDTO.sucesso("Utilizador registado com sucesso");
    }

    @Override
    public RespostaDTO autenticar(UsuarioDTO dto) throws AutenticacaoException {
        validarCredenciais(dto);
        Usuario usuario = usuarioRepository.buscar(dto.getUsername());
        if (usuario == null || !usuario.getPasswordHash().equals(hasher.hash(dto.getPassword()))) {
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


    // =========== Validações ============
    
    private void validarCredenciais(UsuarioDTO dto) throws AutenticacaoException {
        if (dto == null) throw new AutenticacaoException("Dados não fornecidos");
        validarCampo(dto.getUsername(), "Username");
        validarCampo(dto.getPassword(), "Password");
    }

    private void validarCampo(String valor, String nomeCampo) throws AutenticacaoException {
        if (valor == null || valor.isBlank())
            throw new AutenticacaoException(nomeCampo + " não pode estar vazio");
        if (valor.length() < MIN_LENGTH)
            throw new AutenticacaoException(nomeCampo + " deve ter pelo menos " + MIN_LENGTH + " caracteres");
        if (!valor.matches("^[a-zA-Z0-9]+$"))
            throw new AutenticacaoException(nomeCampo + " só pode conter letras e números");
    }



    // =========== Hash de Senha ============

    /**
     * Hash interno de senhas
     */
    private static class PasswordHasher {
        public String hash(String password) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = md.digest(password.getBytes());
                return Base64.getEncoder().encodeToString(hashBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Algoritmo SHA-256 não disponível", e);
            }
        }
    }
}
