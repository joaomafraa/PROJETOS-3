# Configuração do atendimento (HU01.3)

Cada empresa define quando a equipe humana atende, quais assuntos a IA atende sozinha e a mensagem para quem pede um atendente fora do expediente.

**O horário vale só para a equipe humana. A IA continua respondendo fora dele.**

## O que existe

| Parte | Onde |
| --- | --- |
| Entidade e regras de horário | `model/ConfiguracaoAtendimento.java`, `model/IntervaloAtendimento.java` |
| Tabelas | `db/migration/V20__configuracao_atendimento.sql` (`configuracoes_atendimento`, `horarios_atendimento`, `assuntos_atendimento_automatico`) |
| Administração (ler e salvar) | `service/ConfiguracaoAtendimentoService.java` |
| Consulta para triagem, geração e Telegram | `service/DisponibilidadeAtendimentoService.java` + `dto/DisponibilidadeAtendimento.java` |
| API | `web/ConfiguracaoAtendimentoController.java` |
| Tela | Administração → cartão **Configuração do atendimento** (`painel.html`, `configuracao-atendimento.js`, fim do `painel.css`) |
| Integração com o Telegram | `telegram/TransferenciaTelegramService.java` (resposta ao `/atendente`) |

## Regras

- Uma configuração por empresa. A empresa é sempre a do administrador logado.
- Empresa sem configuração funciona exatamente como antes.
- Fuso no formato Região/Cidade (ex.: `America/Recife`). `UTC` ou `-03:00` fixos são recusados.
- Cada período tem dia da semana, abertura e fechamento. **A abertura entra, o fechamento não**: 08:00–18:00 vai até 17:59.
- Pode haver mais de um período por dia (ex.: pausa para almoço), sem sobreposição. A tela edita um período por dia e preserva os períodos extras.
- Períodos que atravessam a meia-noite não são aceitos; cadastre dois períodos.
- Configuração salva **sem nenhum dia marcado** = equipe humana sempre fora do expediente.
- Até 20 assuntos de até 80 caracteres; linhas vazias e repetidos (maiúsculas/minúsculas) são ignorados. Lista vazia = sem restrição.
- Mensagem fora do expediente: até 500 caracteres; em branco usa o texto padrão.
- Se algum dado for inválido, nada é salvo.

## API

Exige perfil `ADMINISTRADOR` (regra `/api/admin/**`) e token CSRF no PUT.

| Método | Rota | O que faz |
| --- | --- | --- |
| GET | `/api/admin/configuracao-atendimento` | Configuração atual (ou valores padrão com `configurada: false`) |
| PUT | `/api/admin/configuracao-atendimento` | Substitui a configuração inteira |

Corpo do PUT:

```json
{
  "fuso": "America/Recife",
  "mensagemForaExpediente": "Atendemos de segunda a sexta, das 8h às 18h.",
  "horarios": [
    { "diaSemana": "MONDAY", "abertura": "08:00", "fechamento": "18:00" }
  ],
  "assuntosAutomaticos": ["Boletos", "Horário de funcionamento"]
}
```

Erros voltam como `{"mensagem": "..."}`: 400 (dados inválidos), 401 (sem login), 403 (sem permissão ou sem CSRF).

## Comportamento no Telegram

Quando o cliente envia `/atendente`:

| Situação | Resposta | Fila humana |
| --- | --- | --- |
| Empresa sem configuração | Mensagem de sempre ("Aguarde um atendente.") | Entra na fila |
| Dentro do expediente | Mensagem de sempre | Entra na fila |
| Fora do expediente | Mensagem configurada + aviso de que ficou na fila + quando o atendimento volta | Entra na fila |

Exemplo fora do expediente (terça, 19:00):

```text
Nossa equipe atende de segunda a sexta, das 8h às 18h.
Sua solicitação ficou registrada na fila de atendimento humano. Um atendente poderá responder a partir de amanhã às 08:00.
Você pode continuar escrevendo: suas mensagens ficam guardadas para o atendente.
```

A conversa vai para `AGUARDANDO_ATENDENTE` normalmente; nada promete resposta imediata.

## Contrato para as outras frentes

`DisponibilidadeAtendimentoService`:

| Método | Para quem | Retorno |
| --- | --- | --- |
| `consultar(empresaId)` / `consultar(empresaId, momento)` | Triagem (Gabriel Mendes) | `DisponibilidadeAtendimento`: `configurada`, `equipeHumanaDisponivel`, `fuso`, `proximaAbertura`, `assuntosAutomaticos`, `mensagemForaExpediente` e `assuntoAtendidoAutomaticamente(assunto)` |
| `contextoParaIa(empresaId, momento)` | Geração (Guilherme) | Texto curto com expediente e assuntos, para incluir no contexto do LLM como dado da empresa |
| `mensagemSolicitacaoHumana(empresaId, momento)` | Telegram (já usado) | Texto fora do expediente, ou vazio para manter a confirmação padrão |

O `empresaId` deve vir do canal validado ou da sessão, nunca do cliente.

## Pendências e limites

- `contextoParaIa` ainda **não** é enviado ao LLM: incluir no prompt depende da evolução do `ProvedorLlm`/`RespostaAutomaticaService`, que é da frente de geração (Guilherme). Até lá, a IA não sabe o horário nem os assuntos configurados.
- O uso dos assuntos na decisão de responder ou encaminhar depende da triagem (Gabriel Mendes).
- Feriados não são tratados.
- Disponibilidade 24h da IA depende também da hospedagem (plano do Render); a configuração não garante isso.
- Não há envio de aviso a atendentes fora do expediente (notificações não existem no projeto).

## Testes

- `model/ConfiguracaoAtendimentoTests` — horário, limites, fuso, próxima abertura, validações.
- `service/DisponibilidadeAtendimentoServiceTests` — mensagens dentro/fora, contexto da IA, isolamento.
- `telegram/TransferenciaForaExpedienteTests` — `/atendente` fora do expediente entra na fila e avisa o cliente.
- `integration/ConfiguracaoAtendimentoApiTests` — login, perfil, CSRF, validação, persistência e isolamento pela API.
