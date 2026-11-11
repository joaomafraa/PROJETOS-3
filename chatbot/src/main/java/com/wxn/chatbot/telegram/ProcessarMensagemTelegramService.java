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
        long conversa = identificacao.obterConversa(empresaId, chatbotId, mensagem.usuarioId(), mensagem.nome());
        registro.registrar(empresaId, chatbotId, mensagem);
        String estado = fila.status(empresaId, mensagem.atualizacaoId());
        if ("ENVIADO".equals(estado) || "INCERTO".equals(estado)) return false;
        if (estado != null) throw new IllegalStateException("Envio pendente requer revisão antes de continuar.");
        boolean pediuAtendente = mensagem.texto().strip().equalsIgnoreCase("/atendente");
        String texto = pediuAtendente ? transferencia.solicitar(empresaId, conversa) : resposta.respostaExistenteOuPendente(empresaId, conversa);
        if (texto == null) return false; // conversa com atendente: mensagem já salva
        if (texto.isEmpty()) texto = resposta.gerar(empresaId, conversa);
        fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), texto);
        // Marca como incerto antes da chamada externa: evita duplicar após queda ou timeout.
        if (!fila.marcarIncerteza(empresaId, mensagem.atualizacaoId()))
            throw new IllegalStateException("Não foi possível preparar o envio.");
        telegram.enviarMensagem(mensagem.chatId(), texto);
        if (!fila.marcarEnviado(empresaId, mensagem.atualizacaoId()))
            throw new IllegalStateException("Não foi possível confirmar o envio.");
        return true;
    }
}
