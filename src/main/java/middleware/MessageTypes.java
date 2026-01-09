package middleware;

public final class MessageTypes {
    private MessageTypes() {}

    
    public static final byte SERVICO_AUTENTICACAO = 1;
    public static final byte SERVICO_EVENTOS      = 2;
    public static final byte SERVICO_AGREGACOES   = 3;
    public static final byte SERVICO_ADMIN        = 4;

    
    public static final byte AUTH_REGISTRAR       = 1;
    public static final byte AUTH_LOGIN           = 2;
    public static final byte AUTH_LOGIN_ADMIN     = 3;

    
    public static final byte EVENTO_REGISTRAR                     = 1;
    public static final byte EVENTO_LISTAR                        = 2;
    public static final byte EVENTO_NOVO_DIA                      = 3;
    public static final byte EVENTO_NOTIFICAR_VENDA_ESPECIFICA    = 4;
    public static final byte EVENTO_NOTIFICAR_VENDAS_CONSECUTIVAS = 5;
    public static final byte EVENTO_FILTRAR                       = 6;

    
    public static final byte AGREG_QTD            = 1;
    public static final byte AGREG_VOLUME         = 2;
    public static final byte AGREG_PRECO_MEDIO    = 3;
    public static final byte AGREG_PRECO_MAXIMO   = 4;
    public static final byte AGREG_COMPLETA       = 5;

    
    public static final byte ADMIN_LISTAR_CLIENTES = 1;
    public static final byte ADMIN_ESTATISTICAS    = 2;
    public static final byte ADMIN_METRICAS        = 3;
}
