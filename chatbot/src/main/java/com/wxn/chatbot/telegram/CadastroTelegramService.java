package com.wxn.chatbot.telegram;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.ClienteIdentificado;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.IdentificacaoClienteService;
import com.wxn.chatbot.telegram.TelegramCliente.Teclado;

import jakarta.persistence.EntityManager;

/**
 * Identificacao (HU07.1) e cadastro minimo (HU07.2) no inicio do atendimento pelo Telegram.
 *
 * Contrato com o canal:
 * - empresa e chatbot vem da configuracao do bot (IdentificacaoTelegramService), nunca do chat;
 * - o telefone so e aceito quando o usuario compartilha o PROPRIO contato pelo botao do
 *   Telegram (MensagemTelegram.telefone). Numero digitado nao localiza nem cadastra ninguem;
 * - telefone reconhecido nao comprova identidade: dados restritos continuam exigindo /validar.
 *
 * Etapas (coluna telegram_contatos.etapa_cadastro):
 * AGUARDANDO_TELEFONE -> numero conhecido na empresa: liga o contato e a conversa ao cliente -> CONCLUIDO
 *                     -> numero desconhecido: guarda o numero e pede o nome -> AGUARDANDO_NOME
 * AGUARDANDO_NOME     -> nome valido -> AGUARDANDO_EMPRESA
 * AGUARDANDO_EMPRESA  -> empresa ou /pular -> cadastro pelo IdentificacaoClienteService -> CONCLUIDO
 *
 * A resposta de cada etapa e gravada no historico e preparada na fila de envio na mesma
 * transacao da mudanca de etapa: se o processamento for repetido, o ProcessarMensagemTelegramService
 * encontra o envio ja preparado e nao aplica a etapa duas vezes.
 */
@Service
public class CadastroTelegramService {

    public enum Etapa { AGUARDANDO_TELEFONE, AGUARDANDO_NOME, AGUARDANDO_EMPRESA, CONCLUIDO }

    /** Texto ja gravado no historico e preparado na fila; falta apenas enviar. */
    public record Resposta(String texto, Teclado teclado) { }

    static final String PEDIR_TELEFONE = "Olá! Para localizar seu cadastro, toque em \"" + TelegramCliente.BOTAO_TELEFONE
            + "\" abaixo. Usamos o número só para identificar seu atendimento.\n\n"
            + "Para falar com uma pessoa agora, use /atendente.";
    static final String TELEFONE_DIGITADO = "Por segurança, não usamos número digitado. Toque em \""
            + TelegramCliente.BOTAO_TELEFONE + "\" para enviar o número da sua conta do Telegram.";
    static final String CONTATO_DE_OUTRA_PESSOA = "Envie o seu próprio número pelo botão \""
            + TelegramCliente.BOTAO_TELEFONE + "\".";
    static final String CADASTRO_LOCALIZADO = "Localizei um cadastro associado a este número. "
            + "Escreva sua dúvida ou abra /menu para ver as opções. "
            + "Use /solicitacoes para consultar seus protocolos. Para consultar planos, valide sua identidade.";
    static final String PEDIR_NOME = "Ainda não encontrei seu cadastro. Como posso chamar você? Envie seu nome completo.";
    static final String NOME_INVALIDO = "Não entendi seu nome. Envie seu nome completo, por exemplo: Marina Costa.";
    static final String EMPRESA_INVALIDA = "Envie o nome da empresa que você representa (até 120 caracteres) ou /pular.";

    private static final int NOME_MAXIMO = 100;
    private static final int EMPRESA_MAXIMO = 120;

    private final EntityManager em;
    private final ConversaRepository conversas;
    private final IdentificacaoClienteService identificacao;
    private final FilaEnvioTelegramService fila;
    private final TransactionTemplate transacao;

    public CadastroTelegramService(EntityManager em, ConversaRepository conversas,
            IdentificacaoClienteService identificacao, FilaEnvioTelegramService fila,
            PlatformTransactionManager transactionManager) {
        this.em = em;
        this.conversas = conversas;
        this.identificacao = identificacao;
        this.fila = fila;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    /**
     * Trata a mensagem enquanto o contato nao concluiu a identificacao. Vazio quando o
     * contato ja esta identificado ou a conversa nao esta no atendimento automatico:
     * nesses casos o fluxo normal (IA ou atendente) continua.
     */
    public Optional<Resposta> atender(long empresaId, long conversaId, MensagemTelegram mensagem) {
        if (mensagem == null) throw new IllegalArgumentException("Mensagem obrigatória.");
        Etapa etapa = transacao.execute(s -> {
            Etapa atual = etapaAtual(empresaId, mensagem.usuarioId(), false);
            if (atual == Etapa.CONCLUIDO) return atual;
            boolean automatica = conversas.existsByIdAndEmpresaIdAndStatus(
                    conversaId, empresaId, Conversa.Status.EM_ATENDIMENTO_AUTOMATICO);
            return automatica ? atual : Etapa.CONCLUIDO;
        });
        if (etapa == Etapa.CONCLUIDO) return Optional.empty();

        // O cadastro pelo servico existente roda na propria transacao (trata duplicidade e
        // concorrencia). Se a etapa seguinte falhar, a repeticao devolve o mesmo cliente.
        ClienteIdentificado cadastrado = null;
        if (etapa == Etapa.AGUARDANDO_EMPRESA && respostaEmpresaValida(mensagem.texto())) {
            cadastrado = transacao.execute(s -> cadastrar(empresaId, mensagem.usuarioId()));
        }
        ClienteIdentificado finalizado = cadastrado;
        return Optional.ofNullable(transacao.execute(s -> aplicarEtapa(empresaId, conversaId, mensagem, finalizado)));
    }

    private Resposta aplicarEtapa(long empresaId, long conversaId, MensagemTelegram mensagem, ClienteIdentificado cadastrado) {
        Etapa etapa = etapaAtual(empresaId, mensagem.usuarioId(), true);
        if (etapa == Etapa.CONCLUIDO) return null;
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));
        if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) return null;

        Resposta resposta = switch (etapa) {
            case AGUARDANDO_TELEFONE -> receberTelefone(empresaId, conversa, mensagem);
            case AGUARDANDO_NOME -> receberNome(empresaId, conversa, mensagem);
            case AGUARDANDO_EMPRESA -> receberEmpresa(empresaId, conversa, mensagem, cadastrado);
            case CONCLUIDO -> null;
        };
        long proximoId = Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
        conversa.adicionarMensagem(proximoId, resposta.texto(), Mensagem.TipoRemetente.CHATBOT);
        em.flush();
        fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), resposta.texto());
        return resposta;
    }

    // HU07.1: so o contato da propria conta localiza um cadastro, e apenas dentro da empresa do bot.
    private Resposta receberTelefone(long empresaId, Conversa conversa, MensagemTelegram mensagem) {
        if (mensagem.telefone() == null) {
            if (mensagem.compartilhouContato()) return new Resposta(CONTATO_DE_OUTRA_PESSOA, Teclado.PEDIR_TELEFONE);
            if (pareceTelefone(mensagem.texto())) return new Resposta(TELEFONE_DIGITADO, Teclado.PEDIR_TELEFONE);
            return new Resposta(PEDIR_TELEFONE, Teclado.PEDIR_TELEFONE);
        }
        String telefone = Cliente.normalizarTelefone(mensagem.telefone());
        Optional<ClienteIdentificado> existente = identificacao.identificar(empresaId, telefone);
        if (existente.isPresent()) {
            vincular(empresaId, mensagem.usuarioId(), conversa, existente.get().id());
            return new Resposta(CADASTRO_LOCALIZADO, Teclado.REMOVER);
        }
        em.createNativeQuery("update telegram_contatos set telefone_informado = :telefone, etapa_cadastro = 'AGUARDANDO_NOME'"
                + " where empresa_id = :empresa and usuario_id = :usuario")
                .setParameter("telefone", telefone).setParameter("empresa", empresaId)
                .setParameter("usuario", mensagem.usuarioId()).executeUpdate();
        return new Resposta(PEDIR_NOME, Teclado.REMOVER);
    }

    // HU07.2: o nome digitado substitui o primeiro nome do perfil do Telegram no cliente provisorio.
    private Resposta receberNome(long empresaId, Conversa conversa, MensagemTelegram mensagem) {
        String nome = mensagem.texto().strip();
        if (mensagem.compartilhouContato() || !nomeValido(nome)) return new Resposta(NOME_INVALIDO, Teclado.NENHUM);
        conversa.getCliente().alterarNome(nome);
        atualizarEtapa(empresaId, mensagem.usuarioId(), Etapa.AGUARDANDO_EMPRESA);
        return new Resposta("Prazer, " + conversa.getCliente().getNome()
                + "! Se estiver falando em nome de uma empresa, informe o nome dela. Essa etapa é opcional: use /pular para continuar.", Teclado.NENHUM);
    }

    private Resposta receberEmpresa(long empresaId, Conversa conversa, MensagemTelegram mensagem, ClienteIdentificado cadastrado) {
        if (cadastrado == null) return new Resposta(EMPRESA_INVALIDA, Teclado.NENHUM);
        Cliente cliente = em.find(Cliente.class, cadastrado.id());
        String empresaInformada = mensagem.texto().strip();
        // Declaracao do interessado; nao cria vinculo com outro ambiente (Cliente.informarEmpresa).
        if (!empresaInformada.equalsIgnoreCase("/pular") && cliente.getEmpresaInformada() == null) {
            cliente.informarEmpresa(empresaInformada);
        }
        vincular(empresaId, mensagem.usuarioId(), conversa, cliente.getId());
        if (!cadastrado.cadastradoAgora()) return new Resposta(CADASTRO_LOCALIZADO, Teclado.NENHUM);
        return new Resposta("Obrigado, " + cliente.getNome() + "! Seu cadastro foi concluído.\n\n"
                + "Escreva sua dúvida ou escolha uma opção no teclado.", Teclado.MENU);
    }

    // Finaliza pelo servico existente: nome do cliente provisorio + telefone compartilhado.
    private ClienteIdentificado cadastrar(long empresaId, long usuarioId) {
        List<?> linhas = em.createNativeQuery("select c.telefone_informado, cl.nome from telegram_contatos c"
                + " join clientes cl on cl.id = c.cliente_id where c.empresa_id = :empresa and c.usuario_id = :usuario")
                .setParameter("empresa", empresaId).setParameter("usuario", usuarioId).getResultList();
        if (linhas.isEmpty()) throw new IllegalStateException("Contato do Telegram não encontrado.");
        Object[] linha = (Object[]) linhas.get(0);
        String telefone = (String) linha[0];
        String nome = (String) linha[1];
        if (telefone == null || nome == null) throw new IllegalStateException("Cadastro incompleto.");
        return identificacao.identificarOuCadastrar(empresaId, telefone, nome, null);
    }

    private void vincular(long empresaId, long usuarioId, Conversa conversa, Long clienteId) {
        Cliente cliente = em.find(Cliente.class, clienteId);
        conversa.identificarCliente(cliente);
        em.createNativeQuery("update telegram_contatos set cliente_id = :cliente, telefone_informado = null,"
                + " etapa_cadastro = 'CONCLUIDO' where empresa_id = :empresa and usuario_id = :usuario")
                .setParameter("cliente", clienteId).setParameter("empresa", empresaId)
                .setParameter("usuario", usuarioId).executeUpdate();
    }

    private Etapa etapaAtual(long empresaId, long usuarioId, boolean bloquear) {
        String sql = "select etapa_cadastro from telegram_contatos where empresa_id = :empresa and usuario_id = :usuario"
                + (bloquear ? " for update" : "");
        List<?> linhas = em.createNativeQuery(sql).setParameter("empresa", empresaId)
                .setParameter("usuario", usuarioId).getResultList();
        if (linhas.isEmpty()) throw new IllegalStateException("Contato do Telegram não encontrado.");
        return Etapa.valueOf((String) linhas.get(0));
    }

    private void atualizarEtapa(long empresaId, long usuarioId, Etapa etapa) {
        em.createNativeQuery("update telegram_contatos set etapa_cadastro = :etapa where empresa_id = :empresa and usuario_id = :usuario")
                .setParameter("etapa", etapa.name()).setParameter("empresa", empresaId)
                .setParameter("usuario", usuarioId).executeUpdate();
    }

    private static boolean pareceTelefone(String texto) {
        return texto != null && texto.strip().matches("\\+?[0-9 ()-]{7,20}");
    }

    private static boolean nomeValido(String nome) {
        return nome.length() >= 2 && nome.length() <= NOME_MAXIMO && !nome.startsWith("/") && !nome.contains("?")
                && nome.codePoints().anyMatch(Character::isLetter) && !nome.matches(".*[0-9@].*");
    }

    private static boolean respostaEmpresaValida(String texto) {
        if (texto == null) return false;
        String valor = texto.strip();
        if (valor.equalsIgnoreCase("/pular")) return true;
        return !valor.isEmpty() && valor.length() <= EMPRESA_MAXIMO && !valor.startsWith("/") && !valor.contains("?")
                && !valor.equals(MensagemTelegram.TEXTO_CONTATO);
    }
}
