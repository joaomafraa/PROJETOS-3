# Segurança da base de conhecimento (HU02.1)

## O que esta entrega protege

`SegurancaContextoConhecimento` é a barreira entre a busca de documentos e a
montagem do prompt. Ela aplica três verificações:

1. todo trecho precisa declarar a mesma empresa validada da conversa;
2. documento, versão e trecho precisam continuar ativos na consulta
   autoritativa antes de serem enviados à IA;
3. as mesmas identidades são consultadas novamente dentro da transação que
   salvará a resposta. Se algo mudou ou foi desativado durante a geração, a
   resposta deve ser descartada.

O contexto devolvido é imutável. Um resultado que misture empresas gera
`SecurityException` e nenhum trecho é liberado. Fonte inativa, versão antiga ou
trecho removido é excluído. A revalidação concorrente gera
`ContextoConhecimentoObsoletoException`.

O registro histórico também valida novamente o `empresaId` de todas as fontes.
A consulta que detecta registro repetido agora inclui `empresaId`, assim como as
demais consultas de fontes. O registro histórico continua existindo depois da
desativação; ele é evidência de uma resposta passada, não conteúdo disponível
para uma resposta nova.

## Contrato para João, Artur e Guilherme

A implementação dos documentos deve fornecer um adaptador para
`DisponibilidadeFonteConhecimento`. A consulta deve usar simultaneamente:

- `empresaId` da conversa/canal validado;
- `documentoId`;
- `documentoVersao`;
- `trechoId`;
- estado ativo/processado do documento e do índice.

O adaptador não pode aceitar um `empresaId` livre do navegador nem procurar o
documento apenas por ID. Se houver cache ou índice, a chave deve começar pela
empresa e a desativação/alteração precisa invalidar os registros correspondentes.

Fluxo de integração esperado em `RespostaAutomaticaService`:

```java
var contexto = segurancaConhecimento.preparar(empresaId, resultadoBusca.trechos(), disponibilidade);
var resultado = provedor.responder(historico, contexto.fontes());

// Dentro da transação, depois de bloquear e revalidar a conversa:
segurancaConhecimento.revalidar(contexto, disponibilidade);
// salvar mensagem e fontes na mesma transação
```

Não foi criado adaptador de banco nesta branch porque o modelo de documentos e a
busca ainda não estão presentes. A integração final não deve usar um adaptador
que sempre devolva `true`; os testes podem usar substitutos, mas produção precisa
consultar o estado persistido criado nas partes de João/Artur.

## Testes

`SegurancaContextoConhecimentoTests` captura a lista que seria entregue ao
provedor e comprova que ela não contém fonte inativa, antiga ou de outra empresa.
Também cobre desativação durante a geração, duplicatas, imutabilidade e entrada
inválida. `FontesRespostaServiceTests`, `FontesRespostaControllerTests` e
`SecurityHttpTests` preservam o isolamento e as permissões do registro/painel.

Para executar a cobertura relacionada:

```powershell
cd chatbot
$env:TELEGRAM_POLLING_ENABLED = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=SegurancaContextoConhecimentoTests,FontesRespostaServiceTests,FontesRespostaControllerTests,SecurityHttpTests' test
```
