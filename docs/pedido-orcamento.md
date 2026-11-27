# Pedido de orçamento pelo Telegram (HU06.1)

Responsável: Guilherme Guimarães.

O cliente pede um orçamento pelo Telegram, informa a necessidade e a equipe comercial recebe o pedido com a necessidade e um contato para retorno. O pedido é uma solicitação real do tipo `ORCAMENTO`, com protocolo `PD-`, e aparece no painel de solicitações da empresa.

Este fluxo **só registra o pedido**. Não calcula preço, não promete proposta ou prazo e não cria lead.

## Como o cliente usa

Depois do cadastro, o cliente escolhe `/orcamento` no `/menu` (botão do teclado) e responde:

1. **Necessidade**: o que precisa orçar, de 10 a 2.000 caracteres. Também vale `/orcamento` seguido do texto na mesma mensagem; nesse caso o bot não pergunta de novo.
2. **Contato**: só é pedido quando o cadastro do cliente não tem telefone nem e-mail. Caso contrário o bot usa o que o cliente já informou no cadastro e não pergunta nada.
3. O bot informa o protocolo e deixa claro que **o registro não é uma proposta** e que valores e prazos virão da equipe.

`/cancelar` desiste em qualquer etapa e nenhuma solicitação é criada. Durante o pedido, outras opções de criação (`/chamado`, `/demonstracao`, `/reuniao`, `/visita`, `/orcamento`) respondem "Conclua o pedido de orçamento ou envie /cancelar". Os comandos de consulta (`/menu`, `/solicitacoes`, `/planos`, `/validar`, `/codigo`) e `/atendente` continuam funcionando.

Se outro pedido ou um chamado já estiver em andamento, `/orcamento` não inicia nada: quem responde é o fluxo ativo, que pede para concluir ou cancelar primeiro. O cadastro continua tendo prioridade: quem ainda não terminou o cadastro não consegue iniciar o orçamento.

## O que fica salvo

| Onde | O quê |
| --- | --- |
| `tickets` | O pedido: tipo `ORCAMENTO`, protocolo `PD-…`, necessidade na descrição, cliente, empresa e conversa de origem |
| `operacoes_solicitacao` | A chave `telegram:<atualizacaoId>`, para a repetição não criar outro pedido (tabela de Pedro) |
| `pedidos_orcamento` | O contato para retorno, no momento do pedido (migração `V28`) |
| `telegram_pedido_orcamento` | Etapa temporária (`NECESSIDADE` ou `CONTATO`); some quando o pedido é registrado ou cancelado |

O contato é gravado como texto rotulado: `Telefone: +5581…`, `E-mail: …` ou os dois (`Telefone: …; E-mail: …`) quando vem do cadastro. Se o cliente digitar um contato, ele é validado (e-mail ou telefone) e vale **apenas para este pedido**: o cadastro e a confirmação de identidade do cliente não mudam.

## Contrato para o código

```java
PedidoOrcamentoRegistrado pedido = pedidoOrcamentoService.registrar(
        empresaId, conversaId, necessidade,
        contatoInformado,              // null usa o contato do cadastro
        "telegram:" + atualizacaoId);

pedido.protocolo(); // PD-…, o mesmo mostrado no painel
pedido.contato();   // o contato guardado
pedido.novo();      // false = mesma operação reprocessada; não confirmar de novo
```

- `empresaId` vem do canal validado, nunca de texto do cliente. Conversa de outra empresa é recusada (`IllegalArgumentException`) e nada é salvo.
- O registro usa `AbrirChamadoService.registrar(..., ORCAMENTO, ...)`: mesmo gerador de protocolo, mesma chave de idempotência e mesmo evento `SolicitacaoRegistradaEvento` (uma vez por pedido criado), que alimenta as notificações de Márcio (HU05.3). Repetir a operação não publica de novo.
- `pedidoOrcamentoService.contatoCadastrado(empresaId, conversaId)` diz se o cliente já tem telefone ou e-mail no cadastro.
- **Uma nova solicitação do cliente deve usar uma chave nova.** No Telegram a chave é a atualização que concluiu o pedido.

## Atomicidade e repetição

`OrcamentoTelegramService` grava na mesma transação a etapa, o pedido, o contato, a resposta no histórico e a mensagem preparada na fila. Se o registro falhar, nada disso fica salvo: o cliente não recebe confirmação de um pedido inexistente e o `ProcessarMensagemTelegramService` devolve `FalhaEtapaTelegramException` na etapa `pedido_orcamento`, sem o texto do cliente. A mesma atualização repetida não cria outro pedido nem repete a confirmação. A entrega externa da confirmação segue o mecanismo existente (envio incerto vai para revisão, sem reenvio automático).

## Painel

Nos detalhes da solicitação aparece "Contato para retorno (orçamento)" (campo `contatoOrcamento` de `GET /api/tickets/{id}`), ao lado da necessidade. O contato só é lido junto com a empresa do atendente (`findByTicketIdAndEmpresaId`); pedido de outra empresa responde como "não encontrado". O filtro por tipo "Orçamento" já existia.

## Limites desta entrega

- **Sem lead.** Ligar o pedido a um lead (e a detecção de duplicidade de `LeadService`) depende de uma regra combinada com a equipe; por isso nada é criado em `leads`. A estrutura de contato e necessidade do lead é parecida, então a regra pode ser acrescentada depois sem mudar o fluxo do cliente.
- **Sem CRM externo, preço ou proposta.** É um registro para a equipe comercial.
- **Sem notificação própria.** A equipe é avisada pelo mecanismo de HU05.3 (notificações no painel), que já cobre os tipos de pedido.
- Duas mensagens diferentes pedindo o mesmo orçamento criam dois pedidos: só a **mesma atualização** do Telegram é tratada como repetição.
- A etapa temporária de um pedido interrompido por `/atendente` é descartada na próxima mensagem da conversa (quando ela já não está em atendimento automático).
- Os testes automáticos rodam em H2. Em 27/11/2026 a migração `V28` e os 44 testes do pedido de orçamento (inclusive as tentativas simultâneas) também passaram em um PostgreSQL 16.9 isolado e descartável, sem usar o banco compartilhado. Essa validação foi feita à parte, com um PostgreSQL embutido (`zonky embedded-postgres`) e a URL de teste trocada temporariamente; ela não faz parte do build.

## Testes

- `service/PedidoOrcamentoServiceTests`: registro e persistência, contato do cadastro, informado e ausente, validações, repetição, chave nova, chave reutilizada, tentativas simultâneas, falha depois do registro, outra empresa, lista e detalhes no painel e ausência de lead.
- `integration/PedidoOrcamentoTelegramTests`: fluxo completo e repetição da atualização, necessidade no próprio comando, cancelamento, necessidade curta ou longa, contato ausente, convivência com `/chamado` e pedidos de agendamento, conversa em atendimento humano, `/menu` e isolamento entre duas empresas.
- `integration/FalhaPedidoOrcamentoTelegramTests`: falha no registro não gera confirmação nem envio preparado.
- `integration/PedidoOrcamentoPainelApiTests`: `GET /api/tickets/{id}` devolve `contatoOrcamento` à empresa do pedido; atendente de outra empresa e acesso sem login não veem o pedido nem o contato.
- `integration/NotificacaoPedidoOrcamentoTests`: o pedido avisa só os responsáveis ativos da mesma empresa; repetir a operação ou um pedido inválido não gera novos avisos.
- `telegram/TelegramClienteTests`: botão `/orcamento` no teclado.

```powershell
$env:TELEGRAM_POLLING_ENABLED = 'false'
$env:TESTAR_GROQ_REAL = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=PedidoOrcamentoServiceTests,PedidoOrcamentoTelegramTests,FalhaPedidoOrcamentoTelegramTests' test
```

## Verificações do deploy

- Feito: `V28` está na `main` e é a única migração com esse número.
- Feito: migração validada em PostgreSQL isolado (ver "Limites desta entrega").
- Feito: o campo "Contato para retorno (orçamento)" foi conferido no painel local, com o contato do cadastro e com um e-mail informado pelo cliente.
- Pendente: após publicar, testar `/orcamento` no ambiente real autorizado: protocolo no painel, contato nos detalhes, cancelamento e repetição. Não ativar polling local com o mesmo token enquanto o Render estiver ativo.
