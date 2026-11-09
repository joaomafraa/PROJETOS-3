package com.wxn.chatbot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.ResumoEstruturadoConversa;
import com.wxn.chatbot.dto.ResumoEstruturadoConversa.ClienteDados;
import com.wxn.chatbot.dto.ResumoEstruturadoConversa.TrechoMensagem;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;

@Service
public class ResumoEstruturadoConversaService {
    private static final int MAX_MENSAGENS_RECENTES = 3;
    private static final int MAX_CARACTERES = 200;
    private final ConversaRepository conversas;

    public ResumoEstruturadoConversaService(ConversaRepository conversas) {
        this.conversas = Objects.requireNonNull(conversas);
    }

    /**
     * Usa a primeira mensagem do cliente como citacao inicial, nao como demanda confirmada,
     * e as tres ultimas mensagens de qualquer remetente, na ordem persistida. Cada trecho
     * tem no maximo 200 pontos de codigo; o historico completo permanece no servico existente.
     */
    @Transactional(readOnly = true)
    public ResumoEstruturadoConversa gerar(Long conversaId, Atendente atendente) {
        Objects.requireNonNull(atendente, "O atendente e obrigatorio.");
        if (!atendente.isAtivo()) {
            throw new SecurityException("Um atendente inativo nao pode visualizar o resumo.");
        }
        if (conversaId == null || conversaId <= 0) {
            throw new IllegalArgumentException("O id da conversa deve ser positivo.");
        }
        Conversa conversa = conversas.findByIdAndEmpresaId(conversaId, atendente.getEmpresa().getId())
                .orElseThrow(() -> new IllegalArgumentException("Conversa nao encontrada nesta empresa."));
        Cliente cliente = conversa.getCliente();
        List<Mensagem> mensagens = conversa.getMensagens();
        TrechoMensagem inicial = mensagens.stream()
                .filter(m -> m.getRemetente() == Mensagem.TipoRemetente.CLIENTE)
                .findFirst().map(this::trecho).orElse(null);
        List<TrechoMensagem> recentes = mensagens.stream()
                .skip(Math.max(0, mensagens.size() - MAX_MENSAGENS_RECENTES))
                .map(this::trecho).toList();

        List<String> ausentes = new ArrayList<>();
        String nome = disponivel(cliente.getNome(), "cliente.nome", ausentes);
        String telefone = disponivel(cliente.getNumeroTelefone(), "cliente.telefone", ausentes);
        String email = disponivel(cliente.getEmail(), "cliente.email", ausentes);
        if (inicial == null) ausentes.add("trechoInicialCliente");
        if (recentes.isEmpty()) ausentes.add("mensagensRecentes");
        return new ResumoEstruturadoConversa(conversa.getId(), conversa.getStatus(), conversa.getDataInicio(),
                new ClienteDados(cliente.getId(), nome, telefone, email), inicial, recentes, ausentes);
    }

    private String disponivel(String valor, String campo, List<String> ausentes) {
        if (valor == null || valor.isBlank()) {
            ausentes.add(campo);
            return null;
        }
        return valor;
    }

    private TrechoMensagem trecho(Mensagem mensagem) {
        String conteudo = mensagem.getConteudo();
        boolean truncado = conteudo.codePointCount(0, conteudo.length()) > MAX_CARACTERES;
        String texto = truncado ? conteudo.substring(0, conteudo.offsetByCodePoints(0, MAX_CARACTERES)) : conteudo;
        return new TrechoMensagem(mensagem.getId(), mensagem.getRemetente(), mensagem.getDataEnvio(), texto, truncado);
    }
}
