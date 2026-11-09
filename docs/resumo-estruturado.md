# Resumo estruturado de conversa

`ResumoEstruturadoConversaService.gerar(conversaId, atendente)` consulta os dados persistidos da conversa pela empresa do atendente ativo. O chamador deve obter o atendente de um contexto confiável; o serviço não autentica usuários. Conversas inexistentes ou de outra empresa recebem o mesmo erro.

O DTO inclui o ID para consultar o histórico pelo `HistoricoConversasService`, status, data de início e dados disponíveis do cliente. Valores ausentes são `null` e têm seu nome listado em `dadosAusentes`; uma lista vazia de mensagens também é indicada nessa lista.

O campo `trechoInicialCliente` cita a primeira mensagem do cliente, se houver. É apenas uma possível pista sobre a demanda, não uma demanda confirmada ou interpretação. `mensagensRecentes` contém até as três últimas mensagens de qualquer remetente, na ordem persistida. Os dois campos podem citar a mesma mensagem. Cada trecho contém ID, tipo de remetente, data e até 200 caracteres Unicode; `truncado` indica quando o texto foi cortado. Para a conversa completa, consulte o serviço de histórico existente.

O serviço não usa LLM, não cria novas tabelas e não depende do painel. Para demonstrar, execute os testes de integração `ResumoEstruturadoConversaTests` com Java 21 e Maven Wrapper, a partir de `chatbot/`: `./mvnw -Dtest=ResumoEstruturadoConversaTests test` (Windows: `mvnw.cmd -Dtest=ResumoEstruturadoConversaTests test`). A integração com autenticação e interface depende das outras frentes.
