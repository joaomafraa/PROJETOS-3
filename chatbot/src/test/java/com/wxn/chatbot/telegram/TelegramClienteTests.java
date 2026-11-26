package com.wxn.chatbot.telegram;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramClienteTests {
    @Test void menuMostraComandosEContatoSemAlterarOTransporte() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.telegram.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.telegram.org/botfake/sendMessage"))
              .andExpect(content().json("{\"chat_id\":5,\"text\":\"Menu\",\"reply_markup\":{\"keyboard\":[[{\"text\":\"/suporte\"},{\"text\":\"/comercial\"}],[{\"text\":\"/atendente\"},{\"text\":\"/chamado\"}],[{\"text\":\"/solicitacoes\"}],[{\"text\":\"/validar\"},{\"text\":\"Compartilhar meu telefone\",\"request_contact\":true}]]}}"))
              .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        new TelegramCliente("fake", builder.build()).enviarMensagem(5, "Menu", TelegramCliente.Teclado.MENU);
        server.verify();
    }
    @Test void consultaAtualizacoesSemConfirmarOffset() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.telegram.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.telegram.org/botfake/getUpdates"))
              .andExpect(content().json("{\"offset\":12,\"timeout\":0,\"allowed_updates\":[\"message\"]}"))
              .andRespond(withSuccess("{\"ok\":true,\"result\":[{\"update_id\":12}]}", MediaType.APPLICATION_JSON));
        var cliente = new TelegramCliente("fake", builder.build());
        assertEquals(1, cliente.buscarAtualizacoes(12).size());
        server.verify();
    }
    @Test void rejeitaOffsetInvalidoEChaveAusente() {
        var cliente = new TelegramCliente("", RestClient.create());
        assertThrows(IllegalArgumentException.class, () -> cliente.buscarAtualizacoes(-1));
        assertThrows(IllegalStateException.class, () -> cliente.buscarAtualizacoes(0));
    }
    @Test void pedeTelefoneComBotaoDeContatoERemoveTeclado() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.telegram.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.telegram.org/botfake/sendMessage"))
              .andExpect(content().json("{\"chat_id\":5,\"text\":\"Oi\",\"reply_markup\":{\"keyboard\":[[{\"text\":\"Compartilhar meu telefone\",\"request_contact\":true}]],\"one_time_keyboard\":true}}"))
              .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/botfake/sendMessage"))
              .andExpect(content().json("{\"chat_id\":5,\"text\":\"Ok\",\"reply_markup\":{\"remove_keyboard\":true}}"))
              .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        var cliente = new TelegramCliente("fake", builder.build());
        cliente.enviarMensagem(5, "Oi", TelegramCliente.Teclado.PEDIR_TELEFONE);
        cliente.enviarMensagem(5, "Ok", TelegramCliente.Teclado.REMOVER);
        server.verify();
    }
}
