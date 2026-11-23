package com.wxn.chatbot.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.ConfiguracaoAtendimentoResumo;
import com.wxn.chatbot.dto.SalvarConfiguracaoAtendimentoComando;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.ConfiguracaoAtendimento;
import com.wxn.chatbot.model.IntervaloAtendimento;
import com.wxn.chatbot.repository.AtendenteRepository;
import com.wxn.chatbot.repository.ConfiguracaoAtendimentoRepository;

@Service
public class ConfiguracaoAtendimentoService {

    private static final String SEM_PERMISSAO = "Somente administradores ativos podem configurar o atendimento.";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ConfiguracaoAtendimentoRepository configuracoes;
    private final AtendenteRepository atendentes;

    public ConfiguracaoAtendimentoService(ConfiguracaoAtendimentoRepository configuracoes,
            AtendenteRepository atendentes) {
        this.configuracoes = configuracoes;
        this.atendentes = atendentes;
    }

    @Transactional(readOnly = true)
    public ConfiguracaoAtendimentoResumo obter(Long solicitanteId) {
        Atendente admin = exigirAdministrador(solicitanteId);
        Long empresaId = admin.getEmpresa().getId();
        return configuracoes.findById(empresaId)
                .map(c -> resumir(c, true))
                .orElseGet(() -> resumir(new ConfiguracaoAtendimento(admin.getEmpresa()), false));
    }

    @Transactional
    public ConfiguracaoAtendimentoResumo salvar(Long solicitanteId, SalvarConfiguracaoAtendimentoComando comando) {
        if (comando == null) {
            throw new IllegalArgumentException("Envie os dados da configuração.");
        }
        Atendente admin = exigirAdministrador(solicitanteId);
        List<IntervaloAtendimento> horarios = converterHorarios(comando.horarios());

        ConfiguracaoAtendimento configuracao = configuracoes.findById(admin.getEmpresa().getId())
                .orElseGet(() -> new ConfiguracaoAtendimento(admin.getEmpresa()));
        configuracao.atualizar(comando.fuso(), comando.mensagemForaExpediente(), horarios,
                comando.assuntosAutomaticos(), admin);
        ConfiguracaoAtendimento salva = configuracoes.saveAndFlush(configuracao);
        return resumir(salva, true);
    }

    private Atendente exigirAdministrador(Long solicitanteId) {
        if (solicitanteId == null || solicitanteId <= 0) {
            throw new SecurityException(SEM_PERMISSAO);
        }
        Atendente atendente = atendentes.findById(solicitanteId)
                .orElseThrow(() -> new SecurityException(SEM_PERMISSAO));
        if (!atendente.isAtivo() || !atendente.possuiPerfil(PerfilAcesso.ADMINISTRADOR)) {
            throw new SecurityException(SEM_PERMISSAO);
        }
        return atendente;
    }

    private static List<IntervaloAtendimento> converterHorarios(List<ConfiguracaoAtendimentoResumo.Horario> horarios) {
        List<IntervaloAtendimento> convertidos = new ArrayList<>();
        if (horarios == null) {
            return convertidos;
        }
        for (ConfiguracaoAtendimentoResumo.Horario h : horarios) {
            if (h == null) {
                throw new IllegalArgumentException("Há um período de atendimento vazio.");
            }
            convertidos.add(new IntervaloAtendimento(dia(h.diaSemana()), hora(h.abertura(), "abertura"),
                    hora(h.fechamento(), "fechamento")));
        }
        return convertidos;
    }

    private static DayOfWeek dia(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Informe o dia da semana de cada período.");
        }
        try {
            return DayOfWeek.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Dia da semana inválido: " + valor.strip());
        }
    }

    private static LocalTime hora(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Informe o horário de " + campo + ".");
        }
        try {
            return LocalTime.parse(valor.strip(), HORA);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Horário de " + campo + " inválido: use o formato HH:mm.");
        }
    }

    private static ConfiguracaoAtendimentoResumo resumir(ConfiguracaoAtendimento c, boolean configurada) {
        Instant agora = Instant.now();
        List<ConfiguracaoAtendimentoResumo.Horario> horarios = c.getHorarios().stream()
                .map(h -> new ConfiguracaoAtendimentoResumo.Horario(h.getDiaSemana().name(),
                        h.getAbertura().format(HORA), h.getFechamento().format(HORA)))
                .toList();
        String proxima = c.proximaAbertura(agora)
                .map(a -> DisponibilidadeAtendimentoService.descreverAbertura(a, agora))
                .orElse(null);
        return new ConfiguracaoAtendimentoResumo(
                configurada,
                c.getFuso().getId(),
                c.getMensagemForaExpediente(),
                horarios,
                List.copyOf(c.getAssuntosAutomaticos()),
                configurada ? c.getAtualizadoEm().toString() : null,
                configurada && c.equipeDisponivelEm(agora),
                proxima);
    }
}
