package com.wxn.chatbot.telegram;

import org.springframework.stereotype.Service;

/** Coordena uma mensagem por chamada; a busca contínua ainda não está ativada. */
@Service
public class ProcessarMensagemTelegramService {
    private final IdentificacaoTelegramService identificacao;
    private final RegistrarMensagemTelegramService registro;
    private final RespostaTelegramService resposta;
    private final FilaEnvioTelegramService fila;
    private final TelegramCliente telegram;
    private final TransferenciaTelegramService transferencia;

    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia) {
        this.identificacao = identificacao; this.registro = registro; this.resposta = resposta;
        this.fila = fila; this.telegram = telegram; this.transferencia = transferencia;
    }

    public boolean processar(long empresaId, long chatbotId, MensagemTelegram mensagem) {
        if (mensagem == null) throw new IllegalArgumentException("Mensagem obrigatória.");
        long conversa = executar("identificacao", () -> identificacao.obterConversa(empresaId, chatbotId, mensagem.usuarioId(), mensagem.nome()));
        executar("registro", () -> registro.registrar(empresaId, chatbotId, mensagem));
        String estado = executar("consulta_envio", () -> fila.status(empresaId, mensagem.atualizacaoId()));
        if ("ENVIADO".equals(estado) || "INCERTO".equals(estado)) return false;
        if (estado != null) throw new IllegalStateException("Envio pendente requer revisão antes de continuar.");
        boolean pediuAtendente = mensagem.texto().strip().equalsIgnoreCase("/atendente");
        String texto = pediuAtendente ? executar("transferencia", () -> transferencia.solicitar(empresaId, conversa)) : executar("historico", () -> resposta.respostaExistenteOuPendente(empresaId, conversa));
        if (texto == null) return false; // conversa com atendente: mensagem já salva
        if (texto.isEmpty()) texto = executar("geracao_ia", () -> resposta.gerar(empresaId, conversa));
        String conteudo = texto;
        executar("preparo_envio", () -> { fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), conteudo); return true; });
        // Marca como incerto antes da chamada externa: evita duplicar após queda ou timeout.
        if (!fila.marcarIncerteza(empresaId, mensagem.atualizacaoId()))
            throw new IllegalStateException("Não foi possível preparar o envio.");
        executar("envio", () -> { telegram.enviarMensagem(mensagem.chatId(), conteudo); return true; });
        if (!fila.marcarEnviado(empresaId, mensagem.atualizacaoId()))
            throw new IllegalStateException("Não foi possível confirmar o envio.");
        return true;
    }
    private static <T> T executar(String etapa, java.util.function.Supplier<T> operacao) {
        try { return operacao.get(); } catch (RuntimeException erro) { throw new FalhaEtapaTelegramException(etapa, erro); }
    }
}
