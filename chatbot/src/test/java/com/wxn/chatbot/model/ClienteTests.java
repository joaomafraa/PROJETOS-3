package com.wxn.chatbot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteTests {

    private Empresa empresaExemplo;
    private Empresa empresaAlfa;
    private Cliente cliente;

    @BeforeEach
    void prepararDados() {
        empresaExemplo = new Empresa(1L, "Empresa Exemplo");
        empresaAlfa = new Empresa(2L, "Empresa Alfa");
        cliente = new Cliente(1L, "81999990000");
    }


    @Test
    void criaClienteSoComTelefone() {
        assertEquals(1L, cliente.getId());
        assertEquals("81999990000", cliente.getNumeroTelefone());
        assertNull(cliente.getNome());
        assertNull(cliente.getEmail());
        assertNull(cliente.getEmpresa());
        assertFalse(cliente.isIdentidadeConfirmada());
    }

    @Test
    void removeEspacosETracosDoTelefone() {
        Cliente outro = new Cliente(2L, "+55 81 99999-0000");
        assertEquals("+5581999990000", outro.getNumeroTelefone());
    }

    @Test
    void recusaIdInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Cliente(null, "81999990000"));
        assertThrows(IllegalArgumentException.class, () -> new Cliente(0L, "81999990000"));
        assertThrows(IllegalArgumentException.class, () -> new Cliente(-5L, "81999990000"));
    }

    @Test
    void recusaTelefoneInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Cliente(2L, null));
        assertThrows(IllegalArgumentException.class, () -> new Cliente(2L, "123"));
    }


    @Test
    void alteraNomeERemoveEspacosDasPontas() {
        cliente.alterarNome("  Marina Costa  ");
        assertEquals("Marina Costa", cliente.getNome());
    }

    @Test
    void recusaNomeVazio() {
        assertThrows(IllegalArgumentException.class, () -> cliente.alterarNome(null));
        assertThrows(IllegalArgumentException.class, () -> cliente.alterarNome("   "));
    }


    @Test
    void atualizaEmailEmMinusculas() {
        cliente.atualizarEmail("Marina@Exemplo.com");
        assertEquals("marina@exemplo.com", cliente.getEmail());
    }

    @Test
    void recusaEmailInvalido() {
        assertThrows(IllegalArgumentException.class, () -> cliente.atualizarEmail(null));
        assertThrows(IllegalArgumentException.class, () -> cliente.atualizarEmail("marina"));
        assertThrows(IllegalArgumentException.class, () -> cliente.atualizarEmail("marina@exemplo"));
    }

    @Test
    void trocarEmailDesfazConfirmacao() {
        cliente.atualizarEmail("marina@exemplo.com");
        cliente.confirmarIdentidade();

        cliente.atualizarEmail("novo@exemplo.com");

        assertFalse(cliente.isIdentidadeConfirmada());
    }

    @Test
    void trocarTelefoneDesfazConfirmacao() {
        cliente.atualizarEmail("marina@exemplo.com");
        cliente.confirmarIdentidade();

        cliente.atualizarTelefone("81988887777");

        assertEquals("81988887777", cliente.getNumeroTelefone());
        assertFalse(cliente.isIdentidadeConfirmada());
    }


    @Test
    void confirmaIdentidadeQuandoTemEmail() {
        cliente.atualizarEmail("marina@exemplo.com");
        cliente.confirmarIdentidade();
        assertTrue(cliente.isIdentidadeConfirmada());
    }

    @Test
    void naoConfirmaIdentidadeSemEmail() {
        assertThrows(IllegalStateException.class, () -> cliente.confirmarIdentidade());
        assertFalse(cliente.isIdentidadeConfirmada());
    }


    @Test
    void vinculaEmpresaNoCadastro() {
        cliente.vincularEmpresa(empresaExemplo);
        assertEquals(empresaExemplo, cliente.getEmpresa());
    }

    @Test
    void naoVinculaEmpresaNula() {
        assertThrows(IllegalArgumentException.class, () -> cliente.vincularEmpresa(null));
    }

    @Test
    void naoVinculaSegundaEmpresa() {
        cliente.vincularEmpresa(empresaExemplo);
        assertThrows(IllegalStateException.class, () -> cliente.vincularEmpresa(empresaAlfa));
        assertEquals(empresaExemplo, cliente.getEmpresa());
    }


    @Test
    void adminDaEmpresaAtualTrocaEmpresa() {
        cliente.vincularEmpresa(empresaExemplo);
        Atendente admin = criarAtendente(10L, empresaExemplo, Atendente.PerfilAcesso.ADMINISTRADOR);

        cliente.trocarEmpresa(empresaAlfa, admin);

        assertEquals(empresaAlfa, cliente.getEmpresa());
    }

    @Test
    void atendenteComumNaoTrocaEmpresa() {
        cliente.vincularEmpresa(empresaExemplo);
        Atendente atendente = criarAtendente(11L, empresaExemplo, Atendente.PerfilAcesso.ATENDENTE);

        assertThrows(SecurityException.class, () -> cliente.trocarEmpresa(empresaAlfa, atendente));
        assertEquals(empresaExemplo, cliente.getEmpresa());
    }

    @Test
    void adminInativoNaoTrocaEmpresa() {
        cliente.vincularEmpresa(empresaExemplo);
        Atendente admin = criarAtendente(10L, empresaExemplo, Atendente.PerfilAcesso.ADMINISTRADOR);
        Atendente outroAdmin = criarAtendente(12L, empresaExemplo, Atendente.PerfilAcesso.ADMINISTRADOR);
        admin.desativar(outroAdmin);

        assertThrows(SecurityException.class, () -> cliente.trocarEmpresa(empresaAlfa, admin));
        assertEquals(empresaExemplo, cliente.getEmpresa());
    }

    @Test
    void adminDeOutraEmpresaNaoPuxaCliente() {
        cliente.vincularEmpresa(empresaExemplo);
        Atendente adminAlfa = criarAtendente(20L, empresaAlfa, Atendente.PerfilAcesso.ADMINISTRADOR);

        assertThrows(SecurityException.class, () -> cliente.trocarEmpresa(empresaAlfa, adminAlfa));
        assertEquals(empresaExemplo, cliente.getEmpresa());
    }

    @Test
    void naoTrocaParaAMesmaEmpresa() {
        cliente.vincularEmpresa(empresaExemplo);
        Atendente admin = criarAtendente(10L, empresaExemplo, Atendente.PerfilAcesso.ADMINISTRADOR);

        assertThrows(IllegalArgumentException.class, () -> cliente.trocarEmpresa(empresaExemplo, admin));
    }

    @Test
    void naoTrocaEmpresaSemEmpresaAtual() {
        Atendente admin = criarAtendente(10L, empresaExemplo, Atendente.PerfilAcesso.ADMINISTRADOR);
        assertThrows(IllegalStateException.class, () -> cliente.trocarEmpresa(empresaAlfa, admin));
    }

    @Test
    void naoTrocaEmpresaComDadosNulos() {
        cliente.vincularEmpresa(empresaExemplo);
        Atendente admin = criarAtendente(10L, empresaExemplo, Atendente.PerfilAcesso.ADMINISTRADOR);

        assertThrows(IllegalArgumentException.class, () -> cliente.trocarEmpresa(null, admin));
        assertThrows(IllegalArgumentException.class, () -> cliente.trocarEmpresa(empresaAlfa, null));
    }


    private Atendente criarAtendente(Long id, Empresa empresa, Atendente.PerfilAcesso perfil) {
        return new Atendente(id, "Atendente " + id, "atendente" + id + "@exemplo.com", empresa, perfil);
    }
}
