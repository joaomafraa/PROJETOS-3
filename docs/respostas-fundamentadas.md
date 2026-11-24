# Respostas fundamentadas nos documentos (HU02.1 e HU02.2)

Responsável: Guilherme Guimarães.

## O que acontece quando o cliente pergunta no Telegram

1. A mensagem passa pelo fluxo que já existia: identificação, cadastro, triagem e comandos.
2. `RespostaAutomaticaService` pega a última pergunta do cliente e procura trechos com a busca do Artur (`BuscaConhecimentoService`).
3. Os trechos passam pela barreira do Hélio (`SegurancaContextoConhecimento.preparar`). Só seguem trechos da empresa da conversa, de documentos ativos e na versão atual.
4. A política do Malta (`PoliticaInsuficiencia`) decide o que fazer:
   - **Saudação sem pergunta** (ex.: "oi"): pede esclarecimento.
   - **Nenhum trecho**: informa a limitação e oferece `/atendente`.
   - **Trechos que cobrem menos de 60% dos termos da pergunta**: informa que as informações não bastam.
   - **Caso contrário**: segue para a IA.

   Nos três primeiros casos a IA não é chamada e nenhum fato é inventado.
5. A Groq recebe a montagem abaixo, em uma única chamada:
   - **Sistema:** só as instruções. São as regras de atendimento, a `INSTRUCAO_GERACAO` do Malta e o pedido para citar `[F1]`, `[F2]`...
   - **Usuário:** documentos e mensagens do cliente, sempre como dados. Os trechos vêm entre `<<< >>>`, junto da última mensagem do cliente.
   - **Limites:** últimas 10 mensagens da conversa, até 1.000 caracteres cada, no máximo 5 trechos e 4.000 caracteres de documentos (vindos da busca), e resposta limitada por `GROQ_MAX_TOKENS` (padrão 1500).
6. As citações são conferidas por `ReferenciasResposta` (Pedro). `[F9]` inventado é removido, e `[F1]` vira `[1]`.
7. Na transação que salva, a conversa e os documentos são conferidos de novo:
   - **Se um atendente assumiu ou a conversa mudou:** a resposta é descartada.
   - **Se um documento foi editado ou desativado:** a resposta também é descartada (`ContextoConhecimentoObsoletoException`).
   - **Se nada mudou:** mensagem e fontes são salvas juntas (`FontesRespostaService.registrar`).
8. O Telegram envia o texto com a lista "Fontes", pelo formatador que o Pedro já tinha feito.

## Arquivos alterados

| Arquivo | Mudança |
|---|---|
| `llm/RespostaAutomaticaService.java` | Busca, segurança, política, citações, revalidação e registro de fontes |
| `llm/ProvedorLlm.java` | Novo `responder(historico, fontes)`; o antigo continua |
| `llm/GroqProvedor.java` | Montagem do prompt com papéis separados e limites |
| `application.properties` | `spring.ai.openai.chat.max-tokens=${GROQ_MAX_TOKENS:1500}` |

Não há migração nova nem dependência nova.

## Testes

- `integration/RespostaFundamentadaTests`: cobre pergunta coberta (com fontes salvas e enviadas ao Telegram), pergunta sem fonte, saudação, cobertura insuficiente, referência inventada, documento desativado e assunção humana durante a geração, resposta vazia e conversa na fila humana.
- `llm/GroqProvedorTests`: confere papéis do prompt, documento malicioso fora das instruções, limite de histórico e de tamanho.
- `service/AtendimentoHumanoServiceTests`: adaptado ao novo construtor.

```powershell
cd chatbot
$env:TELEGRAM_POLLING_ENABLED = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=RespostaFundamentadaTests,GroqProvedorTests,AtendimentoHumanoServiceTests' test
```

## Limites

- Os testes usam um provedor simulado. Ainda falta testar com a Groq real e o bot de teste:
  - pergunta coberta por documento ativo;
  - pergunta sem cobertura;
  - pergunta vaga;
  - `/atendente`.
- A instrução contra injeção é uma defesa, não uma garantia. O que os testes comprovam é que os documentos nunca entram como instrução de sistema.
- Se a Groq começar a devolver respostas vazias, o motivo pode ser o modelo de raciocínio gastando o limite de tokens. Nesse caso, aumente `GROQ_MAX_TOKENS` no Render.
- A regra de 60% de cobertura é por palavras. Uma pergunta com sinônimos que não estão no documento cai em "informação insuficiente".
