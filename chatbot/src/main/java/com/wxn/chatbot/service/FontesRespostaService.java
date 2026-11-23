package com.wxn.chatbot.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.wxn.chatbot.dto.FontesMensagem;
import com.wxn.chatbot.llm.FonteDisponivel;
import com.wxn.chatbot.llm.ReferenciasResposta;
import com.wxn.chatbot.llm.SegurancaContextoConhecimento;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.FonteResposta;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.repository.FonteRespostaRepository;

/**
 * Registro e consulta da origem das respostas automaticas (HU02.2).
 *
 * O registro acontece na mesma transacao que salva a resposta, para que uma
 * resposta descartada nao deixe fontes soltas e uma resposta salva nao fique
 * sem a sua origem.
 */
@Service
public class FontesRespostaService {
    public static final int MAXIMO_FONTES_POR_RESPOSTA = 20;

    private final FonteRespostaRepository fontes;
    private final ConversaRepository conversas;
    private final EntityManager entityManager;
    private final SegurancaContextoConhecimento segurancaConhecimento;

    public FontesRespostaService(FonteRespostaRepository fontes, ConversaRepository conversas,
            EntityManager entityManager, SegurancaContextoConhecimento segurancaConhecimento) {
        this.fontes = Objects.requireNonNull(fontes);
        this.conversas = Objects.requireNonNull(conversas);
        this.entityManager = Objects.requireNonNull(entityManager);
        this.segurancaConhecimento = Objects.requireNonNull(segurancaConhecimento);
    }

    /**
     * Registra os trechos entregues a IA e os que a resposta citou.
     * Deve ser chamado logo depois de {@code conversa.adicionarMensagem(...)}, na mesma transacao.
     * Toda citacao precisa estar entre os trechos fornecidos; nenhum id inventado e aceito.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Conversa conversa, Long mensagemId, List<FonteDisponivel> recuperadas,
            List<ReferenciasResposta.Citacao> citadas) {
        Objects.requireNonNull(conversa, "A conversa e obrigatoria.");
        if (mensagemId == null || mensagemId <= 0) {
            throw new IllegalArgumentException("O id da mensagem deve ser positivo.");
        }
        Mensagem mensagem = conversa.getMensagens().stream()
                .filter(item -> item.getId().equals(mensagemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mensagem nao encontrada nesta conversa."));

        Long empresaId = conversa.getEmpresa().getId();
        List<FonteDisponivel> fornecidas = segurancaConhecimento.validarPertencimento(empresaId, recuperadas);
        List<ReferenciasResposta.Citacao> referencias = citadas == null ? List.of() : List.copyOf(citadas);
        if (fornecidas.size() > MAXIMO_FONTES_POR_RESPOSTA) {
            throw new IllegalArgumentException(
                    "Uma resposta pode registrar no maximo " + MAXIMO_FONTES_POR_RESPOSTA + " trechos.");
        }
        Set<String> chavesFornecidas = new HashSet<>();
        fornecidas.forEach(fonte -> chavesFornecidas.add(fonte.chave()));
        Set<Integer> numeros = new HashSet<>();
        Set<String> chavesCitadas = new HashSet<>();
        for (ReferenciasResposta.Citacao citacao : referencias) {
            Objects.requireNonNull(citacao, "A citacao nao pode ser nula.");
            if (!chavesFornecidas.contains(citacao.fonte().chave())) {
                throw new IllegalArgumentException("A resposta citou um trecho que nao foi fornecido a IA.");
            }
            if (!numeros.add(citacao.numero()) || !chavesCitadas.add(citacao.fonte().chave())) {
                throw new IllegalArgumentException("Citacoes repetidas na mesma resposta.");
            }
        }
        if (fontes.existsByEmpresaIdAndConversaIdAndMensagemId(empresaId, conversa.getId(), mensagemId)) {
            throw new IllegalStateException("As fontes desta resposta ja foram registradas.");
        }

        // A mensagem precisa estar gravada antes das fontes (chave estrangeira).
        entityManager.flush();
        for (int posicao = 0; posicao < fornecidas.size(); posicao++) {
            fontes.save(FonteResposta.recuperada(conversa, mensagem, posicao, fornecidas.get(posicao)));
        }
        for (ReferenciasResposta.Citacao citacao : referencias) {
            fontes.save(FonteResposta.citada(conversa, mensagem, citacao.numero(), citacao.fonte()));
        }
    }

    /** Fontes de todas as respostas de uma conversa, para o atendente autorizado da mesma empresa. */
    @Transactional(readOnly = true)
    public List<FontesMensagem> listarPorConversa(Long conversaId, Atendente atendente) {
        Objects.requireNonNull(atendente, "O atendente e obrigatorio.");
        if (!atendente.isAtivo()) {
            throw new SecurityException("Um atendente inativo nao pode consultar fontes.");
        }
        if (conversaId == null || conversaId <= 0) {
            throw new IllegalArgumentException("O id da conversa deve ser positivo.");
        }
        Long empresaId = atendente.getEmpresa().getId();
        conversas.findByIdAndEmpresaId(conversaId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa nao encontrada nesta empresa."));

        Map<Long, List<FonteResposta>> porMensagem = new LinkedHashMap<>();
        for (FonteResposta fonte : fontes.findByEmpresaIdAndConversaIdOrderByMensagemIdAscUsoAscOrdemAsc(
                empresaId, conversaId)) {
            porMensagem.computeIfAbsent(fonte.getMensagemId(), id -> new java.util.ArrayList<>()).add(fonte);
        }
        return porMensagem.entrySet().stream()
                .map(item -> new FontesMensagem(item.getKey(),
                        filtrar(item.getValue(), FonteResposta.Uso.CITADA),
                        filtrar(item.getValue(), FonteResposta.Uso.RECUPERADA)))
                .toList();
    }

    /** Fontes citadas por uma resposta, na ordem dos numeros [1], [2]... usados no texto. */
    @Transactional(readOnly = true)
    public List<FontesMensagem.Fonte> citadas(Long empresaId, Long conversaId, Long mensagemId) {
        if (empresaId == null || conversaId == null || mensagemId == null) {
            throw new IllegalArgumentException("Empresa, conversa e mensagem sao obrigatorias.");
        }
        return fontes.findByEmpresaIdAndConversaIdAndMensagemIdAndUsoOrderByOrdemAsc(
                        empresaId, conversaId, mensagemId, FonteResposta.Uso.CITADA)
                .stream().map(FontesMensagem.Fonte::de).toList();
    }

    private static List<FontesMensagem.Fonte> filtrar(List<FonteResposta> lista, FonteResposta.Uso uso) {
        return lista.stream().filter(fonte -> fonte.getUso() == uso)
                .sorted(java.util.Comparator.comparingInt(FonteResposta::getOrdem))
                .map(FontesMensagem.Fonte::de).toList();
    }
}
