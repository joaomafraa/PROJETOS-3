package com.wxn.chatbot.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.AtendenteResumo;
import com.wxn.chatbot.dto.CadastrarAtendenteComando;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.repository.AtendenteRepository;

@Service
public class AdministracaoAtendentesService {

    private static final String MENSAGEM_DUPLICADO = "Já existe um atendente com este e-mail na empresa.";
    private static final String MENSAGEM_NAO_ENCONTRADO = "Atendente não encontrado na empresa.";
    private static final String MENSAGEM_SEM_PERMISSAO = "Apenas um administrador ativo pode realizar esta operação.";

    private final AtendenteRepository atendentes;

    public AdministracaoAtendentesService(AtendenteRepository atendentes) {
        this.atendentes = Objects.requireNonNull(atendentes, "O repositório de atendentes é obrigatório.");
    }

    @Transactional
    public AtendenteResumo cadastrar(Long solicitanteId, CadastrarAtendenteComando comando) {
        Objects.requireNonNull(comando, "Os dados do atendente são obrigatórios.");
        Atendente administrador = carregarAdministrador(solicitanteId);
        Long empresaId = administrador.getEmpresa().getId();

        // Valida nome, e-mail e perfil antes de consumir um ID da sequência.
        Atendente provisorio = new Atendente(Long.MAX_VALUE, comando.nome(), comando.email(),
                administrador.getEmpresa(), comando.perfil());
        if (atendentes.existsByEmpresaIdAndEmail(empresaId, provisorio.getEmail())) {
            throw new AtendenteDuplicadoException(MENSAGEM_DUPLICADO);
        }

        Atendente novo = new Atendente(atendentes.proximoId(), provisorio.getNome(), provisorio.getEmail(),
                administrador.getEmpresa(), provisorio.getPerfil());
        try {
            // A restrição única no banco protege contra cadastros simultâneos.
            return AtendenteResumo.de(atendentes.saveAndFlush(novo));
        } catch (DataIntegrityViolationException e) {
            throw new AtendenteDuplicadoException(MENSAGEM_DUPLICADO, e);
        }
    }

    @Transactional(readOnly = true)
    public List<AtendenteResumo> listar(Long solicitanteId, Boolean ativo) {
        Long empresaId = carregarAdministrador(solicitanteId).getEmpresa().getId();
        List<Atendente> encontrados = ativo == null
                ? atendentes.findByEmpresaIdOrderByNomeAscIdAsc(empresaId)
                : atendentes.findByEmpresaIdAndAtivoOrderByNomeAscIdAsc(empresaId, ativo);
        return encontrados.stream().map(AtendenteResumo::de).toList();
    }

    @Transactional(readOnly = true)
    public AtendenteResumo buscar(Long solicitanteId, Long atendenteId) {
        Long empresaId = carregarAdministrador(solicitanteId).getEmpresa().getId();
        return atendentes.findByIdAndEmpresaId(validarId(atendenteId), empresaId)
                .map(AtendenteResumo::de)
                .orElseThrow(() -> new NoSuchElementException(MENSAGEM_NAO_ENCONTRADO));
    }

    @Transactional
    public AtendenteResumo alterarPerfil(Long solicitanteId, Long atendenteId, PerfilAcesso novoPerfil) {
        Objects.requireNonNull(novoPerfil, "O novo perfil de acesso é obrigatório.");
        Par par = travar(solicitanteId, atendenteId);
        par.alvo().alterarPerfil(novoPerfil, par.solicitante());
        return AtendenteResumo.de(par.alvo());
    }

    @Transactional
    public AtendenteResumo desativar(Long solicitanteId, Long atendenteId) {
        Par par = travar(solicitanteId, atendenteId);
        par.alvo().desativar(par.solicitante());
        return AtendenteResumo.de(par.alvo());
    }

    @Transactional
    public AtendenteResumo ativar(Long solicitanteId, Long atendenteId) {
        Par par = travar(solicitanteId, atendenteId);
        par.alvo().ativar(par.solicitante());
        return AtendenteResumo.de(par.alvo());
    }

    private Atendente carregarAdministrador(Long solicitanteId) {
        Atendente solicitante = atendentes.findById(validarId(solicitanteId))
                .orElseThrow(() -> new SecurityException(MENSAGEM_SEM_PERMISSAO));
        verificarAdministrador(solicitante);
        return solicitante;
    }

    private Par travar(Long solicitanteId, Long atendenteId) {
        validarId(solicitanteId);
        validarId(atendenteId);
        Atendente solicitante;
        Atendente alvo;
        if (solicitanteId.equals(atendenteId)) {
            solicitante = bloquear(solicitanteId, true);
            alvo = solicitante;
        } else if (solicitanteId < atendenteId) {
            solicitante = bloquear(solicitanteId, true);
            alvo = bloquear(atendenteId, false);
        } else {
            alvo = bloquear(atendenteId, false);
            solicitante = bloquear(solicitanteId, true);
        }
        verificarAdministrador(solicitante);
        if (!alvo.pertenceA(solicitante.getEmpresa())) {
            // Não revela a existência de atendentes de outra empresa.
            throw new NoSuchElementException(MENSAGEM_NAO_ENCONTRADO);
        }
        return new Par(solicitante, alvo);
    }

    private Atendente bloquear(Long id, boolean ehSolicitante) {
        return atendentes.buscarParaAtualizar(id).orElseThrow(() -> ehSolicitante
                ? new SecurityException(MENSAGEM_SEM_PERMISSAO)
                : new NoSuchElementException(MENSAGEM_NAO_ENCONTRADO));
    }

    private static void verificarAdministrador(Atendente solicitante) {
        if (!solicitante.isAtivo() || !solicitante.possuiPerfil(PerfilAcesso.ADMINISTRADOR)) {
            throw new SecurityException(MENSAGEM_SEM_PERMISSAO);
        }
    }

    private static Long validarId(Long id) {
        Objects.requireNonNull(id, "O id do atendente é obrigatório.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id do atendente deve ser positivo.");
        }
        return id;
    }

    private record Par(Atendente solicitante, Atendente alvo) {
    }
}
