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
    private final CadastroTelegramService cadastro;
    private final TriagemTelegramService triagem;
    private final AberturaChamadoTelegramService aberturaChamado;
    private final ConsultaServicosTelegramService servicos;
    private final PedidoAgendamentoTelegramService agendamento;
    private final OrcamentoTelegramService orcamento;

    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia,
            com.wxn.chatbot.security.ValidacaoClienteService validacao, CadastroTelegramService cadastro) {
        this(identificacao, registro, resposta, fila, telegram, transferencia, validacao, cadastro, null);
    }

    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia,
            com.wxn.chatbot.security.ValidacaoClienteService validacao, CadastroTelegramService cadastro,
            TriagemTelegramService triagem) {
        this(identificacao, registro, resposta, fila, telegram, transferencia, validacao, cadastro, triagem, null);
    }

    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia,
            com.wxn.chatbot.security.ValidacaoClienteService validacao, CadastroTelegramService cadastro,
            TriagemTelegramService triagem, AberturaChamadoTelegramService aberturaChamado) {
        this(identificacao, registro, resposta, fila, telegram, transferencia, validacao, cadastro, triagem, aberturaChamado, null);
    }

    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia,
            com.wxn.chatbot.security.ValidacaoClienteService validacao, CadastroTelegramService cadastro,
            TriagemTelegramService triagem, AberturaChamadoTelegramService aberturaChamado,
            ConsultaServicosTelegramService servicos) {
        this(identificacao, registro, resposta, fila, telegram, transferencia, validacao, cadastro, triagem, aberturaChamado, servicos, null);
    }

    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia,
            com.wxn.chatbot.security.ValidacaoClienteService validacao, CadastroTelegramService cadastro,
            TriagemTelegramService triagem, AberturaChamadoTelegramService aberturaChamado,
            ConsultaServicosTelegramService servicos, PedidoAgendamentoTelegramService agendamento) {
        this(identificacao, registro, resposta, fila, telegram, transferencia, validacao, cadastro, triagem,
                aberturaChamado, servicos, agendamento, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProcessarMensagemTelegramService(IdentificacaoTelegramService identificacao,
            RegistrarMensagemTelegramService registro, RespostaTelegramService resposta,
            FilaEnvioTelegramService fila, TelegramCliente telegram, TransferenciaTelegramService transferencia,
            com.wxn.chatbot.security.ValidacaoClienteService validacao, CadastroTelegramService cadastro,
            TriagemTelegramService triagem, AberturaChamadoTelegramService aberturaChamado,
            ConsultaServicosTelegramService servicos, PedidoAgendamentoTelegramService agendamento,
            OrcamentoTelegramService orcamento) {
        this.identificacao = identificacao; this.registro = registro; this.resposta = resposta;
        this.fila = fila; this.telegram = telegram; this.transferencia = transferencia; this.validacao = validacao;
        this.cadastro = cadastro;
        this.triagem = triagem;
        this.aberturaChamado = aberturaChamado;
        this.servicos = servicos;
        this.agendamento = agendamento;
        this.orcamento = orcamento;
    }

    public boolean processar(long empresaId, long chatbotId, MensagemTelegram mensagem) {
        if (mensagem == null) throw new IllegalArgumentException("Mensagem obrigatória.");
        String original = mensagem.texto().strip();
        String entrada = NavegacaoTelegram.comando(original);
        String intencao = NavegacaoTelegram.normalizar(entrada);
        MensagemTelegram dirigida = entrada.equals(original) ? mensagem
                : new MensagemTelegram(mensagem.atualizacaoId(), mensagem.chatId(), mensagem.usuarioId(),
                        mensagem.nome(), entrada, mensagem.telefone());
        boolean encerrar = intencao.equals("/encerrar") || intencao.matches(
                "(?:(?:quero|pode) )?(?:finalizar|encerrar|terminar)(?: (?:o |a |meu |minha )?(?:atendimento|conversa))?");
        // Uma confirmação já processada não deve reabrir a conversa encerrada ao ser repetida.
        if (encerrar) {
            String envioAnterior = executar("consulta_envio", () -> fila.status(empresaId, mensagem.atualizacaoId()));
            if ("ENVIADO".equals(envioAnterior) || "INCERTO".equals(envioAnterior)) return false;
            if (envioAnterior != null) throw new IllegalStateException("Envio pendente requer revisão antes de continuar.");
        }
        long conversa = executar("identificacao", () -> identificacao.obterConversa(empresaId, chatbotId, mensagem.usuarioId(), mensagem.nome()));
        boolean comandoCodigo = entrada.matches("(?is)^/codigo(?:\\s.*)?$");
        boolean codigo = comandoCodigo || entrada.matches("[0-9]{6}");
        // Codigos nao devem entrar no historico, no painel ou no contexto enviado ao LLM.
        MensagemTelegram registrada = codigo ? new MensagemTelegram(mensagem.atualizacaoId(), mensagem.chatId(),
                mensagem.usuarioId(), mensagem.nome(), "[codigo de verificacao omitido]") : mensagem;
        boolean atualizacaoNova = executar("registro", () -> registro.registrar(empresaId, chatbotId, registrada));
        String estado = executar("consulta_envio", () -> fila.status(empresaId, mensagem.atualizacaoId()));
        if ("ENVIADO".equals(estado) || "INCERTO".equals(estado)) return false;
        if (estado != null) throw new IllegalStateException("Envio pendente requer revisão antes de continuar.");
        if (!atualizacaoNova) {
            throw new IllegalStateException(
                    "Atualizacao repetida sem resultado preparado requer revisao antes de continuar.");
        }
        boolean pediuAtendente = entrada.equalsIgnoreCase("/atendente");
        String texto;
        TelegramCliente.Teclado teclado = TelegramCliente.Teclado.NENHUM;
        boolean preparado = false;
        if (encerrar) {
            texto = executar("encerramento", () -> transferencia.finalizar(empresaId, conversa, mensagem));
            preparado = true;
            teclado = TelegramCliente.Teclado.REMOVER;
        } else if (intencao.equals("/menu")) {
            texto = NavegacaoTelegram.APRESENTACAO;
            teclado = TelegramCliente.Teclado.MENU;
        } else if (entrada.equalsIgnoreCase("/validar")) {
            try {
                validacao.solicitar(empresaId, conversa);
                texto = "Solicitei o envio do código ao seu e-mail cadastrado.\n\n"
                        + "Quando receber, digite os seis números aqui. Depois, escolha «Minhas solicitações» ou «Meus planos» em /menu.";
            } catch (RuntimeException e) {
                texto = "Não consegui iniciar uma nova confirmação agora. Se você já recebeu um código, digite os seis números aqui. "
                        + "Caso contrário, aguarde alguns minutos e tente /validar novamente ou peça ajuda com /atendente.";
            }
        } else if (codigo) {
            String informado = comandoCodigo ? entrada.substring(7).strip() : entrada;
            boolean confirmado;
            try { confirmado = validacao.confirmar(empresaId, conversa, informado); }
            catch (RuntimeException e) { confirmado = false; }
            texto = confirmado ? "Identidade confirmada. Você já pode escolher «Minhas solicitações» ou «Meus planos» no teclado."
                    : "Não consegui confirmar esse código. Confira os seis números. Se ele tiver expirado, tente /validar novamente.";
            if (confirmado) teclado = TelegramCliente.Teclado.MENU;
        } else if (entrada.equalsIgnoreCase("/solicitacoes")) {
            try {
                var itens = validacao.consultarSolicitacoes(empresaId, conversa);
                texto = itens.isEmpty() ? "Você ainda não tem solicitações registradas. Se precisar de suporte, use /chamado." : itens.stream()
                        .map(item -> "• " + item.protocolo() + " — " + rotulo(item.tipo()) + " · " + rotulo(item.status()))
                        .collect(java.util.stream.Collectors.joining("\n", "Suas solicitações mais recentes\n\n", ""));
            } catch (SecurityException e) {
                texto = "Para proteger seus dados, preciso confirmar sua identidade antes de mostrar as solicitações. "
                        + "Toque em /validar e, quando receber o código por e-mail, digite os seis números aqui.";
            } catch (RuntimeException e) {
                texto = "Não consegui consultar suas solicitações agora. Tente novamente em alguns minutos ou peça ajuda com /atendente.";
            }
        } else if (entrada.equalsIgnoreCase("/planos") && servicos != null) {
            texto = executar("consulta_planos", () -> servicos.responder(empresaId, conversa));
        } else {
            // HU07.1/HU07.2: identificacao e cadastro antes da IA; a resposta ja vem preparada na fila.
            var etapa = pediuAtendente ? java.util.Optional.<CadastroTelegramService.Resposta>empty()
                    : executar("cadastro", () -> cadastro.atender(empresaId, conversa, dirigida));
            if (etapa.isPresent()) {
                texto = etapa.get().texto(); teclado = etapa.get().teclado(); preparado = true;
            } else {
                if (pediuAtendente) {
                    texto = executar("transferencia", () -> transferencia.solicitar(empresaId, conversa));
                } else {
                    var fluxo = continuarFluxo(empresaId, conversa, dirigida);
                    preparado = fluxo.isPresent();
                    texto = preparado ? fluxo.get()
                            : executar("historico", () -> resposta.respostaExistenteOuPendente(empresaId, conversa));
                }
            }
        }
        if (texto == null) return false; // conversa com atendente: mensagem já salva
        if (texto.isEmpty()) {
            if (intencao.equals("/start")) {
                texto = NavegacaoTelegram.APRESENTACAO;
                teclado = TelegramCliente.Teclado.MENU;
            } else if (intencao.equals("/cancelar")) {
                texto = "Você não tem um pedido em andamento para cancelar. Veja as opções em /menu.";
            } else if (intencao.startsWith("/")) {
                texto = "Não reconheci esse comando. Abra /menu para ver as opções ou escreva sua dúvida.";
            } else if (intencao.matches("oi|ola|opa|bom dia|boa tarde|boa noite|e ai")) {
                texto = "Olá! Como posso ajudar? Escreva sua dúvida ou use /menu para ver as opções.";
            } else if (intencao.matches("obrigad[oa]|valeu|agradeco|muit[oa] obrigad[oa]")) {
                texto = "De nada! Se precisar de mais alguma coisa, escreva aqui ou use /menu.";
            } else {
                texto = executar("geracao_ia", () -> resposta.gerar(empresaId, conversa));
            }
        }
        String conteudo = texto;
        if (!preparado)
            executar("preparo_envio", () -> { fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), conteudo); return true; });
        TelegramCliente.Teclado botoes = teclado;
        // Marca como incerto antes da chamada externa: evita duplicar após queda ou timeout.
        if (!fila.marcarIncerteza(empresaId, mensagem.atualizacaoId()))
            throw new IllegalStateException("Não foi possível preparar o envio.");
        executar("envio", () -> {
            if (botoes == TelegramCliente.Teclado.NENHUM) telegram.enviarMensagem(mensagem.chatId(), conteudo);
            else telegram.enviarMensagem(mensagem.chatId(), conteudo, botoes);
            return true;
        });
        if (!fila.marcarEnviado(empresaId, mensagem.atualizacaoId()))
            throw new IllegalStateException("Não foi possível confirmar o envio.");
        return true;
    }
    private java.util.Optional<String> continuarFluxo(long empresa, long conversa, MensagemTelegram mensagem) {
        return (orcamento == null ? java.util.Optional.<String>empty()
                : executar("pedido_orcamento", () -> orcamento.atender(empresa, conversa, mensagem)))
                .or(() -> agendamento == null ? java.util.Optional.empty()
                        : executar("pedido_agendamento", () -> agendamento.atender(empresa, conversa, mensagem)))
                .or(() -> aberturaChamado == null ? java.util.Optional.empty()
                        : executar("abertura_chamado", () -> aberturaChamado.atender(empresa, conversa, mensagem)))
                .or(() -> triagem == null ? java.util.Optional.empty()
                        : executar("triagem", () -> triagem.atender(empresa, conversa, mensagem)));
    }

    private static String rotulo(Enum<?> valor) {
        return switch (valor.name()) {
            case "ORCAMENTO" -> "Orçamento";
            case "DEMONSTRACAO" -> "Demonstração";
            case "REUNIAO" -> "Reunião";
            case "VISITA_TECNICA" -> "Visita técnica";
            case "EM_ATENDIMENTO" -> "Em atendimento";
            case "CONCLUIDO" -> "Concluído";
            default -> valor.name().charAt(0) + valor.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        };
    }
    private static <T> T executar(String etapa, java.util.function.Supplier<T> operacao) {
        try { return operacao.get(); } catch (RuntimeException erro) { throw new FalhaEtapaTelegramException(etapa, erro); }
    }
}
