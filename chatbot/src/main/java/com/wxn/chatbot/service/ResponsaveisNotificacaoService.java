package com.wxn.chatbot.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.ResponsavelNotificacaoResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.ResponsavelNotificacao;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;
import com.wxn.chatbot.repository.AtendenteRepository;
import com.wxn.chatbot.repository.ResponsavelNotificacaoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class ResponsaveisNotificacaoService {

    private static final String MENSAGEM_SEM_PERMISSAO = "Apenas um administrador ativo pode realizar esta operação.";

    private final ResponsavelNotificacaoRepository responsaveis;
    private final AtendenteRepository atendentes;
    private final EntityManager entityManager;

    public ResponsaveisNotificacaoService(ResponsavelNotificacaoRepository responsaveis,
            AtendenteRepository atendentes, EntityManager entityManager) {
        this.responsaveis = Objects.requireNonNull(responsaveis);
        this.atendentes = Objects.requireNonNull(atendentes);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Transactional
    public List<ResponsavelNotificacaoResumo> definir(Long solicitanteId, TipoNotificacao tipo,
            Collection<Long> atendenteIds) {
        Objects.requireNonNull(tipo, "O tipo de notificação é obrigatório.");
        Objects.requireNonNull(atendenteIds, "A lista de responsáveis é obrigatória.");
        Atendente administrador = carregarAdministrador(solicitanteId);
        Long empresaId = administrador.getEmpresa().getId();

        // Serializa alterações de configuração da mesma empresa.
        entityManager.find(Empresa.class, empresaId, LockModeType.PESSIMISTIC_WRITE);

        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : atendenteIds) {
            ids.add(Objects.requireNonNull(id, "O id do atendente é obrigatório."));
        }
        List<Atendente> encontrados = ids.isEmpty() ? List.of() : atendentes.findByEmpresaIdAndIdIn(empresaId, ids);
        if (encontrados.size() != ids.size()) {
            throw new NoSuchElementException("Um ou mais atendentes não foram encontrados na empresa.");
        }

        responsaveis.removerPorEmpresaETipo(empresaId, tipo);
        // Construtor rejeita atendente inativo; a transação inteira é desfeita nesse caso.
        responsaveis.saveAllAndFlush(encontrados.stream()
                .map(atendente -> new ResponsavelNotificacao(atendente, tipo)).toList());
        return listarPorTipo(empresaId, tipo);
    }

    @Transactional(readOnly = true)
    public List<ResponsavelNotificacaoResumo> listar(Long solicitanteId) {
        Long empresaId = carregarAdministrador(solicitanteId).getEmpresa().getId();
        return responsaveis.findByEmpresaIdOrderByTipoAscAtendenteIdAsc(empresaId).stream()
                .map(ResponsavelNotificacaoResumo::de).toList();
    }

    @Transactional(readOnly = true)
    public List<ResponsavelNotificacaoResumo> destinatariosAtivos(Long empresaId, TipoNotificacao tipo) {
        Objects.requireNonNull(empresaId, "A empresa é obrigatória.");
        Objects.requireNonNull(tipo, "O tipo de notificação é obrigatório.");
        return listarPorTipo(empresaId, tipo).stream().filter(ResponsavelNotificacaoResumo::atendenteAtivo).toList();
    }

    private List<ResponsavelNotificacaoResumo> listarPorTipo(Long empresaId, TipoNotificacao tipo) {
        return responsaveis.findByEmpresaIdAndTipoOrderByAtendenteIdAsc(empresaId, tipo).stream()
                .map(ResponsavelNotificacaoResumo::de).toList();
    }

    private Atendente carregarAdministrador(Long solicitanteId) {
        Objects.requireNonNull(solicitanteId, "O solicitante é obrigatório.");
        Atendente solicitante = atendentes.findById(solicitanteId)
                .orElseThrow(() -> new SecurityException(MENSAGEM_SEM_PERMISSAO));
        if (!solicitante.isAtivo() || !solicitante.possuiPerfil(PerfilAcesso.ADMINISTRADOR)) {
            throw new SecurityException(MENSAGEM_SEM_PERMISSAO);
        }
        return solicitante;
    }
}
