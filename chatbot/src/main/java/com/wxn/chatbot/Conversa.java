package com.wxn.chatbot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "conversas")
public class Conversa {

    public enum Status {
        EM_ATENDIMENTO_AUTOMATICO,
        AGUARDANDO_ATENDENTE,
        EM_ATENDIMENTO_HUMANO,
        ENCERRADA
    }

    @Id
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Atendente responsavel;
    @Column(name = "data_inicio", nullable = false, updatable = false)
    private Instant dataInicio;
    @Transient
    private List<Mensagem> mensagens = new ArrayList<>();
    @Column(name = "data_encerramento")
    private Instant dataEncerramento;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(40)")
    private Status status;

    protected Conversa() {
    }

    public Conversa(Long id, Cliente cliente) {
        this.id = Objects.requireNonNull(id, "O id da conversa e obrigatorio.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id da conversa deve ser positivo.");
        }
        this.cliente = Objects.requireNonNull(cliente, "O cliente e obrigatorio.");
        this.dataInicio = Instant.now();
        this.status = Status.EM_ATENDIMENTO_AUTOMATICO;
    }

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

    public void iniciarAtendimentoHumano() {
        if (empresa != null) {
            throw new IllegalStateException("O atendente responsavel e obrigatorio.");
        }
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

    void verificarEnvio(Mensagem.TipoRemetente remetente, Atendente atendente) {
        verificarAberta();
        Objects.requireNonNull(remetente, "O remetente e obrigatorio.");
        if (empresa == null) {
            if (remetente != Mensagem.TipoRemetente.ATENDENTE && atendente != null) {
                throw new IllegalArgumentException("Apenas mensagens humanas podem possuir atendente.");
            }
            return;
        }
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
