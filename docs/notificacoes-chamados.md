# Notificação de novos chamados (HU05.3)

Quando um chamado novo é registrado, cada responsável configurado para **novos chamados** na empresa do chamado recebe um aviso no painel (menu **Notificações**).

**Canal desta entrega: o próprio painel.** Não há envio por e-mail nem por Telegram aos atendentes. "Notificação criada" significa "disponível no painel do destinatário", não "mensagem enviada".

## Quem recebe

| Situação | Recebe? |
| --- | --- |
| Responsável por "novos chamados" (`NOVO_TICKET`) da mesma empresa, ativo | Sim, um aviso por chamado |
| Responsável inativo | Não |
| Atendente que não é responsável por novos chamados | Não |
| Responsável só por leads ou por atendimento humano | Não |
| Qualquer pessoa de outra empresa | Não |

Os responsáveis são configurados em **Administração → Responsáveis por notificações → "Nova solicitação aberta"** (HU08.3).

## Como funciona

```text
Cliente abre chamado (Telegram) → AbrirChamadoService salva o ticket
  → publica SolicitacaoRegistradaEvento (mesma transação)
  → commit do chamado
  → NotificacaoChamadosOuvinte (só depois do commit) → NotificacaoChamadosService
       → um aviso por responsável elegível, em transação própria
```

- **O chamado nunca depende da notificação.** Os avisos são gerados depois do commit, em outra transação. Se falharem, o chamado continua salvo e o cliente recebe o protocolo normalmente.
- **Sem duplicatas.** A tabela tem chave única (chamado, destinatário, tipo) e o serviço confere antes de criar. Processar o mesmo chamado de novo não cria outro aviso.
- **Falha ou responsável configurado depois:** o reprocessamento roda a cada 5 minutos e procura chamados das últimas 24 h que ainda não geraram nenhum aviso.
- **Empresa sem responsável:** o chamado é salvo, ninguém é avisado e fica registrado no log (`WARN ... sem responsável ativo ...`).

## Painel

Menu **Notificações**, com contador de não lidas (atualizado a cada 30 s com a página aberta).

| Método | Rota | O que faz |
| --- | --- | --- |
| GET | `/api/notificacoes-chamados?somenteNaoLidas=` | Últimos 50 avisos do atendente logado |
| GET | `/api/notificacoes-chamados/contagem` | `{ "naoLidas": n }` |
| POST | `/api/notificacoes-chamados/{id}/lida` | Marca um aviso como lido |
| POST | `/api/notificacoes-chamados/lidas` | Marca todos como lidos |

- O destinatário é sempre quem está logado; nenhuma rota recebe id de atendente ou de empresa.
- Aviso de outra pessoa ou de outra empresa responde **404**, igual a um id inexistente.
- POST exige token CSRF.

## Contrato para quem cria solicitações

Pedro (HU05.1/HU06.2) e Guilherme (HU06.1): ao salvar um ticket novo, publiquem na mesma transação:

```java
eventos.publishEvent(new SolicitacaoRegistradaEvento(ticket.getId(), empresaId));
```

Publicar de novo para o mesmo ticket (por exemplo, numa operação repetida) não duplica avisos. Se a operação for idempotente e devolver um ticket já existente, não é preciso publicar.

O reprocessamento cobre qualquer ticket novo das últimas 24 h, mesmo que o evento não tenha sido publicado.

## Configuração

| Propriedade | Padrão | Uso |
| --- | --- | --- |
| `nexus.notificacoes.reprocessamento.enabled` | `true` | Liga o reprocessamento agendado (desligado no perfil `test`) |
| `nexus.notificacoes.reprocessamento.intervalo-ms` | `300000` | Intervalo entre execuções |
| `nexus.notificacoes.reprocessamento.atraso-inicial-ms` | `60000` | Espera após iniciar a aplicação |

Migração: `V23__notificacoes_chamados.sql` (só cria a tabela e a sequência).

## Limites

- Sem envio externo (e-mail/Telegram) aos atendentes. Para isso seria preciso definir com a equipe o canal, os endereços e a confirmação de entrega.
- O aviso aparece quando o atendente está com o painel aberto (contador a cada 30 s) ou ao abrir a tela; não há push em tempo real.
- O reprocessamento só olha as últimas 24 h; um responsável configurado depois disso não recebe avisos de chamados antigos.
- Tipos de pedido de Pedro e Guilherme (orçamento, demonstração, reunião, visita) geram aviso quando o evento for publicado ou pelo reprocessamento.

## Testes

- `model/NotificacaoChamadoTests`: mesma empresa, destinatário inativo, leitura só pelo destinatário.
- `integration/NotificacaoChamadosTests`: fluxo real com commit, quem recebe, isolamento, repetição, empresa sem responsável e reprocessamento.
- `integration/FalhaNotificacaoChamadoTests`: falha na notificação não desfaz o chamado.
- `integration/NotificacoesChamadosApiTests`: login, CSRF, cada um só vê e marca os próprios avisos, isolamento entre empresas.
