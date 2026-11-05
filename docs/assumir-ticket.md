# Assumir ticket — primeira etapa

O serviço `AssumirTicketService.assumir(ticketId, atendenteId)` busca o atendente no banco, valida sua atividade e consulta o ticket pela empresa desse atendente. A operação atribui o responsável e passa de `PENDENTE` para `EM_ATENDIMENTO`, reutilizando os métodos da entidade Ticket.

## Regras

- IDs devem ser positivos e os registros devem existir.
- O atendente precisa estar ativo e pertencer à empresa do ticket.
- Ticket inexistente ou de outra empresa produz a mesma mensagem de não encontrado.
- Ticket em atendimento ou concluído não pode ser assumido novamente, inclusive pelo mesmo atendente.
- Um ticket pendente já atribuído só pode ser iniciado pelo responsável atual. Transferir responsáveis será uma operação separada.
- Assumir o ticket não altera o estado da conversa de origem.

## Transação e concorrência

A consulta `TicketRepository.buscarParaAtualizar` utiliza `PESSIMISTIC_WRITE`. O bloqueio permanece até a transação terminar. Se dois atendentes disputarem o mesmo ticket, o segundo aguarda e verifica o estado atualizado após o primeiro commit, sendo rejeitado se o ticket já estiver em atendimento.

A consulta evita carregar associações com joins opcionais no comando de bloqueio. Não foi necessária migração nem dependência nova. A entidade gerenciada é persistida automaticamente no commit. O retorno é o DTO `TicketAssumido`, sem expor entidades JPA.

## Integração com as outras frentes

O ID do atendente é um parâmetro interno: deve ser fornecido pelo contexto autenticado da frente de Gabriel Malta. Não deve vir livremente do corpo ou dos parâmetros de uma requisição pública. Este serviço não implementa login nem oferece uma rota HTTP.

A busca do atendente usa EntityManager, seguindo o acesso JPA já existente no projeto, enquanto Márcio desenvolve AtendenteRepository. A consulta de bloqueio foi adicionada ao TicketRepository sem mudar as consultas existentes de Pedro e Hélio.

## Como demonstrar

Na pasta `chatbot`, execute no PowerShell:

```powershell
.\mvnw.cmd test -Dtest=AssumirTicketServiceTests
```

Os testes criam dados próprios em H2 e verificam persistência após commit, isolamento entre empresas, atendente inativo, registros e IDs inválidos, estados inválidos, atribuição prévia e disputa entre duas transações independentes.

O teste concorrente mantém a primeira transação aberta e verifica que a segunda aguarda; depois confirma que somente o primeiro responsável foi salvo. Essa validação em H2 não substitui a verificação posterior no PostgreSQL/Neon.

## Próximas etapas

A conclusão já está disponível no serviço `ConcluirTicketService`, descrito em [concluir-ticket.md](concluir-ticket.md). Transferência da conversa e LLM continuam pendentes. Os botões da interface dependem da integração com API e autenticação.
