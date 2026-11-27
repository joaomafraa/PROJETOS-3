# Confirmação de pedidos no Telegram (HU06.3)

Após o cadastro, o cliente escolhe `/demonstracao`, `/reuniao` ou `/visita` no `/menu`, informa sua necessidade e uma preferência de dia e horário. Antes do registro pode enviar `/cancelar`. O retorno informa o protocolo real e esclarece que a data e o horário dependem da confirmação da equipe; não é um agendamento confirmado.

O fluxo usa `PedidoAgendamentoService` e a chave `telegram:<atualizacaoId>`. A etapa, o pedido e a confirmação preparada na fila são gravados transacionalmente; falha no registro não confirma o pedido. Repetição da atualização segue os controles de idempotência existentes. A entrega externa pode ficar incerta e continua sujeita ao mecanismo de revisão; não é reenviada automaticamente.

O estado temporário do fluxo usa a migração V27. A persistência das solicitações usa a V26. O menu e as etapas preservam a abertura de chamado existente; comandos de outro pedido exigem cancelar o atual. O pedido não substitui a solicitação de atendimento humano.

Os testes simulam o Telegram e usam banco H2. Antes do deploy, verificar a numeração de migrações com a equipe e validar PostgreSQL em ambiente isolado. Após publicar, testar os três tipos, cancelamento, protocolo no painel e comportamento de falha no ambiente real autorizado. Não ativar polling local com o mesmo token enquanto o Render estiver ativo.
