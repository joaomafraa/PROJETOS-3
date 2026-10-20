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
    private final Empresa empresa;
    private final Chatbot chatbot;
    private Atendente responsavel;
    private final Instant dataInicio;
    private final List<Mensagem> mensagens;
    private Instant dataEncerramento;
    private Status status;

    public Conversa(Long id, Cliente cliente, Empresa empresa, Chatbot chatbot) {
        this.id = Objects.requireNonNull(id, "O id da conversa e obrigatorio.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id da conversa deve ser positivo.");
        }
        this.cliente = Objects.requireNonNull(cliente, "O cliente e obrigatorio.");
        this.empresa = Objects.requireNonNull(empresa, "A empresa do atendimento e obrigatoria.");
        this.chatbot = Objects.requireNonNull(chatbot, "O chatbot e obrigatorio.");
        chatbot.verificarPodeAtender(empresa);
        if (cliente.getEmpresa() != null && !cliente.getEmpresa().getId().equals(empresa.getId())) {
            throw new IllegalArgumentException("O cliente pertence a outra empresa.");
        }
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

    public Empresa getEmpresa() {
        return empresa;
    }

    public Chatbot getChatbot() {
        return chatbot;
    }

    public Atendente getResponsavel() {
        return responsavel;
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
        return adicionarMensagem(id, conteudo, remetente, null);
    }

    public Mensagem adicionarMensagem(Long id, String conteudo, Mensagem.TipoRemetente remetente,
            Atendente atendente) {
        verificarAberta();
        if (mensagens.stream().anyMatch(mensagem -> mensagem.getId().equals(id))) {
            throw new IllegalArgumentException("Ja existe uma mensagem com esse id na conversa.");
        }

        Mensagem mensagem = new Mensagem(id, this, conteudo, remetente, atendente);
        mensagens.add(mensagem);
        return mensagem;
    }

    public void solicitarAtendimentoHumano() {
        if (status != Status.EM_ATENDIMENTO_AUTOMATICO) {
            throw new IllegalStateException("A conversa deve estar em atendimento automatico.");
        }
        status = Status.AGUARDANDO_ATENDENTE;
    }

    public void iniciarAtendimentoHumano(Atendente atendente) {
        if (status != Status.AGUARDANDO_ATENDENTE) {
            throw new IllegalStateException("A conversa deve estar aguardando um atendente.");
        }
        Objects.requireNonNull(atendente, "O atendente responsavel e obrigatorio.");
        atendente.verificarPodeAtender(empresa);
        responsavel = atendente;
        status = Status.EM_ATENDIMENTO_HUMANO;
    }

    public void encerrar() {
        verificarAberta();
        dataEncerramento = Instant.now();
        status = Status.ENCERRADA;
    }

    void verificarEnvio(Mensagem.TipoRemetente remetente, Atendente atendente) {
        verificarAberta();
        Objects.requireNonNull(remetente, "O remetente e obrigatorio.");
        if (remetente == Mensagem.TipoRemetente.ATENDENTE) {
            Objects.requireNonNull(atendente, "O autor humano e obrigatorio.");
            if (status != Status.EM_ATENDIMENTO_HUMANO) {
                throw new IllegalStateException("A conversa deve estar em atendimento humano.");
            }
            responsavel.verificarPodeAtender(empresa);
            atendente.verificarPodeAtender(empresa);
            if (!responsavel.getId().equals(atendente.getId())) {
                throw new IllegalArgumentException("O autor deve ser o responsavel pela conversa.");
            }
        } else {
            if (atendente != null) {
                throw new IllegalArgumentException("Apenas mensagens humanas podem possuir atendente.");
            }
            if (remetente == Mensagem.TipoRemetente.CHATBOT) {
                if (status != Status.EM_ATENDIMENTO_AUTOMATICO) {
                    throw new IllegalStateException("O chatbot so pode responder no atendimento automatico.");
                }
                chatbot.verificarPodeAtender(empresa);
            }
        }
    }

    private void verificarAberta() {
        if (status == Status.ENCERRADA) {
            throw new IllegalStateException("A conversa esta encerrada.");
        }
    }
}
