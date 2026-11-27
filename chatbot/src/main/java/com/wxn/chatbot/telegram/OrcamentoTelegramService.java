package com.wxn.chatbot.telegram;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.PedidoOrcamentoRegistrado;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.model.PedidoOrcamento;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.PedidoOrcamentoService;

import jakarta.persistence.EntityManager;

/**
 * Pedido de orcamento pelo Telegram (HU06.1).
 *
 * Fluxo: /orcamento (ou /orcamento seguido da necessidade) -> necessidade -> registro.
 * O contato vem do cadastro do cliente; so quando o cadastro nao tem telefone nem e-mail o
 * canal pede um contato (etapa CONTATO). /cancelar desiste a qualquer momento.
 *
 * A etapa, o pedido e a resposta preparada na fila sao gravados na mesma transacao: se o
 * registro falhar, nada e confirmado ao cliente. A chave da operacao e a atualizacao do
 * Telegram que concluiu o pedido; reprocessar a mesma atualizacao devolve o mesmo protocolo.
 *
 * Nao cria lead, nao informa preco e nao promete proposta: a equipe comercial responde.
 */
@Service
public class OrcamentoTelegramService {
    static final String PEDIR_NECESSIDADE = "Certo! Descreva o que você precisa orçar, de 10 a 2.000 caracteres. "
            + "A equipe comercial vai analisar e retornar; por aqui não informamos valores. "
            + "Se mudar de ideia, envie /cancelar.";
    static final String NECESSIDADE_INVALIDA = "Descreva o que você precisa orçar usando de 10 a 2.000 caracteres "
            + "ou envie /cancelar.";
    static final String PEDIR_CONTATO = "Anotei sua necessidade. Não encontrei telefone nem e-mail no seu cadastro: "
            + "envie um e-mail ou um telefone com DDD para a equipe retornar, ou /cancelar.";
    static final String CONTATO_INVALIDO = "Não entendi o contato. Envie um e-mail (nome@empresa.com) "
            + "ou um telefone com DDD, ou /cancelar.";
    static final String CANCELADO = "Pedido de orçamento cancelado. Nenhuma solicitação foi criada.";
    static final String OUTRA_OPCAO = "Conclua o pedido de orçamento ou envie /cancelar antes de escolher outra opção.";

    private static final String NECESSIDADE = "NECESSIDADE";
    private static final String CONTATO = "CONTATO";
    private static final Pattern COMANDO = Pattern.compile("(?is)^/orcamento(?:\\s+(.+))?$");

    private final EntityManager em;
    private final ConversaRepository conversas;
    private final PedidoOrcamentoService pedidos;
    private final FilaEnvioTelegramService fila;

    public OrcamentoTelegramService(EntityManager em, ConversaRepository conversas,
            PedidoOrcamentoService pedidos, FilaEnvioTelegramService fila) {
        this.em = em;
        this.conversas = conversas;
        this.pedidos = pedidos;
        this.fila = fila;
    }

    /** Vazio quando a mensagem nao pertence ao pedido de orcamento: o fluxo normal continua. */
    @Transactional
    public Optional<String> atender(long empresaId, long conversaId, MensagemTelegram mensagem) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
        if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) {
            limpar(empresaId, conversaId);
            return Optional.empty();
        }
        String texto = mensagem.texto().strip();
        Object[] estado = estado(empresaId, conversaId);
        Matcher inicio = COMANDO.matcher(texto);
        boolean iniciar = estado == null && inicio.matches();
        if (estado == null && !iniciar) return Optional.empty();
        // Outro pedido ou chamado em andamento responde por esta mensagem.
        if (iniciar && outroFluxoAtivo(empresaId, conversaId)) return Optional.empty();

        String resposta;
        if (iniciar) {
            resposta = iniciar(empresaId, conversa, mensagem, inicio.group(1));
        } else if (texto.equalsIgnoreCase("/cancelar")) {
            limpar(empresaId, conversaId);
            resposta = CANCELADO;
        } else if (texto.startsWith("/") || mensagem.compartilhouContato()) {
            resposta = OUTRA_OPCAO;
        } else if (NECESSIDADE.equals(estado[0])) {
            resposta = receberNecessidade(empresaId, conversa, mensagem, texto);
        } else {
            resposta = receberContato(empresaId, conversa, mensagem, texto, (String) estado[1]);
        }

        long id = Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
        conversa.adicionarMensagem(id, resposta, Mensagem.TipoRemetente.CHATBOT);
        em.flush();
        fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), resposta);
        return Optional.of(resposta);
    }

    private String iniciar(long empresaId, Conversa conversa, MensagemTelegram mensagem, String necessidadeNoComando) {
        if (necessidadeNoComando == null || necessidadeNoComando.isBlank()) {
            gravarEstado(empresaId, conversa.getId(), NECESSIDADE, null);
            return PEDIR_NECESSIDADE;
        }
        String necessidade = necessidadeNoComando.strip();
        if (!necessidadeValida(necessidade)) {
            gravarEstado(empresaId, conversa.getId(), NECESSIDADE, null);
            return NECESSIDADE_INVALIDA;
        }
        return seguirComNecessidade(empresaId, conversa, mensagem, necessidade);
    }

    private String receberNecessidade(long empresaId, Conversa conversa, MensagemTelegram mensagem, String texto) {
        if (!necessidadeValida(texto)) return NECESSIDADE_INVALIDA;
        return seguirComNecessidade(empresaId, conversa, mensagem, texto);
    }

    // So pergunta o contato quando o cadastro do cliente nao tem nenhum.
    private String seguirComNecessidade(long empresaId, Conversa conversa, MensagemTelegram mensagem, String necessidade) {
        if (pedidos.contatoCadastrado(empresaId, conversa.getId()).isPresent()) {
            return registrar(empresaId, conversa, mensagem, necessidade, null);
        }
        gravarEstado(empresaId, conversa.getId(), CONTATO, necessidade);
        return PEDIR_CONTATO;
    }

    private String receberContato(long empresaId, Conversa conversa, MensagemTelegram mensagem, String texto,
            String necessidade) {
        try {
            PedidoOrcamento.contatoInformado(texto);
        } catch (IllegalArgumentException contatoInvalido) {
            return CONTATO_INVALIDO;
        }
        return registrar(empresaId, conversa, mensagem, necessidade, texto);
    }

    private String registrar(long empresaId, Conversa conversa, MensagemTelegram mensagem, String necessidade,
            String contatoInformado) {
        // A atualizacao do Telegram e a chave: reprocessar a mesma mensagem devolve o mesmo protocolo.
        PedidoOrcamentoRegistrado pedido = pedidos.registrar(empresaId, conversa.getId(), necessidade,
                contatoInformado, "telegram:" + mensagem.atualizacaoId());
        limpar(empresaId, conversa.getId());
        return "Seu pedido de orçamento foi registrado. Protocolo: " + pedido.protocolo()
                + ". A equipe comercial vai analisar sua necessidade e retornar "
                + (contatoInformado == null ? "pelos dados do seu cadastro" : "pelo contato que você informou")
                + ". Este registro não é uma proposta: valores e prazos serão informados pela equipe.";
    }

    private Object[] estado(long empresaId, long conversaId) {
        List<?> linhas = em.createNativeQuery("select etapa, necessidade from telegram_pedido_orcamento"
                + " where empresa_id = :empresa and conversa_id = :conversa")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).getResultList();
        return linhas.isEmpty() ? null : (Object[]) linhas.get(0);
    }

    private boolean outroFluxoAtivo(long empresaId, long conversaId) {
        return !em.createNativeQuery("select conversa_id from telegram_pedido_agendamento"
                + " where empresa_id = :empresa and conversa_id = :conversa")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).getResultList().isEmpty()
                || !em.createNativeQuery("select conversa_id from telegram_abertura_chamado"
                + " where empresa_id = :empresa and conversa_id = :conversa and aguardando_descricao = true")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).getResultList().isEmpty();
    }

    private void gravarEstado(long empresaId, long conversaId, String etapa, String necessidade) {
        limpar(empresaId, conversaId);
        // Sem necessidade nao ha parametro nulo: o PostgreSQL nao infere o tipo de um nulo sem tipo.
        var comando = necessidade == null
                ? em.createNativeQuery("insert into telegram_pedido_orcamento (conversa_id, empresa_id, etapa)"
                        + " values (:conversa, :empresa, :etapa)")
                : em.createNativeQuery("insert into telegram_pedido_orcamento (conversa_id, empresa_id, etapa, necessidade)"
                        + " values (:conversa, :empresa, :etapa, :necessidade)")
                        .setParameter("necessidade", necessidade);
        comando.setParameter("conversa", conversaId).setParameter("empresa", empresaId)
                .setParameter("etapa", etapa).executeUpdate();
    }

    private void limpar(long empresaId, long conversaId) {
        em.createNativeQuery("delete from telegram_pedido_orcamento where empresa_id = :empresa and conversa_id = :conversa")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).executeUpdate();
    }

    private static boolean necessidadeValida(String necessidade) {
        return necessidade.length() >= PedidoOrcamentoService.NECESSIDADE_MINIMA
                && necessidade.length() <= PedidoOrcamentoService.NECESSIDADE_MAXIMA;
    }
}
