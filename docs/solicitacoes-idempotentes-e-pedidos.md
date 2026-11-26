# Chamados idempotentes e pedidos de agendamento (HU05.1, HU05.2 e HU06.2)

Responsável: Pedro Martins — negócio e dados.

## O que muda

1. **Chamados sem duplicata (HU05.1/HU05.2).** `AbrirChamadoService` passou a aceitar uma **chave da operação**. Repetir a mesma chave devolve o mesmo chamado e o mesmo protocolo, sem criar outro nem avisar os responsáveis de novo.
2. **Pedidos de demonstração, reunião e visita técnica (HU06.2).** O novo `PedidoAgendamentoService` registra o pedido com a necessidade e a **preferência de horário** do cliente. A preferência é só uma preferência: o horário ainda depende da equipe.
3. **Painel.** Os detalhes da solicitação mostram "Preferência de horário (a confirmar)" quando o pedido tiver uma.

Migração: `V26__operacoes_e_pedidos_agendamento.sql`, com duas tabelas novas. Nenhuma tabela existente é alterada e nenhum dado antigo muda.

## Contrato

### Chave da operação

- Formato combinado para o Telegram: `"telegram:" + mensagem.atualizacaoId()`.
- A chave vale por empresa: a mesma chave em outra empresa é outra operação.
- A mesma chave usada para outra conversa ou outro tipo de solicitação é recusada (`IllegalStateException`).
- Até 120 caracteres; chave vazia é recusada.
- **Uma nova solicitação do cliente deve usar uma chave nova** (por exemplo, a atualização em que ele enviou a nova descrição). Assim um pedido intencionalmente novo gera outra solicitação.

### Chamado de suporte — João

```java
TicketAberto chamado = abrirChamadoService.abrir(empresaId, conversaId, descricao, "telegram:" + atualizacaoId);
chamado.protocolo(); // o mesmo mostrado no painel
```

O `/chamado` do Telegram (`AberturaChamadoTelegramService`) já usa essa versão. A versão sem chave, `abrir(empresaId, conversaId, descricao)`, continua existindo e sempre cria um chamado novo.

### Pedido de agendamento — Gabriel Mendes (confirmação, HU06.3)

```java
PedidoAgendamentoRegistrado pedido = pedidoAgendamentoService.registrar(
        empresaId, conversaId, Ticket.TipoSolicitacao.DEMONSTRACAO, // ou REUNIAO, VISITA_TECNICA
        necessidade, preferenciaHorario, "telegram:" + atualizacaoId);

pedido.protocolo();          // protocolo real, prefixo PD-
pedido.tipo();               // DEMONSTRACAO, REUNIAO ou VISITA_TECNICA
pedido.preferenciaHorario(); // o que o cliente pediu; NAO e horario confirmado
pedido.horarioAConfirmar();  // sempre true nesta entrega
pedido.novo();               // false = reprocessamento da mesma operacao: nao enviar a confirmacao de novo
```

- O resultado só existe depois do registro no banco. Se der erro, nenhum pedido foi salvo e não deve haver confirmação.
- Validações: necessidade de 10 a 2.000 caracteres; preferência de 3 a 300 caracteres (espaços extras são removidos); tipo apenas DEMONSTRACAO, REUNIAO ou VISITA_TECNICA.

### Orçamento — Guilherme (HU06.1)

Se quiser o mesmo protocolo e a mesma proteção contra repetição, use:

```java
AbrirChamadoService.Registro registro = abrirChamadoService.registrar(
        empresaId, conversaId, Ticket.TipoSolicitacao.ORCAMENTO, descricao, chaveOperacao);
registro.ticket(); // solicitação salva (protocolo PD-)
registro.novo();   // false quando a operação foi repetida
```

### Notificações — Márcio (HU05.3)

O `SolicitacaoRegistradaEvento` é publicado **uma vez por solicitação criada**, tanto para chamados quanto para pedidos. Uma operação repetida não publica de novo.

### Protocolos

O gerador é o mesmo de antes (mesma sequência e unicidade): `CH-` para chamados de suporte e `PD-` para pedidos (orçamento, demonstração, reunião e visita).

## Segurança — Hélio

- A empresa vem do canal validado. A conversa precisa ser da empresa informada, com cliente, chatbot e responsável da mesma empresa (regras existentes).
- Conversa de outra empresa: `IllegalArgumentException`, nada é salvo.
- A preferência só é lida junto com a empresa (`findByTicketIdAndEmpresaId`). Os detalhes no painel continuam restritos à empresa do atendente.

## Concorrência

Duas transações ao mesmo tempo com a mesma chave não criam duas solicitações: a chave primária `(empresa_id, chave_operacao)` barra a segunda, que é desfeita por inteiro (chamado incluído). Reprocessar depois devolve o chamado que ficou salvo. Isso foi testado com H2; o comportamento esperado no PostgreSQL é o mesmo, mas não foi testado em PostgreSQL isolado.

## Testes

`SolicitacoesIdempotentesTests` (H2, transações reais):

- repetição com a mesma chave, chave nova, chave reutilizada em outra conversa ou tipo, mesma chave em outra empresa;
- conversa de outra empresa, chave inválida, compatibilidade sem chave;
- duas tentativas simultâneas;
- os três tipos de pedido, pedido repetido, preferência nos detalhes só para a mesma empresa;
- dados inválidos não criam pedido nem avisam ninguém.
