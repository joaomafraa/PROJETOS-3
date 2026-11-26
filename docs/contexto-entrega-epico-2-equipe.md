# NEXUS — Contexto da entrega do Épico 2

Documento para orientar os integrantes e suas IAs. Referência inicial da análise: `main` no commit `08cb947`. O repositório pode ter evoluído: sempre confira a versão disponível antes de implementar.

## Como usar este arquivo

Cada integrante pode anexar este mesmo arquivo à sua IA e informar seu nome ou sua parte. A IA deve identificar a seção correspondente, ler o contexto comum e verificar o repositório atual. Se não souber qual integrante está usando o arquivo, deve perguntar o nome antes de escolher uma tarefa.

Este é um guia de implementação, não uma prova de que uma história ou o épico estejam completos. A IA deve trabalhar na parte solicitada, respeitar as instruções do usuário e as regras locais do repositório, explicar decisões relevantes e entregar testes e limitações reais. Não deve reimplementar as partes já existentes nem fazer o trabalho de outra pessoa sem pedido.

## Contexto do projeto

O NEXUS é um projeto acadêmico de atendimento. Clientes conversam com um bot no **Telegram**; uma IA pode responder usando documentos oficiais e um atendente humano pode assumir a conversa pelo painel. Há empresas distintas e os dados devem permanecer isolados entre elas.

O documento original de histórias menciona WhatsApp. A equipe decidiu usar **Telegram** nesta implementação. Não implemente WhatsApp nesta entrega nem apresente Telegram como integração com WhatsApp.

O **Épico 2 — Serviços e solicitações** tem três grupos de histórias:

- **HU04.1:** consultar plano ou serviço contratado por cliente autorizado.
- **HU04.2:** consultar o andamento de solicitações do cliente.
- **HU04.3:** informar quando a fonte de consulta estiver indisponível e apresentar uma alternativa.
- **HU05.1:** abrir chamado de suporte pelo canal, descrevendo o problema.
- **HU05.2:** informar o protocolo ao cliente após o registro do chamado.
- **HU05.3:** notificar os responsáveis da empresa sobre novos chamados.
- **HU06.1:** registrar pedido de orçamento com necessidade e contato.
- **HU06.2:** registrar pedido de demonstração, reunião ou visita técnica com preferência de horário.
- **HU06.3:** confirmar o registro e explicar que o horário depende da validação da equipe.

### Decisão sobre os dados

Os planos e serviços serão **fictícios e armazenados no próprio NEXUS**. A demonstração deve apresentá-los como simulação. Não prometer consulta a sistema externo real nem criar uma integração paga. O usuário autorizado só pode consultar dados fictícios vinculados ao seu cliente e à sua empresa.

Os pedidos, chamados e protocolos devem ser registros persistidos reais da aplicação, ainda que os dados de demonstração sejam fictícios. Não resolver uma história apenas com mensagens fixas, mocks de produção ou informações inventadas pela IA.

## O que já existe — confira antes de alterar

- `model/Ticket` tem tipos `SUPORTE`, `ORCAMENTO`, `DEMONSTRACAO`, `REUNIAO` e `VISITA_TECNICA`, status, protocolo, cliente, empresa e conversa de origem. Um valor no enum **não significa que o fluxo correspondente já esteja implementado**.
- `service/AbrirChamadoService` abre chamado de suporte a partir de conversa da empresa e gera protocolo com sequência e unicidade. Na base analisada, ainda não existe fluxo completo de abertura pelo Telegram nem garantia de idempotência por atualização recebida.
- Existem listagem, filtros, paginação, detalhes, assunção e conclusão de tickets no painel. Preservar esses fluxos.
- `/solicitacoes` no Telegram consulta até 20 solicitações existentes via `ValidacaoClienteService`, com validação adicional de identidade. Não cria chamado. `/validar` e `/codigo` integram o fluxo de validação existente.
- A validação usa um contato de e-mail confiável, código temporário e mecanismo de envio. Em ambiente real, isso depende da configuração funcional de e-mail; um telefone reconhecido, sozinho, **não prova identidade**.
- Há cadastro de responsáveis por tipo de notificação. Isso, por si só, não implementa envio de notificações para novos chamados.
- Há entidade e gestão de leads, histórico, resumo estruturado e transferência para atendimento humano. Reaproveitar quando necessário, sem criar vínculos presumidos ou duplicados.
- O bot usa polling e já registra atualizações e envios, inclusive tratamento de entrega incerta. O fluxo novo precisa respeitar essas garantias.

Documentação útil: `docs/abertura-chamado.md`, `docs/telegram.md`, `docs/assumir-ticket.md`, `docs/concluir-ticket.md`, `docs/administracao-atendentes.md`, `docs/seguranca-identidade-e-perfis.md` e `docs/leads-atendimento.md`. Documentos podem estar desatualizados: confira código, migrações e testes.

## Divisão da equipe

### João Carlos — HU05.1 e HU05.2: fluxo no Telegram e integração

**Objetivo:** o cliente consegue solicitar abertura de chamado de suporte pelo menu do Telegram, informar o problema e receber o protocolo real.

Implementar a interação no canal, com mensagens claras, opção de cancelar e validação da descrição. Integrar com o serviço preparado por Pedro. O chamado criado deve aparecer no painel com o cliente e a empresa da conversa. Preservar cadastro, triagem, atendimento humano, histórico e controle de envios existentes.

Coordenar a validação integrada do épico: reunir as evidências das partes e testar a jornada em ambiente autorizado. Isso não transfere os testes individuais para João.

**Aceite:** uma solicitação concluída gera um único chamado e informa o mesmo protocolo mostrado no painel; entrada inválida ou cancelamento não criam chamado; atualização repetida ou falha de envio não cria duplicatas. Testar também conversa sob atendimento humano e isolamento.

**Dependência:** contrato idempotente e abertura persistida de Pedro; não criar um segundo gerador de protocolo.

### Artur Almeida — HU04.1: planos e serviços fictícios

**Objetivo:** o cliente autorizado consulta os planos ou serviços fictícios relacionados a ele e à empresa.

Criar a representação persistida e a consulta desses dados com acesso por empresa e cliente. Entregar dados de teste explicitamente fictícios para ambiente isolado, sem preencher dados reais automaticamente. Mostrar plano ou serviço e os atributos que realmente existirem. Caso não haja vínculo, responder que não há informação disponível sem revelar dados de terceiros.

Integrar a consulta ao canal e à validação de identidade existente, combinando a forma de acionamento com João e Malta. A fonte é local: abstração só deve ser adicionada se ajudar a implementar o fluxo, sem prometer integração externa.

**Aceite:** cliente validado consulta apenas seus dados; cliente não validado, cliente de outra empresa e identificadores forjados não têm acesso. Dados persistem e o resultado é claramente apresentado como demonstração.

### Gabriel Malta — HU04.2 e HU04.3: consulta e indisponibilidade

**Objetivo:** o cliente autorizado consegue consultar suas solicitações e entende quando a consulta não pode ser feita.

Revisar e completar o fluxo `/solicitacoes`, aproveitando `ValidacaoClienteService`. Distinguir resultado vazio, necessidade de validação e falha de consulta. Para indisponibilidade, apresentar mensagem segura e alternativa como `/atendente`, sem afirmar que uma consulta foi bem-sucedida ou retornar dados de outro cliente.

Coordenar com Artur um contrato consistente para erros de consulta de planos. Não capturar erros de forma que a falha se torne uma lista vazia. Não enviar códigos, tokens ou exceções técnicas ao cliente.

**Aceite:** consulta validada retorna protocolo, tipo e estado corretos; validação ausente impede consulta; erro simulado apresenta alternativa sem vazar dados. Testar falha sem depender de um serviço externo pago.

### Márcio Rodriguez — HU05.3: notificação de chamados

**Objetivo:** quando um chamado novo é registrado, os responsáveis configurados para esse evento recebem uma notificação pela empresa correta.

Reaproveitar `ResponsavelNotificacao` e as regras de perfis/empresa existentes. Verificar quais canais de comunicação e endereços estão realmente disponíveis. Se não houver canal externo configurado, implementar uma notificação persistida e visível no painel; não simular envio por e-mail ou Telegram como se tivesse ocorrido. Se um canal externo for proposto, alinhar configuração e comportamento com a equipe.

Separar a criação do chamado da tentativa externa de entrega. Evitar notificações duplicadas em reprocessamento e prever falhas ou ausência de responsáveis de forma explícita. Não marcar como enviado sem confirmação.

**Aceite:** novo chamado gera uma notificação para cada responsável elegível da mesma empresa; outras empresas e responsáveis inativos não recebem; repetição e falha têm comportamento testado; o chamado permanece salvo mesmo quando a notificação não puder ser entregue.

### Pedro Martins — HU05.1, HU05.2 e HU06.2: negócio e persistência

**Objetivo:** disponibilizar serviços para registrar chamados e pedidos de demonstração, reunião e visita técnica a partir de uma conversa validada.

Reaproveitar `AbrirChamadoService`, sequência, protocolo e entidade `Ticket`. Acrescentar idempotência para o comando/atualização de origem: repetição da mesma operação retorna o mesmo resultado, sem duplicar registro. Um pedido intencionalmente novo deve poder gerar outra solicitação. Definir a chave e a transação em conjunto com João e Hélio.

Implementar os tipos de HU06.2, preservando necessidade e preferência de horário como **preferência**, não como agendamento confirmado. Definir como essa informação será persistida e apresentada. Registrar o pedido antes de qualquer confirmação ou notificação. Coordenar o contrato com Mendes e Guilherme para evitar serviços ou migrações concorrentes.

**Aceite:** protocolo único e persistente, tipos corretos, conversa/cliente/empresa corretos, preferência preservada, operação repetida idempotente e isolamento. Não alterar dados antigos nem criar recursos fictícios no banco compartilhado durante testes.

### Hélio de Moraes — HU04.1–HU04.3 e HU05.1: segurança e isolamento

**Objetivo:** validar e reforçar a segurança das novas consultas e da abertura de chamados.

Aplicar a empresa obtida da sessão autenticada ou do canal validado, nunca de parâmetro livre do cliente. Cobrir cliente de outra empresa, outro cliente da mesma empresa, validação ausente/expirada, acessos HTTP indevidos, repetição concorrente e dados de teste não vazando para outra conta. Coordenar com Artur, Malta e Pedro; cada autor também deve implementar os filtros da própria parte.

Verificar que logs, respostas, mensagens de erro e histórico não exponham códigos de validação ou informações privadas. Não recriar o mecanismo de validação existente.

**Aceite:** testes de serviço e integração demonstram as tentativas bloqueadas e que nenhum registro de outra empresa/cliente foi lido, alterado ou exposto.

### Guilherme Guimarães — HU06.1: pedido de orçamento

**Objetivo:** registrar pedido de orçamento com necessidade e contato para a equipe comercial.

Usar os dados já fornecidos pelo cliente e pedir somente o que estiver ausente. Persistir a solicitação com tipo comercial adequado e vinculá-la à conversa e à empresa. Reaproveitar os serviços de leads/tickets, respeitar detecção de duplicidades e não criar um lead presumido sem uma regra acordada. Se for usar protocolo comum, combinar com Pedro.

Integrar uma entrada clara no Telegram sem interferir no cadastro ou nas outras opções do menu. Não inventar preço ou prometer proposta pronta. Não implementar integração com CRM externo nesta entrega.

**Aceite:** pedido válido aparece para a equipe com necessidade e contato disponíveis; falha ou cancelamento não confirma registro inexistente; repetição não cria duplicata. Dados de outras empresas não são acessíveis.

### Gabriel Mendes — HU06.3: confirmação dos pedidos

**Objetivo:** após um pedido de demonstração, reunião ou visita ser realmente salvo, confirmar o registro ao cliente sem tratar a preferência como agenda confirmada.

Usar o resultado retornado pelo serviço de Pedro e o envio existente do Telegram. Exibir o tipo do pedido e protocolo quando o serviço fornecer; indicar claramente que a equipe ainda precisa confirmar a data e o horário. Distinguir falha de persistência de incerteza no envio; não confirmar um pedido que não foi salvo.

Essa é uma única história. Mendes não é responsável por construir todo o cadastro ou a persistência dos pedidos de HU06.2.

**Aceite:** pedido persistido recebe confirmação correta; horário não é apresentado como confirmado; erro de registro não gera falsa confirmação; reprocessamento não cria pedido nem envio duplicado. Testes cobrem os três tipos.

## Contratos e ordem de integração

Antes de alterar classes ou migrações compartilhadas, combinar contratos mínimos. Os nomes abaixo são sugestões, não APIs já existentes:

1. **Pedro → João:** resultado de abertura com ID, protocolo, status e chave idempotente da operação. A descrição e a conversa devem ser validadas; empresa vem do canal confiável.
2. **Pedro → Mendes:** resultado de pedido persistido com tipo, protocolo e preferência de horário, sem afirmar agendamento.
3. **Pedro/Guilherme → Márcio:** evento de solicitação realmente criada, com identificação e empresa, sem duplicar notificação quando houver repetição.
4. **Artur ↔ Malta:** resultado que distingue encontrado, não encontrado, validação necessária e indisponível. Não expor dados pela mensagem de erro.
5. **Todos ↔ Hélio:** critérios de autorização e exemplos para duas empresas e dois clientes.

Não considerar um serviço de outra branch integrado até testar a combinação real. Enquanto aguarda dependências, testar com substitutos apenas em testes e documentar o que falta.

## Arquitetura e trabalho em equipe

- A aplicação fica em `chatbot/`: Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway e PostgreSQL. O painel fica em `chatbot/src/main/resources/static`; confira o código antes de presumir outra stack.
- Use uma branch por integrante. Antes de mudar de branch ou atualizar a base, confira alterações locais; preserve trabalho alheio e não use reset destrutivo.
- Prefira regras em serviços/domínio, acesso no repositório e entrada/saída nos controllers. Mantenha encapsulamento e relacionamentos da disciplina de POO.
- Arquivos de conflito provável: `ProcessarMensagemTelegramService`, `TelegramCliente`, `Ticket`, `SecurityConfig`, `painel.html`, `solicitacoes.js` e migrações. Combinem alterações de contrato antes de editá-los.
- Verifique a maior versão Flyway na branch atual e combine a próxima versão. **Não edite migrações já aplicadas** e não apague dados para resolver conflitos.
- Trate o identificador de atualização do Telegram como chave da operação quando o contrato o definir. Persistir a chave e o efeito de negócio atomicamente; não confiar somente em uma variável em memória.
- Não reenvie automaticamente mensagem de entrega incerta sem considerar a possibilidade de já ter sido entregue. Preserve o mecanismo existente de revisão.
- Não adicionar segredos ao código, logs, testes ou commits. As APIs internas do projeto não exigem contratar um produto externo.

## Ambiente e testes

O projeto pode usar Neon e Render reais. Confira o banco configurado antes de iniciar a aplicação ou rodar testes. Use H2 para testes existentes e PostgreSQL isolado quando precisar validar SQL ou concorrência específica; não use o banco compartilhado para popular fixtures ou apagar dados.

**Não rode polling local com o mesmo token enquanto o Render estiver recebendo mensagens.** Para testes automatizados, mantenha `TELEGRAM_POLLING_ENABLED=false` e use clientes simulados. Não envie mensagens ao bot real nem use a cota da Groq durante a suíte comum.

Exemplo de comando no PowerShell, dentro de `chatbot/`, substituindo o nome pela classe real:

```powershell
$env:TELEGRAM_POLLING_ENABLED = 'false'
$env:TESTAR_GROQ_REAL = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=NomeDaClasseDeTeste' test
```

Confirme que o teste usa um banco isolado; não carregue `.env` de produção nos testes. Cada integrante deve testar sucesso, falha e segurança relevantes e informar o resultado real. Ao integrar, testar cadastro, consulta validada, abertura de chamado, protocolo, fila, atendimento humano e demais fluxos afetados.

## Validação para a apresentação

1. Cliente se identifica e faz a validação adicional quando necessário.
2. Cliente autorizado consulta um plano fictício associado a ele; outro cliente não vê o dado.
3. Cliente abre um chamado pelo Telegram; o protocolo coincide com o painel.
4. Repetir a mesma atualização não cria outro chamado; cancelamento não registra pedido.
5. Responsáveis da empresa recebem ou visualizam a notificação conforme o canal realmente implementado.
6. Cliente consulta suas solicitações e recebe alternativa se a consulta falhar.
7. Cliente solicita orçamento, demonstração, reunião ou visita; o pedido aparece para a equipe.
8. Confirmação informa que a preferência de horário depende de validação humana.
9. Uma segunda empresa e um segundo cliente não conseguem acessar registros alheios.

Os cenários acima são objetivos de teste, não evidência de que já passaram. Antes de declarar o épico concluído, conferir todas as histórias, o documento original e a adaptação acordada do canal e da base fictícia.

## Resposta esperada da IA ao terminar

Explique o que foi implementado, quais arquivos e contratos mudaram, como executar e testar, quais testes passaram e quais não foram executados. Informe migrações e variáveis necessárias sem expor credenciais. Distinga claramente funcionalidade isolada, integração local e validação no ambiente real. A IA não deve fazer commit, push ou deploy automaticamente sem que o integrante peça.
