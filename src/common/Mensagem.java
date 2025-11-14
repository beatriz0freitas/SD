package src.common;

//Estrutura header + payload da mensagem
//Representa mensagens enviadas entre cliente e servidor, encapsulando dados e tipos de operação.
public class Mensagem {

    public enum TipoOperacao {
        REGISTO,
        LOGIN,
        REG_EVENTO,
        AGREGACAO_INFO,
        FILTRAR,
        NOTIFICACAO,
        SUPORTE,
        DESCONHECIDO
    }

    private TipoOperacao tipoOperacao;
    private byte[] payload;             //TODO: Não sei se querem usar assim ou outro tipo de estrutura.


    public Mensagem(TipoOperacao tipoOperacao, byte[] payload) {
        this.tipoOperacao = tipoOperacao;
        this.payload = payload;
    }
    
    public TipoOperacao getTipoOperacao() {
        return tipoOperacao;
    }

    public byte[] getPayload() {
        return payload;
    }
}

