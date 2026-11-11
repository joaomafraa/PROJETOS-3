package com.wxn.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatbotApplicationTests {

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private ApplicationContext context;

	@Test
	void healthEndpointReportsUp() throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("http://localhost:" + port + "/actuator/health"))
					.timeout(Duration.ofSeconds(10))
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode());
			assertEquals("UP", JsonMapper.builder().build().readValue(response.body(), Map.class).get("status"));
		}
	}

	@Test
	void protectedApiControllersAreAvailableWithoutLocalPanelProfile() {
		assertEquals(1, context.getBeansOfType(
				com.wxn.chatbot.web.PainelHistoricoController.class).size());
		assertEquals(1, context.getBeansOfType(
				com.wxn.chatbot.web.PainelTicketsController.class).size());
	}

}
