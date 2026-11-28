package com.wxn.chatbot.telegram;

import java.util.List;
import java.util.Optional;
import com.wxn.chatbot.dto.PedidoAgendamentoRegistrado;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.PedidoAgendamentoService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coleta do pedido e confirmação preparada atomicamente com o registro. */
@Service
public class PedidoAgendamentoTelegramService {
    private final EntityManager em;
    private final ConversaRepository conversas;
    private final PedidoAgendamentoService pedidos;
    private final FilaEnvioTelegramService fila;
    public PedidoAgendamentoTelegramService(EntityManager em, ConversaRepository conversas,
            PedidoAgendamentoService pedidos, FilaEnvioTelegramService fila) {
        this.em = em; this.conversas = conversas; this.pedidos = pedidos; this.fila = fila;
    }
    @Transactional
    public Optional<String> atender(long empresaId, long conversaId, MensagemTelegram mensagem) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
        if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) return Optional.empty();
        String texto = mensagem.texto().strip();
        List<?> linhas = em.createNativeQuery("select tipo, etapa, necessidade from telegram_pedido_agendamento where empresa_id = :empresa and conversa_id = :conversa")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).getResultList();
        Object[] estado = linhas.isEmpty() ? null : (Object[]) linhas.get(0);
        Ticket.TipoSolicitacao tipo = tipoComando(texto);
        if (estado == null && tipo == null) return Optional.empty();
        if (estado == null && !em.createNativeQuery("select conversa_id from telegram_abertura_chamado where empresa_id = :empresa and conversa_id = :conversa and aguardando_descricao = true")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).getResultList().isEmpty()) return Optional.empty();
        String resposta;
        if (estado == null) {
            em.createNativeQuery("insert into telegram_pedido_agendamento (empresa_id, conversa_id, tipo, etapa) values (:empresa, :conversa, :tipo, 'NECESSIDADE')")
                    .setParameter("empresa", empresaId).setParameter("conversa", conversaId).setParameter("tipo", tipo.name()).executeUpdate();
            resposta = "Descreva sua necessidade para o pedido de " + nome(tipo) + ". Para desistir, envie /cancelar.";
        } else if (texto.equalsIgnoreCase("/cancelar")) {
            excluir(empresaId, conversaId);
            resposta = "Pedido cancelado. Nenhuma solicitação foi criada.";
        } else if (texto.startsWith("/") || mensagem.compartilhouContato()) {
            resposta = "Conclua o pedido atual ou envie /cancelar antes de escolher outra opção.";
        } else if ("NECESSIDADE".equals(estado[1])) {
            if (texto.length() < 10 || texto.length() > 2000) resposta = "Descreva sua necessidade usando de 10 a 2.000 caracteres ou envie /cancelar.";
            else {
                em.createNativeQuery("update telegram_pedido_agendamento set necessidade = :necessidade, etapa = 'HORARIO' where empresa_id = :empresa and conversa_id = :conversa")
                        .setParameter("necessidade", texto).setParameter("empresa", empresaId).setParameter("conversa", conversaId).executeUpdate();
                resposta = "Qual dia e horário funcionam melhor para você? Por exemplo: terça-feira, depois das 14h.\n\n"
                        + "A equipe ainda precisará confirmar a disponibilidade. Para sair, use /cancelar.";
            }
        } else if (texto.length() < 3 || texto.length() > 300) {
            resposta = "Informe sua preferência de dia e horário com 3 a 300 caracteres ou envie /cancelar.";
        } else {
            tipo = Ticket.TipoSolicitacao.valueOf((String) estado[0]);
            PedidoAgendamentoRegistrado pedido = pedidos.registrar(empresaId, conversaId, tipo, (String) estado[2], texto, "telegram:" + mensagem.atualizacaoId());
            excluir(empresaId, conversaId);
            resposta = "Seu pedido de " + nome(pedido.tipo()) + " foi registrado.\n\nProtocolo: " + pedido.protocolo()
                    + "\nPreferência: " + pedido.preferenciaHorario()
                    + "\n\nA data e o horário ainda dependem da confirmação da equipe. Acompanhe em /solicitacoes.";
        }
        long id = Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
        conversa.adicionarMensagem(id, resposta, Mensagem.TipoRemetente.CHATBOT);
        em.flush(); fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), resposta);
        return Optional.of(resposta);
    }
    private void excluir(long empresa, long conversa) {
        em.createNativeQuery("delete from telegram_pedido_agendamento where empresa_id = :empresa and conversa_id = :conversa")
                .setParameter("empresa", empresa).setParameter("conversa", conversa).executeUpdate();
    }
    private static Ticket.TipoSolicitacao tipoComando(String texto) {
        if (texto.equalsIgnoreCase("/demonstracao")) return Ticket.TipoSolicitacao.DEMONSTRACAO;
        if (texto.equalsIgnoreCase("/reuniao")) return Ticket.TipoSolicitacao.REUNIAO;
        if (texto.equalsIgnoreCase("/visita")) return Ticket.TipoSolicitacao.VISITA_TECNICA;
        return null;
    }
    private static String nome(Ticket.TipoSolicitacao tipo) {
        return switch (tipo) { case DEMONSTRACAO -> "demonstração"; case REUNIAO -> "reunião";
            case VISITA_TECNICA -> "visita técnica"; default -> throw new IllegalArgumentException("Tipo inválido."); };
    }
}
