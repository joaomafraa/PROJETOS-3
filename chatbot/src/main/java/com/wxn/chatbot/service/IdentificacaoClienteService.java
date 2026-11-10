package com.wxn.chatbot.service;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.ClienteIdentificado;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.repository.ClienteRepository;

import jakarta.persistence.EntityManager;

/**
 * Identificacao do cliente pelo telefone (HU07.1) e cadastro minimo quando o numero
 * nao e reconhecido (HU07.2), sempre no escopo de uma empresa.
 *
 * Regras:
 * - O telefone e normalizado pela mesma regra da entidade Cliente antes de buscar ou gravar.
 * - O mesmo telefone pode existir em empresas diferentes, mas nao duas vezes na mesma.
 *   A restricao unica uk_clientes_empresa_telefone garante isso tambem sob concorrencia;
 *   a consulta previa so serve para devolver um erro claro no caso comum.
 * - O empresaId deve vir do contexto confiavel (numero do canal ou atendente autenticado),
 *   nunca de um texto digitado pelo cliente. Este servico so confere se a empresa existe.
 * - Telefone reconhecido nao comprova identidade: o cliente cadastrado comeca com
 *   identidadeConfirmada = false e o resultado nao expoe o e-mail.
 * - Nome e telefone sao obrigatorios no cadastro; o e-mail e opcional.
 */
@Service
public class IdentificacaoClienteService {

    // Ja existe cliente com este telefone na mesma empresa.
    public static class ClienteDuplicadoException extends RuntimeException {
        public ClienteDuplicadoException(Throwable causa) {
            super("Já existe um cliente com este telefone na empresa.", causa);
        }
    }

    private static final String RESTRICAO_TELEFONE = "uk_clientes_empresa_telefone";

    private final ClienteRepository clientes;
    private final EntityManager entityManager;
    private final TransactionTemplate transacao;

    public IdentificacaoClienteService(ClienteRepository clientes, EntityManager entityManager,
            PlatformTransactionManager transactionManager) {
        this.clientes = clientes;
        this.entityManager = entityManager;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    // HU07.1: localiza o cliente da empresa pelo telefone. Vazio quando nao reconhecido.
    @Transactional(readOnly = true)
    public Optional<ClienteIdentificado> identificar(Long empresaId, String telefone) {
        return buscar(empresaId, telefone);
    }

    // HU07.2: cadastra um cliente novo. Lanca ClienteDuplicadoException se o telefone ja existir na empresa.
    @Transactional
    public ClienteIdentificado cadastrar(Long empresaId, String telefone, String nome, String email) {
        return inserir(empresaId, telefone, nome, email);
    }

    /**
     * Fluxo de entrada do canal: devolve o cliente existente ou cadastra com os dados minimos.
     * Cada etapa roda na propria transacao. Se outro cadastro do mesmo telefone vencer a
     * disputa, a restricao do banco rejeita este e o cliente vencedor e devolvido.
     */
    public ClienteIdentificado identificarOuCadastrar(Long empresaId, String telefone, String nome, String email) {
        Optional<ClienteIdentificado> existente = transacao.execute(s -> buscar(empresaId, telefone));
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            return transacao.execute(s -> inserir(empresaId, telefone, nome, email));
        } catch (ClienteDuplicadoException e) {
            return transacao.execute(s -> buscar(empresaId, telefone)).orElseThrow(() -> e);
        }
    }

    private Optional<ClienteIdentificado> buscar(Long empresaId, String telefone) {
        buscarEmpresa(empresaId);
        return clientes.findByEmpresaIdAndNumeroTelefone(empresaId, Cliente.normalizarTelefone(telefone))
                .map(cliente -> ClienteIdentificado.de(cliente, false));
    }

    private ClienteIdentificado inserir(Long empresaId, String telefone, String nome, String email) {
        Empresa empresa = buscarEmpresa(empresaId);
        String telefoneNormalizado = Cliente.normalizarTelefone(telefone);

        if (clientes.existsByEmpresaIdAndNumeroTelefone(empresaId, telefoneNormalizado)) {
            throw new ClienteDuplicadoException(null);
        }

        Cliente cliente = new Cliente(gerarId(), telefoneNormalizado);
        cliente.alterarNome(nome);
        if (email != null && !email.isBlank()) {
            cliente.atualizarEmail(email);
        }
        cliente.vincularEmpresa(empresa);

        try {
            return ClienteIdentificado.de(clientes.saveAndFlush(cliente), true);
        } catch (DataIntegrityViolationException e) {
            String mensagem = e.getMostSpecificCause().getMessage();
            if (mensagem != null && mensagem.toLowerCase(Locale.ROOT).contains(RESTRICAO_TELEFONE)) {
                throw new ClienteDuplicadoException(e);
            }
            throw e;
        }
    }

    private Empresa buscarEmpresa(Long empresaId) {
        if (empresaId == null || empresaId <= 0) {
            throw new IllegalArgumentException("O id da empresa deve ser positivo.");
        }
        Empresa empresa = entityManager.find(Empresa.class, empresaId);
        if (empresa == null) {
            throw new NoSuchElementException("Empresa não encontrada.");
        }
        return empresa;
    }

    // Clientes antigos tem IDs manuais; pula valores da sequencia que ja estejam em uso.
    private Long gerarId() {
        while (true) {
            Long id = clientes.proximoId();
            if (!clientes.existsById(id)) {
                return id;
            }
        }
    }
}
