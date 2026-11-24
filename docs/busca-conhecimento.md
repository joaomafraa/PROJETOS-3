# Busca nos documentos oficiais (HU02.1)

Responsável: Arthur Almeida.

Objetivo: receber uma pergunta e uma empresa já validada, e devolver os trechos oficiais mais relevantes, cada um com a identificação da fonte. A busca **não** chama o LLM, não cadastra documentos e não mexe no Telegram.

## Como funciona

1. Lê os documentos **ativos** da empresa por `DocumentosConhecimentoService.ativosDaEmpresa(empresaId)`, a API do João.
2. Divide cada documento em trechos com `DivisorTrechos`:
   - Um título Markdown (`# Prazos de entrega`) inicia um trecho novo e vira a localização (`Seção: Prazos de entrega`).
   - Parágrafos são agrupados até 800 caracteres. Um parágrafo maior que isso é quebrado por frases.
   - Os IDs são `t1`, `t2`, ... e saem sempre iguais para o mesmo conteúdo. Como toda edição muda a versão do documento, o par versão + trecho identifica o trecho com segurança.
3. Pontua cada trecho com **BM25**, um algoritmo clássico de busca textual:
   - Palavras raras nos documentos valem mais.
   - Repetir uma palavra ajuda, mas cada repetição ajuda menos.
   - Trechos longos não são favorecidos.
   - Acentos, maiúsculas, palavras comuns ("qual", "o", "de"...) e plurais simples são ignorados. Uma palavra também casa com outra que tenha o mesmo início longo, como "trocar" e "troca".
   - O título do documento e a seção contam como parte do trecho.
4. Só aceita trechos que tenham pelo menos 40% dos termos da pergunta e que fiquem acima de 30% da pontuação do melhor trecho.
5. Devolve no máximo **5 trechos** e **4.000 caracteres** no total, do mais relevante para o menos relevante.

Não há índice separado: os trechos são recalculados a partir do documento salvo a cada busca. Por isso, editar ou desativar um documento vale já na próxima busca, sem cache para invalidar.

## Contrato para Guilherme, Malta, Pedro e Hélio

```java
ResultadoBusca resultado = buscaConhecimento.buscar(empresaId, perguntaDoCliente);

resultado.situacao(); // ENCONTRADO, PERGUNTA_SEM_TERMOS, SEM_DOCUMENTOS ou SEM_CORRESPONDENCIA
resultado.trechos();  // TrechoEncontrado(fonte, relevancia), do mais relevante para o menos
resultado.fontes();   // List<FonteDisponivel>, o mesmo tipo usado por Hélio e Pedro
```

- `empresaId` precisa vir do canal validado, como a configuração do bot do Telegram. Nunca use o texto do cliente nem o modelo.
- Quando nada relevante é encontrado, a lista vem **vazia** e a situação diz o motivo. A busca nunca completa o resultado com trechos aleatórios.
- `relevancia` serve só para ordenar. **Não é uma porcentagem de confiança.** `ENCONTRADO` também não prova que os trechos respondem à pergunta. Essa decisão é da política do Malta (`PoliticaInsuficiencia`).
- Cada `FonteDisponivel` traz empresa, ID do documento, versão como texto (`"3"`), título, ID do trecho, seção (ou `null`) e o texto do trecho.

Fluxo esperado na geração (Guilherme):

```java
ResultadoBusca resultado = buscaConhecimento.buscar(empresaId, pergunta);
var contexto = segurancaConhecimento.preparar(empresaId, resultado.fontes(), disponibilidade);
// ... PoliticaInsuficiencia, prompt, provedor ...
segurancaConhecimento.revalidar(contexto, disponibilidade); // dentro da transação que salva
```

`ConsultaTrechoDocumento` é a implementação real do `ConsultaTrechoConhecimento` que o Hélio deixou esperando. Com ela, o `DisponibilidadeFontePersistida` deixa de recusar tudo e passa a confirmar se o trecho existe na versão ativa do documento da empresa. Isso faz a revalidação funcionar: se o documento for editado ou desativado durante a geração, a resposta é descartada.

## Arquivos

| Arquivo | Função |
|---|---|
| `llm/BuscaConhecimentoService.java` | Busca e ordenação |
| `llm/DivisorTrechos.java` | Divisão do documento em trechos |
| `llm/ResultadoBusca.java` | Resultado com situação e trechos |
| `llm/ConsultaTrechoDocumento.java` | Confirma se um trecho ainda existe (para a segurança) |

Não há migração nova nem mudança no `pom.xml`.

## Testes

- `llm/DivisorTrechosTests`: seções, limite de tamanho, quebra de parágrafo grande sem perder palavras, IDs estáveis e quebra de linha do Windows.
- `llm/BuscaConhecimentoServiceTests`: trecho certo em primeiro lugar, acentos e plural, verbo e substantivo, pergunta sem relação, cobertura mínima, saudação, empresa sem documentos, documento inativo, limites de quantidade e tamanho, e entrada inválida.
- `integration/BuscaConhecimentoIntegracaoTests`: com o banco H2, isolamento entre duas empresas, documento desativado fora da busca, passagem pela barreira do Hélio, edição durante a geração descartando a resposta, e consulta do trecho por empresa, versão e ID.

```powershell
cd chatbot
$env:TELEGRAM_POLLING_ENABLED = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=DivisorTrechosTests,BuscaConhecimentoServiceTests,BuscaConhecimentoIntegracaoTests' test
```

## Limites conhecidos

- **Busca por palavras, não por significado.** Sinônimos sem palavra em comum não se encontram, como "valor" e "preço", ou "frete" e "entrega". Uma busca semântica exigiria embeddings, e o grupo decidiu não adicionar serviço pago ou novo provedor.
- **Português simplificado.** A redução de plural cobre os casos comuns (`prazos`, `informações`, `valores`), mas não conjugações completas de verbos.
- **Desempenho.** Cada busca relê e divide todos os documentos ativos da empresa. Para a escala do projeto (poucos documentos de até 200.000 caracteres), isso é rápido. Com muitos documentos grandes, valeria criar um índice persistido.
- **Mudança futura na divisão.** Se a regra do `DivisorTrechos` mudar, os IDs dos trechos mudam. As respostas antigas não são afetadas, porque o registro de fontes do Pedro guarda cópias. Só uma geração em andamento no momento do deploy seria descartada.
- **Integração ainda pendente.** A busca ainda não é chamada pelo `RespostaAutomaticaService`. Ligar a busca à IA é a parte do Guilherme. Até lá, a resposta do Telegram não usa os documentos.
