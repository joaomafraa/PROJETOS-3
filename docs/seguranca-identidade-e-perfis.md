# Acesso por perfil e validacao de identidade

As rotas de administracao exigem um atendente autenticado, ativo e com perfil
ADMINISTRADOR atualmente registrado no banco. A verificacao e feita em cada
operacao, inclusive quando a sessao ainda contem um perfil anterior. Apos uma
promocao para administrador, e necessario entrar novamente para atualizar as
permissoes da sessao. Os servicos
de administracao tambem verificam o administrador e o escopo da empresa.
Administrador, coordenador e atendente ativos podem consultar fila, solicitacoes,
historico e leads da propria empresa, e assumir atendimento disponivel. Para
concluir um atendimento ou enviar mensagens, e necessario ser o responsavel.
Somente administradores podem gerenciar equipe, senhas e notificacoes. As regras
de empresa e responsavel continuam sendo verificadas nos servicos.

As rotas de atendimento e seus metodos estao listados no `SecurityConfig`; uma
nova rota fora de `/api/admin/**` precisa de regra e testes proprios. A familia
administrativa exige o papel de administrador na sessao, e cada operacao tambem
deve verificar o perfil atual e a empresa no backend.

A HU08.1 tambem abrange os dados e servicos consultados pelo cliente no chatbot.
O codigo atual nao define perfis de cliente nem implementa essas consultas.
Consultas de plano, dados de conta e andamento de solicitacoes devem exigir
identidade verificada e ser limitadas ao proprio cliente e empresa. A protecao
administrativa acima nao conclui, por si so, a HU08.1.

## Validacao de cliente (MVP)

Telefone conhecido, email cadastrado e o campo `identidadeConfirmada` nao sao,
por si so, uma comprovacao de identidade na conversa atual. O metodo existente
`Cliente.confirmarIdentidade()` apenas altera um indicador e nao valida uma
prova; nao deve ser exposto como mecanismo de autenticacao. O projeto ainda
usa um desafio separado, vinculado ao cliente, empresa e conversa. O acesso ao
email demonstra posse desse endereco, nao comprova por si so o vinculo do
cliente com a empresa. O endereco e o vinculo precisam ter origem confiavel.

No Telegram, `/validar` solicita um codigo; `/codigo 123456` (exemplo ficticio)
o confirma; `/solicitacoes` consulta ate 20 protocolos, tipos e estados do
cliente vinculado a conversa e da mesma empresa. O codigo tem seis digitos,
expira em dez minutos e aceita no maximo cinco tentativas; apos cinco erros nao
e possivel emitir outro ate o prazo terminar. O codigo e usado uma vez e a
autorizacao dura dez minutos. A troca ou revogacao do endereco confiavel invalida
o acesso. O codigo nao e incluido no historico ou passado ao LLM quando enviado
pelo comando ou sozinho. Comandos nao substituem a validacao da empresa.

O envio SMTP usa `SMTP_HOST`, `SMTP_PORT` (587 por padrao), `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_FROM` e `SMTP_AUTH` (true por padrao). STARTTLS e
obrigatorio. Defina segredos apenas no ambiente; sem servidor ou remetente, o
envio falha e nenhuma consulta e liberada. Os testes substituem o transporte e
capturam codigos ficticios, sem enviar emails reais. Exemplo de execucao no
Windows, em `chatbot/`: ` .\mvnw.cmd test`.

A migracao V14 cria `emails_confiaveis_cliente` e `desafios_cliente`; combine
seu numero com a equipe antes de integrar outras frentes. Nenhum endereco de
cliente existente e promovido a confiavel automaticamente. Nao existe ainda um
processo de cadastro/verificacao do email nem de vinculacao segura do contato
Telegram a um cadastro existente: o fluxo atual cria um cliente para contato
novo. Os testes preparam manualmente dados ficticios e vinculos coerentes. Para
demonstracao real, a equipe precisa preparar dados ficticios em ambiente isolado
por um processo controlado, com contato, conversa, cliente e empresa coerentes.
Nao execute preparacao de demonstração no banco de producao.

Ficam pendentes a escolha e configuracao do provedor SMTP, a origem real dos
cadastros confiaveis, a integracao de vinculacao e a verificacao ponta a ponta
com o canal real. Plano contratado e dados de conta nao sao consultados nem
inventados: dependem de fonte definida. WhatsApp ainda nao esta integrado.
Nao declare HU07.3 ou HU08.1 completas ate resolver essas dependencias.
