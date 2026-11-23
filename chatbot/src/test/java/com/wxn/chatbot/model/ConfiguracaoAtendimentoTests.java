package com.wxn.chatbot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wxn.chatbot.dto.DisponibilidadeAtendimento;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;

class ConfiguracaoAtendimentoTests {

    private Empresa empresa;
    private Atendente admin;
    private ConfiguracaoAtendimento config;

    @BeforeEach
    void preparar() {
        empresa = new Empresa(1L, "Empresa A");
        admin = new Atendente(10L, "Ana", "ana@empresa-a.example", empresa, PerfilAcesso.ADMINISTRADOR);
        config = new ConfiguracaoAtendimento(empresa);
    }

    private static List<IntervaloAtendimento> segundaASexta(String abre, String fecha) {
        List<IntervaloAtendimento> lista = new ArrayList<>();
        for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            lista.add(new IntervaloAtendimento(dia, LocalTime.parse(abre), LocalTime.parse(fecha)));
        }
        return lista;
    }

    private static Instant recife(String dataHora) {
        return LocalDateTime.parse(dataHora).atZone(ConfiguracaoAtendimento.FUSO_PADRAO).toInstant();
    }

    @Test
    void semConfiguracaoDeHorarioEquipeNuncaEstaDisponivel() {
        assertFalse(config.equipeDisponivelEm(recife("2026-11-17T10:00")));
        assertTrue(config.proximaAbertura(recife("2026-11-17T10:00")).isEmpty());
    }

    @Test
    void aberturaEntraEFechamentoNaoEntraNoExpediente() {
        config.atualizar("America/Recife", null, segundaASexta("08:00", "18:00"), List.of(), admin);

        assertFalse(config.equipeDisponivelEm(recife("2026-11-17T07:59:59")));
        assertTrue(config.equipeDisponivelEm(recife("2026-11-17T08:00")));
        assertTrue(config.equipeDisponivelEm(recife("2026-11-17T17:59:59")));
        assertFalse(config.equipeDisponivelEm(recife("2026-11-17T18:00")));
    }

    @Test
    void fimDeSemanaSemHorarioFicaFora() {
        config.atualizar("America/Recife", null, segundaASexta("08:00", "18:00"), List.of(), admin);
        assertFalse(config.equipeDisponivelEm(recife("2026-11-21T10:00"))); // sabado
        assertFalse(config.equipeDisponivelEm(recife("2026-11-22T10:00"))); // domingo
    }

    @Test
    void mesmoInstanteMudaConformeOFusoDaEmpresa() {
        Instant onzeHorasUtc = Instant.parse("2026-11-17T11:00:00Z"); // 08:00 em Recife, 07:00 em Manaus

        config.atualizar("America/Recife", null, segundaASexta("08:00", "18:00"), List.of(), admin);
        assertTrue(config.equipeDisponivelEm(onzeHorasUtc));

        config.atualizar("America/Manaus", null, segundaASexta("08:00", "18:00"), List.of(), admin);
        assertFalse(config.equipeDisponivelEm(onzeHorasUtc));
    }

    @Test
    void diaDaSemanaEhCalculadoNoFusoDaEmpresa() {
        // Sabado 01:00 em UTC ainda e sexta 22:00 em Recife.
        config.atualizar("America/Recife", null,
                List.of(new IntervaloAtendimento(DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(23, 0))),
                List.of(), admin);
        assertTrue(config.equipeDisponivelEm(Instant.parse("2026-11-21T01:00:00Z")));
    }

    @Test
    void doisPeriodosNoMesmoDiaComPausaParaAlmoco() {
        config.atualizar("America/Recife", null, List.of(
                new IntervaloAtendimento(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new IntervaloAtendimento(DayOfWeek.TUESDAY, LocalTime.of(13, 0), LocalTime.of(17, 0))),
                List.of(), admin);
        assertTrue(config.equipeDisponivelEm(recife("2026-11-17T11:00")));
        assertFalse(config.equipeDisponivelEm(recife("2026-11-17T12:30")));
        assertTrue(config.equipeDisponivelEm(recife("2026-11-17T13:00")));
    }

    @Test
    void proximaAberturaNoMesmoDia() {
        config.atualizar("America/Recife", null, segundaASexta("08:00", "18:00"), List.of(), admin);
        assertEquals(LocalDateTime.parse("2026-11-17T08:00"),
                config.proximaAbertura(recife("2026-11-17T06:30")).orElseThrow().toLocalDateTime());
    }

    @Test
    void proximaAberturaNoDiaSeguinte() {
        config.atualizar("America/Recife", null, segundaASexta("08:00", "18:00"), List.of(), admin);
        assertEquals(LocalDateTime.parse("2026-11-18T08:00"),
                config.proximaAbertura(recife("2026-11-17T19:00")).orElseThrow().toLocalDateTime());
    }

    @Test
    void sextaANoitePulaOFimDeSemana() {
        config.atualizar("America/Recife", null, segundaASexta("08:00", "18:00"), List.of(), admin);
        assertEquals(LocalDateTime.parse("2026-11-23T08:00"),
                config.proximaAbertura(recife("2026-11-20T19:00")).orElseThrow().toLocalDateTime());
    }

    @Test
    void soUmDiaPorSemanaVoltaNaSemanaSeguinte() {
        config.atualizar("America/Recife", null,
                List.of(new IntervaloAtendimento(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(9, 0))),
                List.of(), admin);
        assertEquals(LocalDateTime.parse("2026-11-24T08:00"),
                config.proximaAbertura(recife("2026-11-17T08:30")).orElseThrow().toLocalDateTime());
    }

    @Test
    void aberturaDepoisDoFechamentoEhRecusada() {
        assertThrows(IllegalArgumentException.class,
                () -> new IntervaloAtendimento(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(8, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new IntervaloAtendimento(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    void periodosSobrepostosSaoRecusados() {
        List<IntervaloAtendimento> sobrepostos = List.of(
                new IntervaloAtendimento(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new IntervaloAtendimento(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(14, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar("America/Recife", null, sobrepostos, List.of(), admin));
    }

    @Test
    void periodosEncostadosSaoAceitos() {
        config.atualizar("America/Recife", null, List.of(
                new IntervaloAtendimento(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new IntervaloAtendimento(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0))),
                List.of(), admin);
        assertEquals(2, config.getHorarios().size());
    }

    @Test
    void fusoInvalidoOuSemCidadeEhRecusado() {
        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar("Recife/Inexistente", null, List.of(), List.of(), admin));
        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar("UTC", null, List.of(), List.of(), admin));
        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar(" ", null, List.of(), List.of(), admin));
    }

    @Test
    void mensagemEmBrancoUsaOPadraoEMensagemLongaEhRecusada() {
        config.atualizar("America/Recife", "   ", List.of(), List.of(), admin);
        assertEquals(ConfiguracaoAtendimento.MENSAGEM_PADRAO, config.getMensagemForaExpediente());

        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar("America/Recife", "a".repeat(501), List.of(), List.of(), admin));
    }

    @Test
    void assuntosIgnoramLinhasVaziasERepetidos() {
        config.atualizar("America/Recife", null, List.of(),
                java.util.Arrays.asList(" Boletos ", "", null, "boletos", "Horário de funcionamento"), admin);
        assertEquals(List.of("Boletos", "Horário de funcionamento"), config.getAssuntosAutomaticos());
    }

    @Test
    void muitosAssuntosOuAssuntoLongoSaoRecusados() {
        List<String> vinteEUm = IntStream.rangeClosed(1, 21).mapToObj(i -> "Assunto " + i).toList();
        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar("America/Recife", null, List.of(), vinteEUm, admin));
        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar("America/Recife", null, List.of(), List.of("x".repeat(81)), admin));
    }

    @Test
    void administradorDeOutraEmpresaNaoAltera() {
        Empresa outra = new Empresa(2L, "Empresa B");
        Atendente adminB = new Atendente(20L, "Bia", "bia@empresa-b.example", outra, PerfilAcesso.ADMINISTRADOR);
        assertThrows(IllegalArgumentException.class,
                () -> config.atualizar("America/Recife", null, List.of(), List.of(), adminB));
    }

    @Test
    void erroNaoDeixaConfiguracaoPelaMetade() {
        config.atualizar("America/Recife", "Volte amanhã.", segundaASexta("08:00", "18:00"), List.of("Boletos"), admin);

        assertThrows(IllegalArgumentException.class, () -> config.atualizar("America/Manaus", "Outra",
                List.of(), List.of("y".repeat(81)), admin));

        assertEquals("America/Recife", config.getFuso().getId());
        assertEquals("Volte amanhã.", config.getMensagemForaExpediente());
        assertEquals(5, config.getHorarios().size());
        assertEquals(List.of("Boletos"), config.getAssuntosAutomaticos());
    }

    @Test
    void listaVaziaDeAssuntosNaoRestringe() {
        var semLista = new DisponibilidadeAtendimento(1L, true, false, ConfiguracaoAtendimento.FUSO_PADRAO,
                null, List.of(), "x");
        assertTrue(semLista.assuntoAtendidoAutomaticamente("qualquer coisa"));

        var comLista = new DisponibilidadeAtendimento(1L, true, false, ConfiguracaoAtendimento.FUSO_PADRAO,
                null, List.of("Boletos"), "x");
        assertTrue(comLista.assuntoAtendidoAutomaticamente(" boletos "));
        assertFalse(comLista.assuntoAtendidoAutomaticamente("Cancelamento"));
        assertFalse(comLista.assuntoAtendidoAutomaticamente(null));
    }
}
