package com.wxn.chatbot.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.DisponibilidadeAtendimento;
import com.wxn.chatbot.model.ConfiguracaoAtendimento;
import com.wxn.chatbot.repository.ConfiguracaoAtendimentoRepository;

@Service
public class DisponibilidadeAtendimentoService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PT_BR);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE, dd/MM", PT_BR);

    private final ConfiguracaoAtendimentoRepository configuracoes;

    public DisponibilidadeAtendimentoService(ConfiguracaoAtendimentoRepository configuracoes) {
        this.configuracoes = configuracoes;
    }

    @Transactional(readOnly = true)
    public DisponibilidadeAtendimento consultar(Long empresaId) {
        return consultar(empresaId, Instant.now());
    }

    @Transactional(readOnly = true)
    public DisponibilidadeAtendimento consultar(Long empresaId, Instant momento) {
        validarEmpresa(empresaId);
        if (momento == null) {
            throw new IllegalArgumentException("Informe o momento da consulta.");
        }
        Optional<ConfiguracaoAtendimento> salva = configuracoes.findById(empresaId);
        if (salva.isEmpty()) {
            // Empresa que ainda nao configurou: nada muda em relacao ao comportamento antigo.
            return new DisponibilidadeAtendimento(empresaId, false, false, ConfiguracaoAtendimento.FUSO_PADRAO,
                    Optional.empty(), java.util.List.of(), ConfiguracaoAtendimento.MENSAGEM_PADRAO);
        }
        ConfiguracaoAtendimento c = salva.get();
        return new DisponibilidadeAtendimento(empresaId, true, c.equipeDisponivelEm(momento), c.getFuso(),
                c.proximaAbertura(momento), c.getAssuntosAutomaticos(), c.getMensagemForaExpediente());
    }

    @Transactional(readOnly = true)
    public Optional<String> mensagemSolicitacaoHumana(Long empresaId, Instant momento) {
        DisponibilidadeAtendimento d = consultar(empresaId, momento);
        if (!d.configurada() || d.equipeHumanaDisponivel()) {
            return Optional.empty();
        }
        String quando = d.proximaAbertura()
                .map(abertura -> "Um atendente poderá responder a partir de " + descreverAbertura(abertura, momento) + ".")
                .orElse("Um atendente responderá assim que houver alguém da equipe disponível.");
        return Optional.of(d.mensagemForaExpediente() + "\n"
                + "Sua solicitação ficou registrada na fila de atendimento humano. " + quando + "\n"
                + "Você pode continuar escrevendo: suas mensagens ficam guardadas para o atendente.");
    }

    @Transactional(readOnly = true)
    public String contextoParaIa(Long empresaId, Instant momento) {
        DisponibilidadeAtendimento d = consultar(empresaId, momento);
        StringJoiner texto = new StringJoiner("\n");
        if (!d.configurada()) {
            texto.add("Horário da equipe humana: não configurado pela empresa. Não informe horários.");
        } else if (d.equipeHumanaDisponivel()) {
            texto.add("Equipe humana: em expediente agora. O cliente pode pedir atendente com /atendente.");
        } else {
            texto.add("Equipe humana: fora do expediente agora. Não prometa resposta imediata de um atendente.");
            d.proximaAbertura().ifPresent(a -> texto.add("Próximo expediente: " + descreverAbertura(a, momento) + "."));
        }
        if (d.assuntosAutomaticos().isEmpty()) {
            texto.add("Assuntos atendidos automaticamente: a empresa não restringiu.");
        } else {
            texto.add("Assuntos atendidos automaticamente: " + String.join("; ", d.assuntosAutomaticos())
                    + ". Para outros assuntos, ofereça atendimento humano (/atendente).");
        }
        return texto.toString();
    }
    
    static String descreverAbertura(ZonedDateTime abertura, Instant agora) {
        LocalDate hoje = agora.atZone(abertura.getZone()).toLocalDate();
        LocalDate dia = abertura.toLocalDate();
        String hora = abertura.format(HORA);
        if (dia.equals(hoje)) {
            return "hoje às " + hora;
        }
        if (dia.equals(hoje.plusDays(1))) {
            return "amanhã às " + hora;
        }
        return abertura.format(DIA) + ", às " + hora;
    }

    private static void validarEmpresa(Long empresaId) {
        if (empresaId == null || empresaId <= 0) {
            throw new IllegalArgumentException("Empresa inválida.");
        }
    }
}
