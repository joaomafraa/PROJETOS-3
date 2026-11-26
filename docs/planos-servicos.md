# Consulta de planos e serviços (HU04.1)

Responsável: Arthur Almeida.

O cliente envia `/planos` no Telegram, ou toca no botão do menu, e recebe os planos e serviços ligados a ele, sempre marcados como **dados de demonstração**. Os dados são fictícios e ficam no próprio NEXUS. Não há integração com sistema externo.

## Regras

- A consulta só funciona com a identidade validada (`/validar` + `/codigo`). Ela usa `ValidacaoClienteService.exigirContextoValidado`, a fronteira do Hélio, e o cliente sai sempre da validação, nunca da mensagem.
- O repositório filtra por empresa **e** cliente ao mesmo tempo. Outro cliente da mesma empresa, cliente de outra empresa e conversa forjada não veem nada.
- A tabela só aceita registros fictícios: `demonstracao = TRUE` é obrigatório no banco.
- A consulta tem quatro resultados, como no contrato proposto pelo Malta:

| Resultado | Resposta no Telegram |
|---|---|
| `ENCONTRADO` | Lista com nome, tipo, situação, detalhe e vigência (até 20 itens) |
| `SEM_DADOS` | "Não há plano ou serviço disponível..." e oferece `/atendente` |
| `VALIDACAO_NECESSARIA` | Orienta `/validar` |
| `INDISPONIVEL` | Mensagem segura com `/atendente`. A falha nunca vira lista vazia e não mostra detalhes técnicos |

## Arquivos

| Arquivo | Função |
|---|---|
| `model/ServicoContratado.java` | Entidade (plano ou serviço, situação e vigência) |
| `repository/ServicoContratadoRepository.java` | Consulta por empresa + cliente |
| `service/ConsultaServicosContratadosService.java` | Validação, consulta e os 4 resultados |
| `telegram/ConsultaServicosTelegramService.java` | Texto da resposta |
| `telegram/ProcessarMensagemTelegramService.java` | Comando `/planos` e texto do `/menu` |
| `telegram/TelegramCliente.java` | Botão `/planos` no menu |
| `db/migration/V24__servicos_contratados.sql` | Tabela nova; não insere dados |
| `docs/planos-demonstracao.sql` | Dados fictícios, para inserir manualmente em um cliente de teste |

## Dados de demonstração

A migração não insere nenhum dado. Para demonstrar, rode `docs/planos-demonstracao.sql` em um banco de teste. Antes, troque `:empresa_id` e `:cliente_id` pelos IDs de um cliente de teste combinado com o grupo. O script insere um plano ativo, um serviço ativo e um serviço encerrado. No fim do arquivo há um comando para apagar esses dados.

## Testes

- `integration/ConsultaServicosContratadosTests`: cobre consulta sem validação, cliente validado vendo só os próprios dados, o texto no Telegram, outro cliente da mesma empresa, validação de uma conversa não liberando outra, conversa de outra empresa e registro marcado como real recusado pelo banco.
- `service/ConsultaServicosContratadosServiceTests`: cobre cliente vindo da validação, falha do banco virando `INDISPONIVEL`, erro na validação, limite de itens e regras da entidade.
- `telegram/ProcessarMensagemTelegramServiceTests`: confere que `/planos` não passa pela IA nem pelo cadastro.

```powershell
cd chatbot
$env:TELEGRAM_POLLING_ENABLED = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=ConsultaServicosContratadosTests,ConsultaServicosContratadosServiceTests,ProcessarMensagemTelegramServiceTests' test
```

## Limites e dependências

- **Migração V24:** combinar o número com o grupo. Hoje a `main` tem duas migrações V19, e uma delas precisa ser renumerada. Se ela virar V24, esta precisa mudar de número.
- **Vínculo empresa-cliente:** é garantido pela entidade (a empresa vem do cliente) e pela consulta. A chave composta `(cliente_id, empresa_id)` depende da migração de integridade do Hélio. Ela pode ser adicionada depois que a numeração for resolvida.
- **Cadastro de planos:** não há tela para cadastrar planos. Os dados de demonstração entram pelo script.
- **Validação real:** depende do e-mail confiável e do envio de código configurados no ambiente, que são parte existente do projeto.
