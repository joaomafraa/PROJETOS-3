package com.wxn.chatbot.model;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "configuracoes_atendimento")
public class ConfiguracaoAtendimento {

    public static final String MENSAGEM_PADRAO =
            "No momento nossa equipe está fora do horário de atendimento.";
    public static final ZoneId FUSO_PADRAO = ZoneId.of("America/Recife");

    static final int LIMITE_MENSAGEM = 500;
    static final int LIMITE_ASSUNTO = 80;
    static final int LIMITE_ASSUNTOS = 20;
    static final int LIMITE_INTERVALOS = 21;

    @Id
    @Column(name = "empresa_id")
    private Long empresaId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(nullable = false, length = 60)
    private String fuso;

    @Column(name = "mensagem_fora_expediente", nullable = false, length = LIMITE_MENSAGEM)
    private String mensagemForaExpediente;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "horarios_atendimento", joinColumns = @JoinColumn(name = "empresa_id"))
    @OrderColumn(name = "posicao")
    private List<IntervaloAtendimento> horarios = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "assuntos_atendimento_automatico", joinColumns = @JoinColumn(name = "empresa_id"))
    @OrderColumn(name = "posicao")
    @Column(name = "assunto", nullable = false, length = LIMITE_ASSUNTO)
    private List<String> assuntosAutomaticos = new ArrayList<>();

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por")
    private Long atualizadoPor;

    protected ConfiguracaoAtendimento() {
    }

    public ConfiguracaoAtendimento(Empresa empresa) {
        this.empresa = Objects.requireNonNull(empresa, "A empresa é obrigatória.");
        this.fuso = FUSO_PADRAO.getId();
        this.mensagemForaExpediente = MENSAGEM_PADRAO;
        this.atualizadoEm = Instant.now();
    }

    public void atualizar(String fuso, String mensagemForaExpediente, List<IntervaloAtendimento> horarios,
            List<String> assuntos, Atendente responsavel) {
        Objects.requireNonNull(responsavel, "Informe quem fez a alteração.");
        if (!responsavel.pertenceA(empresa)) {
            throw new IllegalArgumentException("A configuração pertence a outra empresa.");
        }
        String fusoValidado = validarFuso(fuso);
        String mensagemValidada = validarMensagem(mensagemForaExpediente);
        List<IntervaloAtendimento> horariosValidados = validarHorarios(horarios);
        List<String> assuntosValidados = validarAssuntos(assuntos);

        this.fuso = fusoValidado;
        this.mensagemForaExpediente = mensagemValidada;
        this.horarios.clear();
        this.horarios.addAll(horariosValidados);
        this.assuntosAutomaticos.clear();
        this.assuntosAutomaticos.addAll(assuntosValidados);
        this.atualizadoEm = Instant.now();
        this.atualizadoPor = responsavel.getId();
    }

    public boolean equipeDisponivelEm(Instant momento) {
        ZonedDateTime local = Objects.requireNonNull(momento, "Informe o momento.").atZone(getFuso());
        return horarios.stream().anyMatch(h -> h.contem(local.getDayOfWeek(), local.toLocalTime()));
    }

    public Optional<ZonedDateTime> proximaAbertura(Instant momento) {
        ZonedDateTime agora = Objects.requireNonNull(momento, "Informe o momento.").atZone(getFuso());
        LocalDate hoje = agora.toLocalDate();
        for (int dias = 0; dias <= 7; dias++) {
            LocalDate data = hoje.plusDays(dias);
            Optional<ZonedDateTime> abertura = horarios.stream()
                    .filter(h -> h.getDiaSemana() == data.getDayOfWeek())
                    .map(h -> data.atTime(h.getAbertura()).atZone(getFuso()))
                    .filter(inicio -> inicio.isAfter(agora))
                    .min(Comparator.naturalOrder());
            if (abertura.isPresent()) {
                return abertura;
            }
        }
        return Optional.empty();
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public ZoneId getFuso() {
        return ZoneId.of(fuso);
    }

    public String getMensagemForaExpediente() {
        return mensagemForaExpediente;
    }

    public List<IntervaloAtendimento> getHorarios() {
        return Collections.unmodifiableList(horarios);
    }

    public List<String> getAssuntosAutomaticos() {
        return Collections.unmodifiableList(assuntosAutomaticos);
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Long getAtualizadoPor() {
        return atualizadoPor;
    }

    private static String validarFuso(String fuso) {
        if (fuso == null || fuso.isBlank()) {
            throw new IllegalArgumentException("Escolha o fuso horário da equipe.");
        }
        try {
            ZoneId zona = ZoneId.of(fuso.strip());
            if (!zona.getId().contains("/")) {
                throw new IllegalArgumentException("Use um fuso no formato Região/Cidade, por exemplo America/Recife.");
            }
            return zona.getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Fuso horário inválido: " + fuso.strip());
        }
    }

    private static String validarMensagem(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return MENSAGEM_PADRAO;
        }
        String limpa = mensagem.strip();
        if (limpa.length() > LIMITE_MENSAGEM) {
            throw new IllegalArgumentException(
                    "A mensagem fora do expediente pode ter no máximo " + LIMITE_MENSAGEM + " caracteres.");
        }
        return limpa;
    }

    private static List<IntervaloAtendimento> validarHorarios(List<IntervaloAtendimento> horarios) {
        if (horarios == null) {
            return List.of();
        }
        if (horarios.size() > LIMITE_INTERVALOS) {
            throw new IllegalArgumentException("Cadastre no máximo " + LIMITE_INTERVALOS + " períodos de atendimento.");
        }
        List<IntervaloAtendimento> ordenados = new ArrayList<>();
        for (IntervaloAtendimento intervalo : horarios) {
            Objects.requireNonNull(intervalo, "Há um período de atendimento vazio.");
            for (IntervaloAtendimento outro : ordenados) {
                if (intervalo.sobrepoe(outro)) {
                    throw new IllegalArgumentException("Há períodos que se sobrepõem no mesmo dia.");
                }
            }
            ordenados.add(intervalo);
        }
        ordenados.sort(Comparator.comparing(IntervaloAtendimento::getDiaSemana)
                .thenComparing(IntervaloAtendimento::getAbertura));
        return ordenados;
    }

    private static List<String> validarAssuntos(List<String> assuntos) {
        if (assuntos == null) {
            return List.of();
        }
        List<String> resultado = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (String assunto : assuntos) {
            if (assunto == null || assunto.isBlank()) {
                continue; // linha em branco no formulario
            }
            String limpo = assunto.strip();
            if (limpo.length() > LIMITE_ASSUNTO) {
                throw new IllegalArgumentException(
                        "Cada assunto pode ter no máximo " + LIMITE_ASSUNTO + " caracteres.");
            }
            if (vistos.add(limpo.toLowerCase(Locale.ROOT))) {
                resultado.add(limpo);
            }
        }
        if (resultado.size() > LIMITE_ASSUNTOS) {
            throw new IllegalArgumentException("Cadastre no máximo " + LIMITE_ASSUNTOS + " assuntos.");
        }
        return resultado;
    }
}
