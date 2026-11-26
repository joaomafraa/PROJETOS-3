package com.wxn.chatbot.telegram;

import java.util.List;
import java.util.Optional;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.AbrirChamadoService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Etapas do canal e seu efeito de negócio são confirmados na mesma transação. */
@Service
public class AberturaChamadoTelegramService {
    private final EntityManager em;
    private final ConversaRepository conversas;
    private final AbrirChamadoService chamados;
    private final FilaEnvioTelegramService fila;

    public AberturaChamadoTelegramService(EntityManager em, ConversaRepository conversas,
            AbrirChamadoService chamados, FilaEnvioTelegramService fila) {
        this.em = em; this.conversas = conversas; this.chamados = chamados; this.fila = fila;
    }

    @Transactional
    public Optional<String> atender(long empresaId, long conversaId, MensagemTelegram mensagem) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
        String texto = mensagem.texto().strip();
        boolean iniciar = texto.equalsIgnoreCase("/chamado");
        boolean cancelar = texto.equalsIgnoreCase("/cancelar");
        if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) {
            limpar(empresaId, conversaId);
            return Optional.empty();
        }
        List<?> registros = em.createNativeQuery("select aguardando_descricao from telegram_abertura_chamado where empresa_id = :empresa and conversa_id = :conversa")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).getResultList();
        boolean aguardando = !registros.isEmpty() && Boolean.TRUE.equals(registros.get(0));
        if (!iniciar && !aguardando) return Optional.empty();
        String resposta;
        if (iniciar) {
            if (registros.isEmpty()) em.createNativeQuery("insert into telegram_abertura_chamado (empresa_id, conversa_id, aguardando_descricao) values (:empresa, :conversa, true)")
                    .setParameter("empresa", empresaId).setParameter("conversa", conversaId).executeUpdate();
            else em.createNativeQuery("update telegram_abertura_chamado set aguardando_descricao = true where empresa_id = :empresa and conversa_id = :conversa")
                    .setParameter("empresa", empresaId).setParameter("conversa", conversaId).executeUpdate();
            resposta = "Descreva o problema para abrir seu chamado de suporte. Se mudar de ideia, envie /cancelar.";
        } else if (cancelar) {
            limpar(empresaId, conversaId);
            resposta = "Abertura de chamado cancelada. Nenhuma solicitação foi criada.";
        } else if (texto.startsWith("/") || mensagem.compartilhouContato() || texto.length() < 10 || texto.length() > 2000) {
            resposta = "Informe uma descrição do problema com 10 a 2.000 caracteres ou envie /cancelar.";
        } else {
            var chamado = chamados.abrir(empresaId, conversaId, texto);
            limpar(empresaId, conversaId);
            resposta = "Seu chamado de suporte foi registrado. Protocolo: " + chamado.protocolo()
                    + ". A equipe poderá acompanhar sua solicitação no painel.";
        }
        long id = Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
        conversa.adicionarMensagem(id, resposta, Mensagem.TipoRemetente.CHATBOT);
        em.flush();
        fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), resposta);
        return Optional.of(resposta);
    }
    private void limpar(long empresaId, long conversaId) {
        em.createNativeQuery("delete from telegram_abertura_chamado where empresa_id = :empresa and conversa_id = :conversa")
                .setParameter("empresa", empresaId).setParameter("conversa", conversaId).executeUpdate();
    }
}
