package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.ResponsavelNotificacaoResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.ResponsavelNotificacao;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:responsaveis-notificacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class ResponsaveisNotificacaoServiceTests {

    private static final AtomicLong IDS = new AtomicLong(600_000);

    @Autowired private ResponsaveisNotificacaoService service;
    @Autowired private AdministracaoAtendentesService administracao;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;

    private TransactionTemplate tx;
    private long empresaA;
    private long admin;
    private long ana;
    private long carla;
    private long inativo;
    private long adminB;
    private long bruno;

    @BeforeEach
    void preparar() {
        long base = IDS.getAndAdd(20);
        empresaA = base;
        long empresaB = base + 1;
        admin = base;
        ana = base + 1;
        carla = base + 2;
        inativo = base + 3;
        adminB = base + 4;
        bruno = base + 5;
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            Empresa a = new Empresa(empresaA, "Empresa A");
            Empresa b = new Empresa(empresaB, "Empresa B");
            em.persist(a);
            em.persist(b);
            Atendente adm = new Atendente(admin, "Admin", "admin@a.com", a, PerfilAcesso.ADMINISTRADOR);
            em.persist(adm);
            em.persist(new Atendente(ana, "Ana", "ana@a.com", a, PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(carla, "Carla", "carla@a.com", a, PerfilAcesso.COORDENADOR));
            Atendente in = new Atendente(inativo, "Ivo", "ivo@a.com", a, PerfilAcesso.ATENDENTE);
            in.desativar(adm);
            em.persist(in);
            em.persist(new Atendente(adminB, "Admin B", "admin@b.com", b, PerfilAcesso.ADMINISTRADOR));
            em.persist(new Atendente(bruno, "Bruno", "bruno@b.com", b, PerfilAcesso.ATENDENTE));
        });
    }

    private List<Long> idsSalvos(TipoNotificacao tipo) {
        return tx.execute(s -> em.createQuery(
                "select r.atendente.id from ResponsavelNotificacao r "
                        + "where r.empresa.id = :e and r.tipo = :t order by r.atendente.id", Long.class)
                .setParameter("e", empresaA).setParameter("t", tipo).getResultList());
    }

    @Test
    void definePersisteEListaResponsaveis() {
        List<ResponsavelNotificacaoResumo> r = service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(carla, ana, ana));

        assertEquals(List.of(ana, carla), r.stream().map(ResponsavelNotificacaoResumo::atendenteId).toList());
        assertEquals(List.of(ana, carla), idsSalvos(TipoNotificacao.NOVO_TICKET));
        assertEquals(2, service.listar(admin).size());
    }

    @Test
    void substituiListaEListaVaziaRemove() {
        service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(ana, carla));
        service.definir(admin, TipoNotificacao.NOVO_LEAD, List.of(ana));

        service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(carla));
        assertEquals(List.of(carla), idsSalvos(TipoNotificacao.NOVO_TICKET));

        service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of());
        assertEquals(List.of(), idsSalvos(TipoNotificacao.NOVO_TICKET));
        assertEquals(List.of(ana), idsSalvos(TipoNotificacao.NOVO_LEAD), "Outro tipo não deve ser afetado");
    }

    @Test
    void rejeitaAtendenteDeOutraEmpresaSemAlterarConfiguracao() {
        service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(ana));
        assertThrows(NoSuchElementException.class,
                () -> service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(carla, bruno)));
        assertEquals(List.of(ana), idsSalvos(TipoNotificacao.NOVO_TICKET));
    }

    @Test
    void rejeitaAtendenteInativoSemAlterarConfiguracao() {
        service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(ana));
        assertThrows(IllegalStateException.class,
                () -> service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(carla, inativo)));
        assertEquals(List.of(ana), idsSalvos(TipoNotificacao.NOVO_TICKET));
    }

    @Test
    void somenteAdministradorAtivoConfigura() {
        assertThrows(SecurityException.class,
                () -> service.definir(carla, TipoNotificacao.NOVO_TICKET, List.of(ana)));
        assertThrows(SecurityException.class, () -> service.listar(ana));
        assertThrows(NullPointerException.class, () -> service.definir(admin, null, List.of(ana)));
        assertEquals(List.of(), idsSalvos(TipoNotificacao.NOVO_TICKET));
    }

    @Test
    void listagemNaoMostraOutraEmpresa() {
        service.definir(admin, TipoNotificacao.NOVO_TICKET, List.of(ana));
        service.definir(adminB, TipoNotificacao.NOVO_TICKET, List.of(bruno));
        assertEquals(List.of(ana), service.listar(admin).stream().map(ResponsavelNotificacaoResumo::atendenteId).toList());
        assertEquals(List.of(bruno), service.listar(adminB).stream().map(ResponsavelNotificacaoResumo::atendenteId).toList());
    }

    @Test
    void atendenteDesativadoSaiDosDestinatariosMasPermaneceNaConfiguracao() {
        service.definir(admin, TipoNotificacao.ATENDIMENTO_HUMANO_SOLICITADO, List.of(ana, carla));
        administracao.desativar(admin, ana);

        assertEquals(List.of(carla), service.destinatariosAtivos(empresaA, TipoNotificacao.ATENDIMENTO_HUMANO_SOLICITADO)
                .stream().map(ResponsavelNotificacaoResumo::atendenteId).toList());
        assertEquals(List.of(ana, carla), idsSalvos(TipoNotificacao.ATENDIMENTO_HUMANO_SOLICITADO));
    }

    @Test
    void entidadeRejeitaAtendenteInativo() {
        Atendente in = tx.execute(s -> em.find(Atendente.class, inativo));
        assertThrows(IllegalStateException.class,
                () -> new ResponsavelNotificacao(in, TipoNotificacao.NOVO_TICKET));
    }
}
