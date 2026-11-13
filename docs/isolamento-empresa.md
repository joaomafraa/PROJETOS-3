# Isolamento por empresa (HU08.2)

## Regra de confiança

Nas rotas do painel, a empresa vem do `ContextoAcesso`, que resolve o atendente
autenticado e ativo. IDs enviados na URL servem apenas para localizar recursos
dentro dessa empresa; eles nunca escolhem o tenant.

No canal Telegram, `empresaId` e `chatbotId` vêm da configuração do polling. A
identificação do canal valida que o chatbot pertence à empresa antes de criar ou
consultar cliente e conversa.

## Proteções aplicadas

- Conversas, histórico, tickets, responsáveis e leads são consultados com
  `id + empresaId` ou com filtro obrigatório de empresa.
- Alterações de conversa, ticket e atendente usam a empresa do atendente
  autenticado antes de bloquear ou modificar a linha.
- A administração não lê nem bloqueia um atendente de outra empresa por um ID
  global. Para o solicitante, um ID de outro tenant se comporta como um ID
  inexistente.
- Envios e revisões do Telegram incluem `empresa_id` nas consultas e atualizações.
  Uma tentativa com conversa de outro tenant recebe resposta genérica de recurso
  não encontrado, sem informar a empresa ou o conteúdo existente.
- Listagens retornam somente dados do tenant atual. Quando um identificador de
  outra empresa é usado em uma consulta de coleção, o resultado é vazio.

## Testes

`IsolamentoEmpresaTests` cobre serviços de fila, histórico, tickets e alterações.
`IsolamentoEmpresaApiTests` exercita as rotas HTTP com duas empresas e verifica:

- listas sem dados da outra empresa;
- histórico e detalhes de ticket sem exposição por ID;
- bloqueio ao assumir conversa, enviar mensagem e revisar envio de outro tenant;
- preservação do estado e do conteúdo da outra empresa após as tentativas.

Para executar apenas a cobertura de isolamento:

```powershell
cd chatbot
.\mvnw.cmd '-Dtest=IsolamentoEmpresaTests,IsolamentoEmpresaApiTests,AdministracaoApiTests,IdentificacaoClienteIntegracaoTests,LeadServiceIntegracaoTests' test
```

## Pontos que novas frentes devem manter

- CRM, documentos e qualquer outra integração externa devem usar uma chave de
  escopo por empresa também no provedor externo e nas regras de idempotência.
  Essas integrações ainda não existem na base atual e não foram simuladas.
- O polling atual associa um bot do Telegram a uma empresa por configuração.
  Suporte a vários bots no mesmo processo deve preservar essa associação
  validada, sem aceitar `empresaId` de mensagens recebidas.
- O banco permite o mesmo e-mail de atendente em empresas diferentes, enquanto o
  login atual recebe apenas o e-mail. Nessa situação ambígua, a autenticação é
  recusada. Antes de liberar esse cenário, a equipe deve definir um identificador
  de login que inclua a empresa ou tornar o e-mail globalmente único.
