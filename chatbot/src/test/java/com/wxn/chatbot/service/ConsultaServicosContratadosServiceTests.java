package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.ServicoContratado;
import com.wxn.chatbot.repository.ServicoContratadoRepository;
import com.wxn.chatbot.security.ValidacaoClienteService;
import com.wxn.chatbot.service.ConsultaServicosContratadosService.Situacao;

class ConsultaServicosContratadosServiceTests {

    private final ValidacaoClienteService validacao = mock(ValidacaoClienteService.class);
    private final ServicoContratadoRepository repositorio = mock(ServicoContratadoRepository.class);
    private final ConsultaServicosContratadosService consulta = new ConsultaServicosContratadosService(validacao, repositorio);

    @Test
    void usaOClienteDaValidacaoENaoOutro() {
        when(validacao.exigirContextoValidado(1L, 3L)).thenReturn(new ValidacaoClienteService.ContextoClienteValidado(1L, 7L, 3L));
        when(repositorio.doCliente(1L, 7L)).thenReturn(List.of(servico("Plano Básico")));

        var resultado = consulta.consultar(1L, 3L);

        assertEquals(Situacao.ENCONTRADO, resultado.situacao());
        verify(repositorio).doCliente(1L, 7L);
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    void falhaNoBancoViraIndisponivelENaoListaVazia() {
        when(validacao.exigirContextoValidado(1L, 3L)).thenReturn(new ValidacaoClienteService.ContextoClienteValidado(1L, 7L, 3L));
        when(repositorio.doCliente(1L, 7L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("conexão perdida"));

        var resultado = consulta.consultar(1L, 3L);

        assertEquals(Situacao.INDISPONIVEL, resultado.situacao());
        assertTrue(resultado.itens().isEmpty());
    }

    @Test
    void validacaoAusenteOuComErroNaoConsultaORepositorio() {
        when(validacao.exigirContextoValidado(1L, 3L)).thenThrow(new SecurityException("Validacao de identidade necessaria."));
        when(validacao.exigirContextoValidado(1L, 4L)).thenThrow(new IllegalArgumentException("Conversa nao encontrada."));

        assertEquals(Situacao.VALIDACAO_NECESSARIA, consulta.consultar(1L, 3L).situacao());
        assertEquals(Situacao.INDISPONIVEL, consulta.consultar(1L, 4L).situacao());
        verifyNoInteractions(repositorio);
    }

    @Test
    void limitaQuantidadeDeItens() {
        when(validacao.exigirContextoValidado(1L, 3L)).thenReturn(new ValidacaoClienteService.ContextoClienteValidado(1L, 7L, 3L));
        List<ServicoContratado> muitos = new ArrayList<>();
        for (int i = 0; i < 30; i++) muitos.add(servico("Serviço " + i));
        when(repositorio.doCliente(1L, 7L)).thenReturn(muitos);

        assertEquals(ConsultaServicosContratadosService.MAXIMO_ITENS, consulta.consultar(1L, 3L).itens().size());
    }

    @Test
    void resultadoSoTemItensQuandoEncontrou() {
        var item = new ConsultaServicosContratadosService.Item(ServicoContratado.Tipo.PLANO, "Plano", null,
                ServicoContratado.Situacao.ATIVO, LocalDate.of(2026, 1, 1), null);
        assertThrows(IllegalArgumentException.class, () -> new ConsultaServicosContratadosService.Resultado(Situacao.ENCONTRADO, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConsultaServicosContratadosService.Resultado(Situacao.INDISPONIVEL, List.of(item)));
    }

    @Test
    void entidadeValidaClienteNomeEPeriodo() {
        Cliente semEmpresa = new Cliente(1L, "81999990000");
        assertThrows(IllegalArgumentException.class, () -> new ServicoContratado(semEmpresa, ServicoContratado.Tipo.PLANO,
                "Plano", null, ServicoContratado.Situacao.ATIVO, LocalDate.of(2026, 1, 1), null));
        Cliente cliente = clienteComEmpresa();
        assertThrows(IllegalArgumentException.class, () -> new ServicoContratado(cliente, ServicoContratado.Tipo.PLANO,
                " ", null, ServicoContratado.Situacao.ATIVO, LocalDate.of(2026, 1, 1), null));
        assertThrows(IllegalArgumentException.class, () -> new ServicoContratado(cliente, ServicoContratado.Tipo.PLANO,
                "Plano", null, ServicoContratado.Situacao.ATIVO, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1)));
        ServicoContratado valido = new ServicoContratado(cliente, ServicoContratado.Tipo.SERVICO, " Backup ", "  ",
                ServicoContratado.Situacao.SUSPENSO, LocalDate.of(2026, 1, 1), null);
        assertEquals("Backup", valido.getNome());
        assertNull(valido.getDetalhe());
        assertSame(cliente.getEmpresa(), valido.getEmpresa());
        assertTrue(valido.isDemonstracao());
    }

    private static ServicoContratado servico(String nome) {
        return new ServicoContratado(clienteComEmpresa(), ServicoContratado.Tipo.PLANO, nome, null,
                ServicoContratado.Situacao.ATIVO, LocalDate.of(2026, 1, 1), null);
    }

    private static Cliente clienteComEmpresa() {
        Cliente cliente = new Cliente(7L, "81999990000");
        cliente.vincularEmpresa(new Empresa(1L, "Empresa"));
        return cliente;
    }
}
