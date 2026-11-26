# Segurança e isolamento do Épico 2

## Contrato para consultas do cliente

`ValidacaoClienteService.exigirContextoValidado(empresaId, conversaId)` é a
fronteira de autorização para consultas de planos, serviços e solicitações no
Telegram. O método devolve `empresaId`, `clienteId` e `conversaId` obtidos da
conversa do canal e de uma validação temporária vigente.

As partes de HU04 não devem aceitar `clienteId` ou `empresaId` enviados pelo
cliente para escolher registros. Os repositórios devem filtrar simultaneamente
pelos IDs de empresa e cliente do `ContextoClienteValidado`.

## Proteções persistentes

A migração `V25__integridade_escopo_empresa.sql` adiciona chaves estrangeiras
compostas. Elas impedem combinações cruzadas de empresa em contatos e
atualizações do Telegram, conversas, chatbots, responsáveis e tickets. A
migração não corrige nem apaga dados: se já houver um vínculo inconsistente, a
implantação falha para que o dado seja auditado antes de prosseguir.

Ao reutilizar um contato do Telegram, o serviço também confere se empresa,
cliente e conversa continuam formando o mesmo vínculo. Uma inconsistência
recebe apenas a mensagem genérica `Canal indisponivel`, sem expor IDs ou dados.

## Repetição e códigos privados

Uma atualização já registrada só pode ser reaproveitada quando existe um envio
com estado conhecido. Se ela reaparecer antes de haver resultado preparado, o
processamento é interrompido para revisão; a regra evita executar novamente uma
operação de negócio em uma janela concorrente.

Códigos de validação continuam substituídos por
`[codigo de verificacao omitido]` antes do histórico e não entram no contexto da
IA. Respostas de falha de validação permanecem genéricas.

## Cobertura automatizada

- `ValidacaoClienteTests`: validação ausente e expirada, outro cliente, outra
  empresa e omissão do código no histórico.
- `IntegridadeEscopoDadosTests`: tentativas de persistir vínculos cruzados.
- `RepeticaoTelegramConcorrenteTests`: duas execuções simultâneas da mesma
  atualização produzem um único registro e uma única mensagem.
- `IsolamentoEmpresaApiTests`: acessos HTTP por IDs de outra empresa não leem
  nem alteram os registros.

A idempotência da criação do chamado por uma chave de operação persistida faz
parte do contrato de Pedro/João. Quando essa implementação for integrada, ela
deve reutilizar o escopo acima e acrescentar ao teste concorrente a verificação
de que existe exatamente um ticket e um protocolo para a mesma atualização.
