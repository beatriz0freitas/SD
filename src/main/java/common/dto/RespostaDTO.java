    package common.dto;

    import java.io.ByteArrayInputStream;
    import java.io.ByteArrayOutputStream;
    import java.io.DataInputStream;
    import java.io.DataOutputStream;
    import java.io.IOException;
    import java.io.Serializable;

    
    public class RespostaDTO implements Serializable {

        private boolean sucesso;
        private String mensagem;

        
        private byte[] dados;

        public RespostaDTO() {}

        public RespostaDTO(boolean sucesso, String mensagem) {
            this.sucesso = sucesso;
            this.mensagem = mensagem;
        }

        public RespostaDTO(boolean sucesso, String mensagem, byte[] dados) {
            this.sucesso = sucesso;
            this.mensagem = mensagem;
            this.dados = dados;
        }

        public byte[] serialize() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);

            out.writeBoolean(sucesso);
            out.writeUTF(mensagem != null ? mensagem : "");

            if (dados != null && dados.length > 0) {
                out.writeBoolean(true);
                out.writeInt(dados.length);
                out.write(dados);
            } else {
                out.writeBoolean(false);
            }

            out.flush();
            return baos.toByteArray();
        }

        public static RespostaDTO deserialize(byte[] data) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream in = new DataInputStream(bais);

            RespostaDTO dto = new RespostaDTO();
            dto.sucesso = in.readBoolean();
            dto.mensagem = in.readUTF();

            boolean temDados = in.readBoolean();
            if (temDados) {
                int tamanho = in.readInt();
                if (tamanho < 0 || tamanho > 10_000_000) {
                    throw new IOException("Tamanho de dados inválido: " + tamanho);
                }
                dto.dados = new byte[tamanho];
                in.readFully(dto.dados);
            } else {
                dto.dados = null;
            }

            return dto;
        }

        public static RespostaDTO sucesso(String mensagem) {
            return new RespostaDTO(true, mensagem);
        }

        public static RespostaDTO sucesso(String mensagem, byte[] dados) {
            return new RespostaDTO(true, mensagem, dados);
        }

        public static RespostaDTO erro(String mensagem) {
            return new RespostaDTO(false, mensagem);
        }

        public boolean isSucesso() { return sucesso; }
        public void setSucesso(boolean sucesso) { this.sucesso = sucesso; }
        public String getMensagem() { return mensagem; }
        public void setMensagem(String mensagem) { this.mensagem = mensagem; }

        public byte[] getDados() { return dados; }
        public void setDados(byte[] dados) { this.dados = dados; }

        public boolean temDados() { return dados != null && dados.length > 0; }

        @Override
        public String toString() {
            return String.format("RespostaDTO{sucesso=%b, mensagem='%s', temDados=%b}",
                    sucesso, mensagem, temDados());
        }
    }