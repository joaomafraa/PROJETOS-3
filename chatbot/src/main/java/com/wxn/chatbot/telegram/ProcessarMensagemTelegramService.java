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
    private final com.wxn.chatbot.security.ValidacaoClienteService validacao;

    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia,
            com.wxn.chatbot.security.ValidacaoClienteService validacao) {
        this.identificacao = identificacao; this.registro = registro; this.resposta = resposta;
        this.fila = fila; this.telegram = telegram; this.transferencia = transferencia; this.validacao = validacao;
    }

    public boolean processar(long empresaId, long chatbotId, MensagemTelegram mensagem) {
        if (mensagem == null) throw new IllegalArgumentException("Mensagem obrigatória.");
        long conversa = executar("identificacao", () -> identificacao.obterConversa(empresaId, chatbotId, mensagem.usuarioId(), mensagem.nome()));
        String entrada = mensagem.texto().strip();
        boolean comandoCodigo = entrada.matches("(?is)^/codigo(?:\\s.*)?$");
        boolean codigo = comandoCodigo || entrada.matches("[0-9]{6}");
        // Codigos nao devem entrar no historico, no painel ou no contexto enviado ao LLM.
        MensagemTelegram registrada = codigo ? new MensagemTelegram(mensagem.atualizacaoId(), mensagem.chatId(),
                mensagem.usuarioId(), mensagem.nome(), "[codigo de verificacao omitido]") : mensagem;
        executar("registro", () -> registro.registrar(empresaId, chatbotId, registrada));
        String estado = executar("consulta_envio", () -> fila.status(empresaId, mensagem.atualizacaoId()));
        if ("ENVIADO".equals(estado) || "INCERTO".equals(estado)) return false;
        if (estado != null) throw new IllegalStateException("Envio pendente requer revisão antes de continuar.");
        boolean pediuAtendente = entrada.equalsIgnoreCase("/atendente");
        String texto;
        if (entrada.equalsIgnoreCase("/validar")) {
            try {
                validacao.solicitar(empresaId, conversa);
            } catch (RuntimeException e) { /* Resposta generica nao revela o estado do cadastro. */ }
            texto = "Se a verificacao estiver disponivel, o codigo sera enviado ao email confiavel. Use /codigo seguido do codigo; caso nao receba, aguarde ou procure um atendente.";
        } else if (codigo) {
            String informado = comandoCodigo ? entrada.substring(7).strip() : entrada;
            boolean confirmado;
            try { confirmado = validacao.confirmar(empresaId, conversa, informado); }
            catch (RuntimeException e) { confirmado = false; }
            texto = confirmado ? "Identidade verificada temporariamente. Use /solicitacoes para consultar."
                    : "Codigo invalido ou expirado, ou verificacao indisponivel.";
        } else if (entrada.equalsIgnoreCase("/solicitacoes")) {
            try {
                var itens = validacao.consultarSolicitacoes(empresaId, conversa);
                texto = itens.isEmpty() ? "Nenhuma solicitacao encontrada." : itens.stream()
                        .map(item -> item.protocolo() + " - " + item.tipo() + " - " + item.status())
                        .collect(java.util.stream.Collectors.joining("\n", "Suas solicitacoes (ate 20):\n", ""));
            } catch (SecurityException e) {
                texto = "Para consultar suas solicitacoes, valide sua identidade com /validar.";
            }
        } else {
            texto = pediuAtendente ? executar("transferencia", () -> transferencia.solicitar(empresaId, conversa)) : executar("historico", () -> resposta.respostaExistenteOuPendente(empresaId, conversa));
        }
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
