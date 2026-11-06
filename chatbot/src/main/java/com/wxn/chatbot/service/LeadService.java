package com.wxn.chatbot.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.LeadResumo;
import com.wxn.chatbot.dto.RegistrarLeadComando;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Lead;
import com.wxn.chatbot.repository.LeadRepository;

import jakarta.persistence.EntityManager;

/**
 * Registro e consulta local de leads (HU09.1), sempre no escopo de uma empresa.
 *
 * Regra de contatos repetidos: o mesmo contato pode registrar varios interesses.
 * So e duplicata o mesmo contato com a mesma necessidade (sem diferenca de caixa ou
 * espacos) na mesma empresa; a restricao unica no banco garante isso sob concorrencia.
 *
 * O empresaId deve vir do contexto autenticado quando ele existir; este servico nao
 * autentica nem integra com CRM.
 */
@Service
public class LeadService {

    private static final String MENSAGEM_DUPLICADO =
            "Já existe um lead com este contato e esta necessidade na empresa.";

    private final LeadRepository leadRepository;
    private final EntityManager entityManager;

    public LeadService(LeadRepository leadRepository, EntityManager entityManager) {
        this.leadRepository = Objects.requireNonNull(leadRepository, "O repositorio de leads e obrigatorio.");
        this.entityManager = Objects.requireNonNull(entityManager, "O EntityManager e obrigatorio.");
    }

    @Transactional
    public LeadResumo registrar(Long empresaId, RegistrarLeadComando comando) {
        Objects.requireNonNull(comando, "Os dados do lead são obrigatórios.");
        Empresa empresa = buscarEmpresa(empresaId);
        Lead lead = new Lead(empresa, comando.nome(), comando.contato(), comando.origem(),
                comando.necessidade());

        if (leadRepository.existsByEmpresaIdAndContatoAndNecessidadeChave(
                empresaId, lead.getContato(), Lead.chaveDaNecessidade(lead.getNecessidade()))) {
            throw new LeadDuplicadoException(MENSAGEM_DUPLICADO);
        }

        try {
            return LeadResumo.de(leadRepository.saveAndFlush(lead));
        } catch (DataIntegrityViolationException e) {
            throw new LeadDuplicadoException(MENSAGEM_DUPLICADO, e);
        }
    }

    @Transactional(readOnly = true)
    public List<LeadResumo> listar(Long empresaId) {
        buscarEmpresa(empresaId);
        return leadRepository.findByEmpresaIdOrderByDataCriacaoAscIdAsc(empresaId).stream()
                .map(LeadResumo::de).toList();
    }

    @Transactional(readOnly = true)
    public List<LeadResumo> buscarPorContato(Long empresaId, String contato) {
        buscarEmpresa(empresaId);
        return leadRepository.findByEmpresaIdAndContatoOrderByDataCriacaoAscIdAsc(
                empresaId, Lead.normalizarContato(contato)).stream()
                .map(LeadResumo::de).toList();
    }

    @Transactional(readOnly = true)
    public LeadResumo buscarPorId(Long empresaId, Long leadId) {
        buscarEmpresa(empresaId);
        return leadRepository.findByIdAndEmpresaId(
                Objects.requireNonNull(leadId, "O id do lead é obrigatório."), empresaId)
                .map(LeadResumo::de)
                .orElseThrow(() -> new NoSuchElementException("Lead não encontrado na empresa."));
    }

    private Empresa buscarEmpresa(Long empresaId) {
        Objects.requireNonNull(empresaId, "A empresa é obrigatória.");
        Empresa empresa = entityManager.find(Empresa.class, empresaId);
        if (empresa == null) {
            throw new NoSuchElementException("Empresa não encontrada.");
        }
        return empresa;
    }
}
