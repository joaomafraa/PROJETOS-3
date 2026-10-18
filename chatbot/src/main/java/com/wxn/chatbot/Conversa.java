package com.wxn.chatbot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Conversa {

    public enum Status {
        EM_ATENDIMENTO_AUTOMATICO,
        AGUARDANDO_ATENDENTE,
        EM_ATENDIMENTO_HUMANO,
        ENCERRADA
    }

    private final Long id;
    private final Cliente cliente;
    private final Instant dataInicio;
    private final List<Mensagem> mensagens;
    private Instant dataEncerramento;
    private Status status;

    public Conversa(Long id, Cliente cliente) {
        this.id = Objects.requireNonNull(id, "O id da conversa e obrigatorio.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id da conversa deve ser positivo.");
        }
        this.cliente = Objects.requireNonNull(cliente, "O cliente e obrigatorio.");
        this.dataInicio = Instant.now();
        this.mensagens = new ArrayList<>();
        this.status = Status.EM_ATENDIMENTO_AUTOMATICO;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Instant getDataInicio() {
        return dataInicio;
    }

    public Instant getDataEncerramento() {
        return dataEncerramento;
    }

    public Status getStatus() {
        return status;
    }

    public List<Mensagem> getMensagens() {
        return List.copyOf(mensagens);
    }

    public Mensagem adicionarMensagem(Long id, String conteudo, Mensagem.TipoRemetente remetente) {
        verificarAberta();
        if (mensagens.stream().anyMatch(mensagem -> mensagem.getId().equals(id))) {
            throw new IllegalArgumentException("Ja existe uma mensagem com esse id na conversa.");
        }

        Mensagem mensagem = new Mensagem(id, this, conteudo, remetente);
        mensagens.add(mensagem);
        return mensagem;
    }

    public void solicitarAtendimentoHumano() {
        if (status != Status.EM_ATENDIMENTO_AUTOMATICO) {
            throw new IllegalStateException("A conversa deve estar em atendimento automatico.");
        }
        status = Status.AGUARDANDO_ATENDENTE;
    }

    public void iniciarAtendimentoHumano() {
        if (status != Status.AGUARDANDO_ATENDENTE) {
            throw new IllegalStateException("A conversa deve estar aguardando um atendente.");
        }
        status = Status.EM_ATENDIMENTO_HUMANO;
    }

    public void encerrar() {
        verificarAberta();
        dataEncerramento = Instant.now();
        status = Status.ENCERRADA;
    }

    private void verificarAberta() {
        if (status == Status.ENCERRADA) {
            throw new IllegalStateException("A conversa esta encerrada.");
        }
    }
}
