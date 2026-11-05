# Atendimento humano — terceira etapa

O serviço AtendimentoHumanoService coordena duas operações transacionais:

- `solicitar(conversaId, empresaId)`: passa de EM_ATENDIMENTO_AUTOMATICO para AGUARDANDO_ATENDENTE.
- `assumir(conversaId, atendenteId)`: valida o atendente cadastrado e ativo, consulta a conversa em sua empresa, atribui o responsável e passa para EM_ATENDIMENTO_HUMANO.

As regras existentes de Conversa são reutilizadas. Solicitações repetidas e tentativas de assumir em estados diferentes de AGUARDANDO_ATENDENTE são rejeitadas. Conversas encerradas não são reabertas. O retorno é AtendimentoHumanoResumo, sem expor a entidade JPA.

## Segurança e concorrência

A consulta usa empresa e bloqueio PESSIMISTIC_WRITE, sem joins opcionais. Dois atendentes não conseguem assumir a mesma conversa por este serviço: o segundo aguarda o commit e é rejeitado ao encontrar o estado já atualizado.

Os parâmetros são internos. A empresa e a conversa de `solicitar` devem vir do contexto validado da integração do canal ou de uma operação autorizada. Filtrar pela empresa não autentica o cliente nem prova que ele possui acesso à conversa. O atendente de `assumir` deve vir da identidade autenticada. Não há nova rota pública nem botão nesta etapa; a autenticação e o WhatsApp dependem das outras frentes.

O histórico é preservado. A conversa entra na fila existente ao solicitar e sai ao ser assumida. Tickets associados não são atribuídos ou alterados automaticamente.

## Respostas automáticas

A regra existente de Conversa já rejeita mensagens de CHATBOT quando o estado deixa de ser EM_ATENDIMENTO_AUTOMATICO, inclusive durante a espera pelo humano. Os testes verificam essa rejeição e a preservação do histórico.

Ainda não há integração LLM. Quando ela for implementada, deverá reconsultar e bloquear a conversa antes de persistir a resposta, sem manter transação aberta durante a chamada externa. Esta etapa não comprova ainda o tratamento de uma resposta do provedor que chega durante uma transferência.

## Demonstração

Na pasta chatbot:

```powershell
.\mvnw.cmd test -Dtest=AtendimentoHumanoServiceTests
```

Os testes cobrem persistência, entrada e saída da fila, preservação de histórico e ticket, estados inválidos, isolamento por empresa, atendente inativo/inexistente, IDs inválidos, rejeição de mensagens automáticas e disputa entre duas transações.

Banco dos testes: H2. A validação de concorrência em PostgreSQL/Neon permanece pendente. Não foram necessárias migrações, credenciais ou dependências novas.

Transferência nesta etapa significa chatbot → fila → atendente humano. Reatribuição entre dois atendentes e integração com LLM são trabalhos posteriores.
