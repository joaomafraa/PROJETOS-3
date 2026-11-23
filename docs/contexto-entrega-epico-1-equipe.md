# NEXUS — Contexto e guia de implementação para as IAs da equipe

Data da divisão: 17/11/2026. Referência consultada: commit `073d031`.

## 1. Como usar este arquivo

O integrante deve anexar este arquivo à conversa com sua IA e informar apenas seu nome ou sua responsabilidade. Não precisa copiar um prompt nem conhecer o histórico das conversas de João.

**Instruções para a IA que receber este documento:**

1. Identifique o integrante e leia sua seção, os contratos e as regras comuns antes de alterar código.
2. Se o integrante não estiver identificado, pergunte somente qual é seu nome/parte. Não escolha por conta própria.
3. Localize o repositório disponível e leia suas instruções locais. Os caminhos deste documento são relativos à raiz do repositório, não ao computador de João.
4. Confira a branch, as alterações locais e o código atual. A base pode ter evoluído desde a elaboração deste arquivo.
5. Reutilize o que já existe. Apresente brevemente o que sua parte acrescentará e implemente dentro desse escopo quando solicitado pelo integrante.
6. Não implemente as tarefas dos demais. Se uma dependência estiver ausente, avance com o contrato e testes isolados, identificando explicitamente o que ainda depende da integração.
7. Não apresente mocks, respostas fixas ou telas sem integração como funcionalidade pronta.
8. Entregue código, testes relevantes, instruções para verificar e descrição das dependências restantes. Explique ao integrante o que mudou, em linguagem que ele consiga apresentar ao professor.

Este arquivo é contexto de trabalho, não evidência de que todas as funcionalidades propostas já existem ou estão aprovadas em produção.

## 2. Projeto, objetivo e entrega

NEXUS é um sistema de atendimento por empresa: o cliente conversa pelo Telegram, a IA atende automaticamente e uma equipe humana pode assumir a conversa no painel. O histórico, os clientes, os tickets e os leads são persistidos. O projeto é acadêmico e deve demonstrar POO, encapsulamento, relacionamentos e separação entre apresentação, negócio e persistência.

O enunciado desta semana pede a segunda história funcionando ponta a ponta no ambiente real, integração das duas histórias, correção de bugs e consistência das classes/camadas. Também menciona no mínimo duas histórias épicas completas. Não concluir que o requisito acadêmico inteiro está atendido apenas porque uma tarefa individual ficou pronta; conferir a interpretação e os critérios com o professor.

A decisão do grupo para esta rodada é:

- João fica com a validação final do Épico 3 e com a gestão dos documentos oficiais da IA.
- Os outros sete integrantes trabalham nas lacunas do Épico 1.
- O avanço central é a IA responder a partir de documentos oficiais da empresa, com triagem, configuração, fontes e isolamento.
- Não reconstruir Telegram, transferência, assunção, histórico ou resumo já existentes.

O documento original de histórias pode citar WhatsApp e CRM externo. Para o fluxo atual, o grupo adotou Telegram e o relacionamento/leads no próprio NEXUS. Não adicionar WhatsApp ou um CRM de terceiros nesta divisão. Registrar essa adaptação na documentação da entrega, sem afirmar que implementamos a integração original.

As referências HU abaixo seguem a divisão acordada. Conferir a redação do documento oficial disponível no repositório; este guia detalha tarefas técnicas e não substitui os critérios acadêmicos das histórias. Mais de uma pessoa pode contribuir para uma HU, com responsabilidades diferentes. Uma parte concluída não significa que a HU inteira terminou.

## 3. Base existente: preservar e reaproveitar

Na revisão do código foram encontrados:

- Entidades `Empresa`, `Cliente`, `Atendente`, `Chatbot`, `Conversa`, `Mensagem`, `Ticket` e `Lead`.
- Repositórios, serviços, controllers e testes de fila, histórico, tickets, administração, identificação e leads.
- Spring Security, autenticação do atendente e contexto de acesso por empresa/perfil.
- Recebimento de mensagens do Telegram, identificação/cadastro e encaminhamento à IA.
- Solicitação de atendente, assunção, resposta humana, conclusão e tratamento de envios sem confirmação.
- Resumo estruturado da conversa e vínculo explícito entre lead e conversa.
- Arquivamento de conversas e desativação de pessoas da equipe preservando histórico.
- Spring AI integrado à Groq.

O usuário já testou o acesso pelo Telegram e a transferência/assunção. Não atribuir essas funcionalidades novamente como implementação nova para Artur ou Guilherme. A validação de regressão continua necessária após integrar as novas partes.

**Lacuna observada na IA:** `GroqProvedor` recebe o histórico e uma instrução genérica para não inventar informações. Não foi encontrada, na revisão desta base, a cadeia de documentos oficiais, busca de trechos e geração fundamentada nesses trechos. Uma instrução no prompt não equivale a conhecimento oficial nem garante ausência de invenções.

**Documentação antiga pode estar desatualizada:** o README ainda contém afirmações de que autenticação e Spring AI não foram implementados, apesar de o código já conter essas partes. Usar código, testes e execução como evidência, registrando divergências.

## 4. Mapa técnico para localizar o trabalho

| Local | Função |
|---|---|
| `chatbot/pom.xml` | Java 21, Maven, Spring Boot, Spring AI, JPA, Security, Flyway e testes. Conferir versões efetivamente declaradas; não atualizar stack por conveniência. |
| `chatbot/src/main/java/com/wxn/chatbot/model` | Entidades e regras do domínio. |
| `chatbot/src/main/java/com/wxn/chatbot/repository` | Persistência e consultas. |
| `chatbot/src/main/java/com/wxn/chatbot/service` | Serviços de negócio. |
| `chatbot/src/main/java/com/wxn/chatbot/web` | Controllers da aplicação. |
| `chatbot/src/main/java/com/wxn/chatbot/security` | Segurança e `ContextoAcesso`. |
| `chatbot/src/main/java/com/wxn/chatbot/llm` | `ProvedorLlm`, `GroqProvedor`, `RespostaAutomaticaService`. |
| `chatbot/src/main/java/com/wxn/chatbot/telegram` | Recebimento, cadastro, transferência, geração e envio. |
| `chatbot/src/main/resources/db/migration` | Migrações Flyway; na base consultada existem V1 a V17. Conferir novamente antes de criar outra. |
| `chatbot/src/main/resources/static` | Painel HTML/CSS/JavaScript, incluindo `painel.html`, `painel.css`, `contexto.js`, `historico.js`, `administracao.js` e `leads.js`. |
| `chatbot/abertura` | Projeto React/Vite/Framer Motion da abertura. Não presumir que o painel inteiro usa React. |
| `chatbot/src/test` | Testes existentes, inclusive integração e HTTP. |
| `docs` | Guias dos fluxos existentes e modelo de classes. |

Leia, conforme a tarefa: `docs/telegram.md`, `docs/atendimento-humano.md`, `docs/seguranca-identidade-e-perfis.md`, `docs/modelo-de-classes.md`, `docs/resumo-estruturado.md` e `docs/leads-atendimento.md`.

Cuidados específicos com o código existente:

- `RespostaAutomaticaService` faz a chamada ao provedor fora da transação e revalida estado/histórico antes de salvar. Preservar essa proteção contra uma resposta automática chegar após assunção humana ou mudança da conversa.
- `ProvedorLlm` atualmente retorna texto a partir de uma lista de `TrechoConversa`. A evolução desse contrato deve ser única e compatível entre Guilherme, Malta e Pedro.
- `ProcessarMensagemTelegramService` coordena cadastro, comandos, histórico e geração. Evitar que cada integrante crie um fluxo paralelo de processamento.
- `ContextoAcesso` obtém atendente, empresa e perfil autenticados. Não confiar em um `empresaId` enviado livremente pelo navegador como autorização.
- Há configuração de autorização HTTP. Um serviço correto não basta: testar também se o endpoint novo está acessível ao perfil correto e bloqueado aos demais.

## 5. Divisão de responsabilidades

### João Carlos — validação final do Épico 3 + HU02.1: documentos oficiais

**Resultado:** o administrador consegue disponibilizar e administrar conteúdo oficial para a IA da sua empresa.

Implementar:

- Cadastro/upload, listagem, consulta e ativação/desativação dos documentos no painel existente.
- Persistência do vínculo com a empresa, título, estado e conteúdo extraído, com identidade de versão suficiente para rastrear as respostas.
- Extração do texto e tratamento de arquivo vazio, formato não suportado, excesso de tamanho e erro de leitura.
- Definir e documentar formatos e limites realmente suportados. Não prometer OCR ou leitura de PDF digitalizado se isso não estiver implementado.
- Disponibilizar ao mecanismo de busca somente conteúdo processado e ativo.
- Persistir o conteúdo necessário fora do disco temporário do Render. Não depender de um upload local que desaparece após reinício.

Validação final do Épico 3:

- Conferir identificação/cadastro, permissões, isolamento, administração, leads, fila, histórico e atendimento humano contra os critérios das histórias.
- Validar o vínculo lead/conversa pela interface e pela autorização HTTP.
- Registrar evidências do fluxo Telegram → IA → solicitação humana → assunção → resposta → conclusão → nova mensagem.
- Verificar arquivamento e desativação sem perda de histórico.
- Registrar falhas e dependências reais; não declarar conclusão só pela presença de classes.

**Limite:** não implementar a busca de Artur nem a geração de Guilherme. João entrega o conteúdo oficial consumível por eles.

**Aceite:** documento válido aparece no painel, permanece após reinício e fica disponível para busca; documento inválido tem erro compreensível; documento inativo não pode fundamentar novas respostas.

### Artur Almeida — Épico 1 / HU02.1: busca nos documentos

**Resultado:** dada uma pergunta e uma empresa validada, localizar trechos oficiais relevantes.

Implementar:

- Divisão do conteúdo extraído em trechos identificáveis, preservando documento, versão e localização quando disponível.
- Busca e ordenação por relevância, com limites de quantidade e tamanho do contexto.
- Retorno vazio explícito quando não houver material suficiente, sem preencher com conteúdo inventado.
- Atualização/invalidação dos trechos quando o documento mudar ou for desativado.
- Estratégia compatível com os recursos do projeto. Busca textual pode ser um primeiro caminho; não presumir que a Groq fornece embeddings nem introduzir um serviço pago sem decisão do grupo.

**Dependência:** conteúdo fornecido por João. Enquanto isso, usar dados fictícios somente nos testes.

**Limite:** não chamar o LLM para redigir a resposta, não cadastrar documentos e não refazer o canal Telegram.

**Aceite:** perguntas de teste recuperam trechos pertinentes; perguntas sem correspondência não recebem trechos arbitrários; documentos inativos e de outra empresa nunca aparecem. Documentar os limites da estratégia de busca.

### Guilherme Guimarães — Épico 1 / HU02.1 e HU02.2: geração fundamentada

**Resultado:** a IA existente usa pergunta, histórico e trechos oficiais na resposta real.

Implementar:

- Integração da busca de Artur ao fluxo de `RespostaAutomaticaService` e `ProvedorLlm`.
- Contexto estruturado, distinguindo instruções, histórico e conteúdo documental.
- Limite de contexto e tamanho de resposta para não enviar indefinidamente todo o histórico/documentação.
- Integração da política de insuficiência de Malta e das fontes de Pedro.
- Preservação das regras que suspendem a IA durante atendimento humano e descartam uma geração quando a conversa mudou.
- Tratamento de falha, resposta vazia e limite do provedor sem duplicar mensagens nem expor detalhes internos ao cliente.

**Limite:** não reconstruir transferência/assunção; não criar outro cliente Groq ou adicionar uma segunda integração de Spring AI.

**Aceite:** uma pergunta sobre conteúdo cadastrado produz resposta compatível com esse conteúdo; ausência de fonte segue a política definida; o fluxo aparece no histórico real sem duplicação. Testar também assunção humana durante a geração.

### Gabriel Malta — Épico 1 / HU02.3: insuficiência e limites da IA

**Resultado:** a IA reconhece quando precisa esclarecer uma pergunta ou não possui informação oficial suficiente.

Implementar:

- Política testável para pergunta ambígua, ausência de documentos e evidência insuficiente.
- Mensagens claras para pedir esclarecimento ou informar a limitação.
- Oferta de atendimento humano utilizando o fluxo existente, sem transferir automaticamente toda pergunta desconhecida.
- Regras para a IA não inventar preços, prazos, políticas ou serviços da empresa.
- Casos de teste com instruções maliciosas em mensagens e documentos; tratar documentos como conteúdo, nunca como ordens para alterar permissões ou executar ações.

**Dependência:** resultado da busca e integração de Guilherme. Definir a política em componente isolado, evitando uma segunda montagem de prompt concorrente.

**Limite:** não refazer autenticação nem o endpoint de transferência. Não prometer garantia absoluta contra alucinação; demonstrar comportamento e limites pelos testes.

**Aceite:** os casos sem informação recebem resposta honesta, sem fatos fabricados; ambiguidades geram esclarecimento; pedido explícito de humano continua usando a jornada existente.

### Márcio Rodriguez — Épico 1 / HU01.3: configuração do atendimento

**Resultado:** cada empresa consegue configurar o funcionamento do atendimento e a comunicação fora do expediente humano.

Implementar:

- Configuração persistida de horários, dias e fuso da equipe humana.
- Configuração dos assuntos atendidos automaticamente e da mensagem fora do expediente.
- Interface de administração consistente com o painel atual e restrita ao perfil autorizado.
- Serviço que informa disponibilidade e escopo para a triagem e a geração.
- Comportamento fora do horário: explicar a situação e permitir solicitação de humano conforme a fila existente, sem prometer atendimento imediato.

**Limite:** não reconstruir polling, cadastro de atendentes ou ativação de chatbot que já existe. Horário humano não deve desligar automaticamente a IA.

**Aceite:** configurações persistem, variam por empresa e produzem mensagens adequadas dentro/fora do horário, com testes de fuso e limites de expediente. Disponibilidade contínua depende também da hospedagem; não prometer 24h real sem verificar o plano do Render.

### Pedro Martins — Épico 1 / HU02.2: fontes e apresentação das respostas

**Resultado:** o atendente consegue consultar de onde veio uma resposta automática e o cliente recebe texto legível.

Implementar:

- Registro das fontes associadas à mensagem efetivamente salva: documento, versão e trechos utilizados, segundo o contrato combinado.
- Identificação das fontes no painel, aproveitando a visualização existente da conversa.
- Formatação da resposta no Telegram respeitando os limites do canal, sem cortar referências indevidamente.
- Distinção entre trechos recuperados e fontes efetivamente citadas/associadas. Não afirmar que todo documento recuperado sustenta todas as frases.
- Validação de que uma referência produzida pela IA pertence ao conjunto de fontes fornecidas; não aceitar IDs ou links inventados.

**Dependência:** identificação dos trechos de Artur e resultado estruturado de Guilherme.

**Limite:** não refazer histórico, resumo ou layout geral. Não publicar arquivos internos nem URLs privadas no Telegram; a indicação pode usar título e seção quando permitido.

**Aceite:** mensagem nova mostra as fontes corretas, isoladas por empresa; mensagem antiga sem fontes continua funcionando; desativar documento não destrói o registro histórico da origem da resposta.

### Hélio de Moraes — Épico 1 / HU02.1: segurança da base de conhecimento

**Resultado:** os novos documentos e contextos da IA respeitam empresa, perfil e estado de ativação.

Implementar e integrar:

- Regras de acesso da base de conhecimento em serviços, consultas e endpoints.
- Testes com duas empresas tentando acessar documentos, trechos e fontes uma da outra.
- Verificação de que a busca e o prompt enviados ao provedor não incluem conteúdo de outra empresa.
- Tratamento de caches/índices e documentos desativados para impedir uso em novas respostas.
- Cenário de alteração/desativação enquanto a resposta é gerada, combinando com Guilherme a revalidação necessária antes de salvar.

**Limite:** não reescrever toda a segurança do projeto nem ser o único responsável pelo isolamento. Cada autor implementa os filtros da própria parte; Hélio integra as proteções e cobre os cenários de acesso indevido.

**Aceite:** testes HTTP e de serviço comprovam bloqueio de acesso cruzado e de operações administrativas indevidas; uma captura do contexto do provedor no teste comprova que dados proibidos não foram enviados.

### Gabriel Mendes — Épico 1 / HU01.2: triagem suporte/comercial

**Resultado:** a conversa tem uma intenção de atendimento persistida e visível no painel.

Implementar:

- Classificação suporte/comercial com estado explícito de indefinição.
- Pergunta de esclarecimento quando a intenção não estiver clara.
- Persistência e apresentação da classificação na conversa.
- Possibilidade de corrigir a intenção quando o cliente esclarecer ou mudar de assunto, sem reclassificar aleatoriamente a cada mensagem.
- Integração com o escopo de atendimento configurado por Márcio e com a jornada existente.

**Limite:** não recriar resumo estruturado, leads ou cadastro. Classificar como comercial não autoriza criar lead duplicado nem abrir ticket automaticamente. Qualquer consequência adicional deve estar prevista no fluxo e usar os serviços existentes.

**Aceite:** suporte, comercial e ambiguidade têm comportamentos verificáveis; a classificação permanece após recarregar o painel e reiniciar a aplicação; a triagem não responde por cima do atendente humano nem repete o cadastro já realizado.

## 6. Contratos entre as partes e ordem de integração

Os nomes abaixo são **propostas de contrato**, não classes que já existem. Antes de criar versões concorrentes, verificar o que já entrou na branch base e combinar os pontos compartilhados. Decisões internas reversíveis podem ser tomadas pelo responsável; mudanças no contrato público e migrações compartilhadas precisam de coordenação.

| Contrato proposto | Conteúdo mínimo | Responsável principal |
|---|---|---|
| Documento disponível | ID, empresa, título, versão, estado e texto extraído. | João |
| Trecho encontrado | ID do trecho, ID/versão do documento, texto, localização quando disponível e ordenação/relevância. | Artur |
| Resultado da busca | Lista limitada de trechos e indicação explícita de ausência de correspondência. | Artur |
| Política de resposta | Responder, pedir esclarecimento ou informar insuficiência; motivo e oferta de humano quando aplicável. | Malta |
| Configuração de atendimento | Empresa, fuso, horários, escopo e mensagem fora de expediente. | Márcio |
| Intenção da conversa | Suporte, comercial ou indefinida, vinculada à conversa e sua empresa. | Mendes |
| Resultado da geração | Texto e referências válidas, preservando o vínculo com a mensagem salva. | Guilherme, com Pedro |
| Registro de fontes | Mensagem, documento/versão e referências dos trechos, com controle de acesso. | Pedro |

Não usar uma suposta porcentagem de confiança do LLM como prova de que há evidência. A suficiência precisa de política explícita e exemplos testados. IDs de empresa vêm do contexto autenticado ou do canal validado, não de inferência do modelo.

Fluxo desejado:

```text
Telegram → identificação/cadastro existente → estado da conversa
  ├─ atendimento humano → preservar o fluxo humano existente
  └─ automático → triagem/escopo → busca nos documentos da empresa
       ├─ informação insuficiente → esclarecer ou informar limitação
       └─ trechos relevantes → geração fundamentada
            → revalidar conversa e documentos
            → salvar mensagem e fontes → enviar pelo fluxo existente
```

Ordem prática:

1. Alinhar os contratos mínimos e migrações. João e Artur iniciam a base documental; Márcio e Mendes podem avançar em módulos próprios.
2. Guilherme e Malta desenvolvem integração/política com substitutos de teste enquanto a busca não estiver pronta.
3. Pedro integra fontes ao resultado real e ao painel. Hélio participa desde o início para evitar uma base sem isolamento.
4. Integrar pequenas partes e executar regressões. João valida a jornada no ambiente real ao final.

Enquanto a dependência não existir, entregar testes e contrato utilizáveis. Não substituir temporariamente o atendimento real por um mock e esquecer de removê-lo.

## 7. Regras de colaboração e arquitetura

- Cada integrante trabalha em sua branch. Sugestões: `feature/documentos-ia`, `feature/busca-conhecimento`, `feature/respostas-fundamentadas`, `feature/limites-ia`, `feature/configuracao-atendimento`, `feature/fontes-respostas`, `feature/seguranca-conhecimento`, `feature/triagem-atendimento`.
- Verificar alterações locais antes de trocar de branch ou atualizar a base. Não usar reset destrutivo, reescrever histórico ou apagar trabalho alheio para resolver conflitos.
- Combinar alterações em `ProvedorLlm`, `RespostaAutomaticaService`, `ProcessarMensagemTelegramService`, `SecurityConfig`, `Conversa`, `Mensagem`, `pom.xml` e arquivos centrais do painel.
- Coordenar a numeração Flyway entre branches; não assumir que V18 continua livre. Nunca editar uma migração já aplicada para inserir a nova funcionalidade.
- Novas colunas precisam funcionar com dados antigos. Não apagar dados do Neon para fazer a aplicação iniciar.
- Regras de negócio ficam em serviços/domínio; controllers validam entrada e delegam; repositórios consultam/persistem. Evitar regras apenas no JavaScript.
- Manter encapsulamento e invariantes. Não resolver uma mudança expondo setters indiscriminados nas entidades.
- Chamadas externas demoradas não devem manter transações e locks do banco abertos.
- Respeitar a identidade visual atual do NEXUS. Não refazer home, login, vídeo ou animações nesta entrega.
- APIs mencionadas aqui são endpoints internos do projeto. Não é necessário contratar outro sistema para criá-las.
- Não adicionar dependência paga, novo provedor ou banco vetorial por padrão. Explicar a necessidade se a solução realmente exigir infraestrutura adicional.
- Commits devem ser pequenos e em português, por exemplo `feat: adicionar busca nos documentos da empresa`. Commit, push e deploy seguem o pedido do integrante; este arquivo não autoriza publicação automática na main.

## 8. Ambiente e testes sem interferir no bot real

O ambiente publicado usa Render, PostgreSQL/Neon, Groq e Telegram. Credenciais devem ficar em variáveis de ambiente; nunca incluí-las neste arquivo, em commits, prints ou logs de teste.

**Existe um bot ativo no Render. Não ativar polling local com o mesmo token simultaneamente.** Para desenvolvimento, manter `TELEGRAM_POLLING_ENABLED=false`. Testes de lógica usam clientes simulados. Teste real exige coordenação da instância que recebe as mensagens.

O banco local configurado pode apontar para o mesmo Neon usado pelo Render. Conferir antes de iniciar a aplicação. Usar banco de teste isolado para migrações/fixtures; não carregar a configuração real por conveniência.

Há Maven Wrapper e testes com H2 no perfil `test`. Primeiro inspecionar as propriedades e os testes da branch atual, especialmente os de conexão real. Exemplos a partir de `chatbot/`, para testes isolados explicitamente selecionados:

```powershell
# Windows / PowerShell
$env:TELEGRAM_POLLING_ENABLED = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=NomeDaClasseDeTeste' test
```

```bash
# Linux/macOS/Git Bash
TELEGRAM_POLLING_ENABLED=false ./mvnw -Dspring.profiles.active=test -Dtest=NomeDaClasseDeTeste test
```

Substituir `NomeDaClasseDeTeste` pela classe real. Esses comandos não dispensam confirmar o datasource e o isolamento do teste. Não carregar `.env` de produção para executar testes automatizados.

Além dos testes novos, executar as regressões relacionadas a histórico, Telegram, transferência, segurança e geração. Se a mudança usar SQL específico de PostgreSQL, verificar em PostgreSQL isolado; sucesso no H2 não comprova compatibilidade total com Neon.

Testes de rede real devem ser separados e optativos. Não gastar cota de Groq nem mandar mensagens ao Telegram real durante uma suíte comum.

Cada parte precisa de casos de sucesso, entrada inválida e acesso indevido quando aplicável. Testar regras e integração, não apenas getters ou a existência de componentes.

## 9. Cenários integrados para a entrega

Estes cenários são critérios de validação futura, não uma declaração de testes já executados:

1. Administrador cadastra documento válido; ele fica disponível apenas para a sua empresa.
2. Cliente já identificado faz pergunta coberta; a IA usa a informação correta e a mensagem aparece com fontes no painel.
3. Pergunta sem resposta nos documentos gera limitação ou esclarecimento, sem inventar informação.
4. Documento desativado deixa de fundamentar novas respostas; referências históricas continuam identificáveis.
5. Empresa A não consegue consultar, recuperar ou enviar à IA documentos da empresa B.
6. Cliente informa intenção de suporte/comercial; o painel mostra a classificação persistida.
7. Fora do expediente humano, o bot informa a situação e mantém a jornada possível sem prometer resposta imediata.
8. Cliente solicita humano, atendente assume e responde; a IA não interfere.
9. Atendente assume durante uma geração: a resposta automática obsoleta não é enviada.
10. Falha do provedor ou incerteza no envio não resulta em mensagem duplicada nem perda silenciosa do histórico.
11. Conclusão e nova mensagem seguem a regra já implementada e validada pelo grupo.
12. Reinício da aplicação preserva documentos, configurações, classificação e fontes.

João centraliza a evidência final; cada integrante fornece os testes e passos da própria parte. Não concentrar toda a correção e todo o teste nas mãos dele.

## 10. O que a IA deve entregar ao integrante

Ao finalizar a tarefa, informar:

- O que foi acrescentado e qual história isso atende.
- Quais comportamentos existentes foram reutilizados.
- Arquivos principais e contratos alterados.
- Migração e configuração novas, se houver, sem segredos.
- Testes executados e resultado real; distinguir testes que não foram possíveis.
- Passos curtos para verificar no painel/Telegram e explicar ao professor.
- Dependências de outros integrantes e limitações conhecidas.
- Situação honesta: pronto isoladamente, integrado localmente ou validado no ambiente real.

Não declarar Épico 1 ou Épico 3 completo por conta própria. O fechamento exige integração, critérios das histórias e validação final. Não inventar porcentagens de conclusão.
