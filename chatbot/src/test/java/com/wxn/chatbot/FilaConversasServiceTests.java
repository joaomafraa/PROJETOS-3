package com.wxn.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FilaConversasServiceTests {

    @Mock
    private ConversaRepository conversaRepository;

    private FilaConversasService service;
    private Empresa empresa;

    @BeforeEach
    void configurar() {
        service = new FilaConversasService(conversaRepository);
        empresa = new Empresa(1L, "WXN");
    }

    @Test
    void consultaSomenteAFilaDaEmpresaDoAtendente() {
        Atendente atendente = novoAtendente(1L, Atendente.PerfilAcesso.ATENDENTE);
        List<Conversa> fila = List.of();
        when(conversaRepository.findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
                empresa.getId(),
                Conversa.Status.AGUARDANDO_ATENDENTE)).thenReturn(fila);

        assertEquals(fila, service.visualizarFila(atendente));
        verify(conversaRepository).findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
                empresa.getId(),
                Conversa.Status.AGUARDANDO_ATENDENTE);
    }

    @Test
    void rejeitaAtendenteInativoAntesDeConsultarOBanco() {
        Atendente atendente = novoAtendente(1L, Atendente.PerfilAcesso.ADMINISTRADOR);
        Atendente outroAdministrador = novoAtendente(2L, Atendente.PerfilAcesso.ADMINISTRADOR);
        atendente.desativar(outroAdministrador);

        assertThrows(SecurityException.class, () -> service.visualizarFila(atendente));
        verifyNoInteractions(conversaRepository);
    }

    @Test
    void exigeAtendente() {
        assertThrows(NullPointerException.class, () -> service.visualizarFila(null));
        verifyNoInteractions(conversaRepository);
    }

    private Atendente novoAtendente(Long id, Atendente.PerfilAcesso perfil) {
        return new Atendente(id, "Atendente " + id, "atendente" + id + "@wxn.com", empresa, perfil);
    }
}
