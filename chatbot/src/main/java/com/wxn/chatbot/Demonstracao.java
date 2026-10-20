package com.wxn.chatbot;

public class Demonstracao {

    public static void main(String[] args) {
        Empresa empresa = new Empresa(1L, "WXN");
        Cliente cliente = new Cliente(1L, "++55 81 4002-8922");
        cliente.alterarNome("Marina");
        cliente.atualizarEmail("marina@example.com");
        cliente.informarEmpresa("Empresa Exemplo");
        Chatbot chatbot = new Chatbot(1L, "Assistente WXN", empresa, "Como posso ajudar?");
        Atendente atendente = new Atendente(1L, "Ana", "ana@example.com",
                empresa, Atendente.PerfilAcesso.ATENDENTE);

        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);
        conversa.adicionarMensagem(1L, "Gostaria de uma demonstracao.", Mensagem.TipoRemetente.CLIENTE);
        conversa.adicionarMensagem(2L, chatbot.getMensagemBoasVindas(), Mensagem.TipoRemetente.CHATBOT);
        conversa.solicitarAtendimentoHumano();
        conversa.iniciarAtendimentoHumano(atendente);
        conversa.adicionarMensagem(3L, "Vou agendar sua demonstracao.",Mensagem.TipoRemetente.ATENDENTE, atendente);

        Ticket ticket = new Ticket(1L, "WXN-001", "Agendar demonstracao",Ticket.TipoSolicitacao.DEMONSTRACAO, conversa);
        try {
            ticket.iniciarAtendimento();
        } catch (IllegalStateException e) {
            System.out.println("Operacao recusada: " + e.getMessage());
        }
        ticket.atribuirResponsavel(atendente);
        ticket.iniciarAtendimento();
        ticket.concluir();

        System.out.println("Ambiente: " + conversa.getEmpresa().getNome());
        System.out.println("Empresa informada: " + cliente.getEmpresaInformada());
        System.out.println("Conversa: " + conversa.getStatus());
        System.out.println("Responsavel pela conversa: " + conversa.getResponsavel().getNome());
        System.out.println("Ticket " + ticket.getProtocolo() + ": " + ticket.getStatus());
        System.out.println("Responsavel pelo ticket: " + ticket.getResponsavel().getNome());
        for (Mensagem mensagem : conversa.getMensagens()) {
            String autor;

            if (mensagem.getAtendente() != null) {
                autor = mensagem.getAtendente().getNome();
            } else {
                autor = mensagem.getRemetente().name();
            }

            System.out.println(autor + ": " + mensagem.getConteudo());
        }
    }
}
