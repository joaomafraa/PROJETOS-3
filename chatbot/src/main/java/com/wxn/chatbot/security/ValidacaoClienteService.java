package com.wxn.chatbot.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.ConversaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Uso interno: a empresa e a conversa devem vir de um canal autenticado. */
@Service
public class ValidacaoClienteService {
    private static final Duration VALIDADE = Duration.ofMinutes(10);
    private static final int LIMITE = 5;
    private final SecureRandom aleatorio = new SecureRandom();
    private final EntityManager em;
    private final ConversaRepository conversas;
    private final PasswordEncoder encoder;
    private final EnvioCodigoCliente envio;

    public ValidacaoClienteService(EntityManager em, ConversaRepository conversas,
            PasswordEncoder encoder, EnvioCodigoCliente envio) {
        this.em = em;
        this.conversas = conversas;
        this.encoder = encoder;
        this.envio = envio;
    }

    @Transactional
    public void solicitar(long empresaId, long conversaId) {
        Conversa conversa = conversa(empresaId, conversaId);
        String email = emailConfiavel(conversa);
        Instant agora = Instant.now();
        DesafioCliente desafio = em.find(DesafioCliente.class, conversaId);
        if (desafio != null && agora.isBefore(desafio.expiraEm)) {
            throw new IllegalStateException("Aguarde o prazo do codigo anterior.");
        }
        String codigo = String.format(Locale.ROOT, "%06d", aleatorio.nextInt(1_000_000));
        boolean novo = desafio == null;
        if (desafio == null) {
            desafio = new DesafioCliente(conversaId);
        }
        desafio.empresaId = empresaId;
        desafio.clienteId = conversa.getCliente().getId();
        desafio.email = email;
        desafio.codigoHash = encoder.encode(codigo);
        desafio.expiraEm = agora.plus(VALIDADE);
        desafio.tentativas = 0;
        desafio.usado = false;
        desafio.validadoAte = null;
        if (novo) em.persist(desafio);
        envio.enviar(email, codigo);
    }

    /** Retorna falso sem reverter a transacao, preservando tentativas invalidas. */
    @Transactional
    public boolean confirmar(long empresaId, long conversaId, String codigo) {
        Conversa conversa = conversa(empresaId, conversaId);
        DesafioCliente desafio = em.find(DesafioCliente.class, conversaId);
        Instant agora = Instant.now();
        if (!validoPara(desafio, conversa) || desafio.usado || desafio.tentativas >= LIMITE
                || !agora.isBefore(desafio.expiraEm)) return false;
        desafio.tentativas++;
        if (codigo == null || !codigo.matches("[0-9]{6}") || !encoder.matches(codigo, desafio.codigoHash)) return false;
        desafio.usado = true;
        desafio.validadoAte = agora.plus(VALIDADE);
        return true;
    }

    public record Solicitacao(String protocolo, Ticket.TipoSolicitacao tipo, Ticket.StatusTicket status) {}

    @Transactional
    public List<Solicitacao> consultarSolicitacoes(long empresaId, long conversaId) {
        Conversa conversa = conversa(empresaId, conversaId);
        DesafioCliente desafio = em.find(DesafioCliente.class, conversaId);
        if (!validoPara(desafio, conversa) || !desafio.usado || desafio.validadoAte == null
                || !Instant.now().isBefore(desafio.validadoAte)) {
            throw new SecurityException("Validacao de identidade necessaria.");
        }
        return em.createQuery("select t from Ticket t where t.empresa.id = :empresa and t.cliente.id = :cliente order by t.dataAbertura desc, t.id desc", Ticket.class)
                .setParameter("empresa", empresaId).setParameter("cliente", conversa.getCliente().getId())
                .setMaxResults(20).getResultList().stream()
                .map(t -> new Solicitacao(t.getProtocolo(), t.getTipo(), t.getStatus())).toList();
    }

    private Conversa conversa(long empresaId, long conversaId) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId)
                .orElseThrow(() -> new SecurityException("Validacao indisponivel."));
        if (conversa.getStatus() == Conversa.Status.ENCERRADA || conversa.getCliente().getEmpresa() == null
                || !conversa.getCliente().getEmpresa().getId().equals(empresaId)) {
            throw new SecurityException("Validacao indisponivel.");
        }
        return conversa;
    }

    private boolean validoPara(DesafioCliente desafio, Conversa conversa) {
        if (desafio == null || !desafio.clienteId.equals(conversa.getCliente().getId())
                || !desafio.empresaId.equals(conversa.getEmpresa().getId())) return false;
        try { return desafio.email.equals(emailConfiavel(conversa)); }
        catch (SecurityException e) { return false; }
    }

    private String emailConfiavel(Conversa conversa) {
        String email = conversa.getCliente().getEmail();
        if (email == null) throw new SecurityException("Validacao indisponivel.");
        var registros = em.createNativeQuery("select email from emails_confiaveis_cliente where cliente_id=:cliente and empresa_id=:empresa and email=:email")
                .setParameter("cliente", conversa.getCliente().getId())
                .setParameter("empresa", conversa.getEmpresa().getId()).setParameter("email", email).getResultList();
        if (registros.size() != 1) throw new SecurityException("Validacao indisponivel.");
        return email;
    }
}
