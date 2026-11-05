# Concluir ticket — segunda etapa

`ConcluirTicketService.concluir(ticketId, atendenteId)` conclui um ticket em atendimento e retorna `TicketConcluido`, com ID, protocolo, status, responsável e data de conclusão.

## Regras

- O atendente deve existir, estar ativo e pertencer à empresa do ticket.
- Apenas o responsável atual pode concluir. Ser administrador não concede uma exceção automática.
- O estado deve ser EM_ATENDIMENTO. Tickets pendentes ou já concluídos são rejeitados.
- A conclusão reutiliza Ticket.concluir(), preserva o responsável e grava a data.
- A conversa de origem não é encerrada automaticamente.
- Repetir a operação é rejeitado, sem mudar a data da primeira conclusão.

## Persistência e integração

A operação é transacional e usa o bloqueio PESSIMISTIC_WRITE de `TicketRepository.buscarParaAtualizar`, compartilhado com o serviço de assumir ticket. A antiga consulta `buscarParaAssumir` recebeu esse nome mais geral; consumidores externos dessa assinatura devem atualizar suas chamadas.

Não foram adicionadas migrações ou configurações. O ID do atendente deve vir do contexto autenticado quando a frente de autenticação for integrada. Não existe nova rota pública nem botão nesta etapa.

## Demonstração

Na pasta chatbot, execute:

```powershell
.\mvnw.cmd test -Dtest=AssumirTicketServiceTests
```

A classe cobre o ciclo de assumir e concluir: persistência após commit, data de conclusão, estados inválidos, IDs inválidos, isolamento por empresa, responsável incorreto, administrador sem atribuição, atendente inativo e conclusões concorrentes.

Os testes usam H2; validar também no PostgreSQL antes de considerar comprovado o comportamento de concorrência no Neon.

Próxima parte: solicitar e assumir atendimento humano na conversa. A integração com LLM vem depois.
