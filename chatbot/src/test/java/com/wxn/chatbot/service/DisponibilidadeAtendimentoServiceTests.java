package com.wxn.chatbot.service;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.ConfiguracaoAtendimento;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.IntervaloAtendimento;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:disponibilidade-atendimento;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class DisponibilidadeAtendimentoServiceTests {

    private static final long EMPRESA_A = 41L;
    private static final long EMPRESA_B = 42L;

    @Autowired private DisponibilidadeAtendimentoService disponibilidade;
    @Autowired private EntityManager em;

    private static Instant recife(String dataHora) {
        return LocalDateTime.parse(dataHora).atZone(ConfiguracaoAtendimento.FUSO_PADRAO).toInstant();
    }

    @BeforeEach
    void preparar() {
        Empresa a = new Empresa(EMPRESA_A, "Empresa A");
        Empresa b = new Empresa(EMPRESA_B, "Empresa B");
        em.persist(a);
        em.persist(b);
        Atendente admin = new Atendente(401L, "Ana", "ana@disp-a.example", a, PerfilAcesso.ADMINISTRADOR);
        em.persist(admin);

        List<IntervaloAtendimento> horarios = new ArrayList<>();
        for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            horarios.add(new IntervaloAtendimento(dia, LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        ConfiguracaoAtendimento config = new ConfiguracaoAtendimento(a);
        config.atualizar("America/Recife", "Nossa equipe atende de segunda a sexta, das 8h às 18h.",
                horarios, List.of("Boletos", "Horário de funcionamento"), admin);
        em.persist(config);
        em.flush();
        em.clear(); // garante que os testes leem do banco, nao da memoria
    }

    @Test
    void empresaSemConfiguracaoMantemComportamentoAntigo() {
        var d = disponibilidade.consultar(EMPRESA_B, recife("2026-11-17T10:00"));
        assertFalse(d.configurada());
        assertFalse(d.equipeHumanaDisponivel());
        assertTrue(d.assuntosAutomaticos().isEmpty());
        assertTrue(disponibilidade.mensagemSolicitacaoHumana(EMPRESA_B, recife("2026-11-17T23:00")).isEmpty());
        assertTrue(disponibilidade.contextoParaIa(EMPRESA_B, recife("2026-11-17T10:00")).contains("não configurado"));
    }

    @Test
    void configuracaoDeUmaEmpresaNaoValeParaOutra() {
        assertTrue(disponibilidade.consultar(EMPRESA_A, recife("2026-11-17T10:00")).configurada());
        assertFalse(disponibilidade.consultar(EMPRESA_B, recife("2026-11-17T10:00")).configurada());
    }

    @Test
    void dentroDoExpedienteUsaAConfirmacaoDeSempre() {
        var d = disponibilidade.consultar(EMPRESA_A, recife("2026-11-17T10:00"));
        assertTrue(d.equipeHumanaDisponivel());
        assertEquals(List.of("Boletos", "Horário de funcionamento"), d.assuntosAutomaticos());
        assertTrue(disponibilidade.mensagemSolicitacaoHumana(EMPRESA_A, recife("2026-11-17T10:00")).isEmpty());
    }

    @Test
    void foraDoExpedienteExplicaQueFicouNaFilaSemPrometerRespostaImediata() {
        String mensagem = disponibilidade.mensagemSolicitacaoHumana(EMPRESA_A, recife("2026-11-17T19:00")).orElseThrow();

        assertTrue(mensagem.startsWith("Nossa equipe atende de segunda a sexta, das 8h às 18h."));
        assertTrue(mensagem.contains("ficou registrada na fila"));
        assertTrue(mensagem.contains("amanhã às 08:00"));
        assertFalse(mensagem.toLowerCase().contains("imediat"));
    }

    @Test
    void sextaANoiteInformaASegundaFeira() {
        String mensagem = disponibilidade.mensagemSolicitacaoHumana(EMPRESA_A, recife("2026-11-20T19:00")).orElseThrow();
        assertTrue(mensagem.contains("segunda-feira, 23/11, às 08:00"), mensagem);
    }

    @Test
    void antesDeAbrirNoMesmoDiaInformaHoje() {
        String mensagem = disponibilidade.mensagemSolicitacaoHumana(EMPRESA_A, recife("2026-11-17T06:00")).orElseThrow();
        assertTrue(mensagem.contains("hoje às 08:00"), mensagem);
    }

    @Test
    void contextoDaIaInformaExpedienteEAssuntos() {
        String fora = disponibilidade.contextoParaIa(EMPRESA_A, recife("2026-11-17T19:00"));
        assertTrue(fora.contains("fora do expediente"));
        assertTrue(fora.contains("Não prometa resposta imediata"));
        assertTrue(fora.contains("Boletos; Horário de funcionamento"));

        String dentro = disponibilidade.contextoParaIa(EMPRESA_A, recife("2026-11-17T10:00"));
        assertTrue(dentro.contains("em expediente agora"));
    }

    @Test
    void empresaOuMomentoInvalidoSaoRecusados() {
        assertThrows(IllegalArgumentException.class, () -> disponibilidade.consultar(0L, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> disponibilidade.consultar(null, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> disponibilidade.consultar(EMPRESA_A, null));
    }
}
