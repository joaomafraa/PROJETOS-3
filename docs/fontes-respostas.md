# Fontes das respostas automáticas (HU02.2)

Responsável: Pedro Martins.

Objetivo: o atendente consegue ver de onde veio cada resposta automática e o cliente recebe, no Telegram, um texto legível com a fonte indicada.

## Situação

| Parte | Situação |
|---|---|
| Registro das fontes no banco (migração V18) | Pronto e testado |
| Validação das referências citadas pela IA | Pronto e testado |
| Consulta das fontes no painel (`GET /api/historico/{conversaId}/fontes`) | Pronto e testado |
| Exibição das fontes na conversa e nos detalhes da solicitação | Pronto |
| Texto e lista de fontes no Telegram, dentro do limite de 4096 caracteres | Pronto e testado |
| Geração da resposta usando trechos oficiais | **Depende de Guilherme e Artur** |

Enquanto a geração fundamentada não chamar o registro, as respostas automáticas continuam funcionando e aparecem no painel como "Sem fontes registradas". O formatador do Telegram já está ativo e só organiza o texto e respeita o limite do canal.

## Contrato

### 1. Trecho entregue à IA — `llm/FonteDisponivel`

A busca nos documentos (Artur) produz os trechos; a geração (Guilherme) converte para:

```java
new FonteDisponivel(empresaId, documentoId, documentoVersao, documentoTitulo,
        trechoId, localizacao /* pode ser null */, texto);
```

- `empresaId` deve ser a mesma empresa da conversa; caso contrário o registro é recusado.
- `documentoId + documentoVersao + trechoId` identifica o trecho.
- `localizacao` é a seção ou página, quando houver. Ela aparece para o cliente; não colocar caminhos de arquivo ou URLs internas.

### 2. Rótulos no prompt e citações — `llm/ReferenciasResposta`

```java
List<FonteDisponivel> fornecidas = ReferenciasResposta.numerar(trechosDaBusca); // remove repetidos
// No prompt, o trecho fornecidas.get(i) recebe o rótulo ReferenciasResposta.rotulo(i): F1, F2, ...
// Instrução à IA: citar a fonte com o rótulo entre colchetes, por exemplo [F1] ou [F1, F2].
```

Depois da resposta do provedor:

```java
ReferenciasResposta.Resultado resultado = ReferenciasResposta.extrair(textoDaIa, fornecidas);
resultado.texto();                // texto a salvar: [F2] vira [1] na ordem de citação
resultado.citadas();              // somente trechos realmente fornecidos
resultado.referenciasInvalidas(); // quantos rótulos inventados foram descartados
```

Rótulos inexistentes (por exemplo `[F9]` quando só havia dois trechos) são removidos do texto e não são registrados.

### 3. Registro junto com a resposta — `service/FontesRespostaService`

Na mesma transação em que a resposta é salva (em `RespostaAutomaticaService`, depois de revalidar a conversa):

```java
conversa.adicionarMensagem(mensagemId, resultado.texto(), Mensagem.TipoRemetente.CHATBOT);
fontesRespostaService.registrar(conversa, mensagemId, fornecidas, resultado.citadas());
```

- Exige transação ativa: se a resposta for descartada, as fontes também são.
- Recusa: mensagem que não é do chatbot, citação de trecho não fornecido, fonte de outra empresa, mais de 20 trechos e registro repetido.
- Guarda uma cópia do título, da versão, da localização e de até 600 caracteres do trecho. Desativar ou atualizar o documento não apaga a origem das respostas antigas.

### 4. Tipos de fonte

- **CITADA**: o trecho foi referenciado na resposta com `[n]`.
- **RECUPERADA**: o trecho foi entregue à IA como contexto. Isso não prova que ele sustenta cada frase; o painel mostra esses trechos como "Também consultadas pela IA (não necessariamente usadas)".

## Painel

`GET /api/historico/{conversaId}/fontes` exige login. A empresa vem do atendente autenticado: conversa de outra empresa responde 404 e atendente inativo responde 403.

Em cada resposta do chatbot, o painel mostra "Fontes: N citadas", com título, seção, versão e o trecho. Isso aparece na Caixa de entrada e no histórico dos detalhes da solicitação (`fontes.js`).

## Telegram

`telegram/FormatadorRespostaTelegram` é aplicado em `RespostaTelegramService`:

```
O prazo é de 5 dias úteis [1].

Fontes:
[1] Manual de entregas — Seção 3
```

- Mostra apenas o título e a seção, sem ids, arquivos ou links.
- Lista no máximo 5 fontes e informa quantas outras existem.
- Se o texto passar de 4096 caracteres, corta o corpo, nunca a lista de fontes, sem partir uma referência `[n]` nem um emoji.

## Testes

- `ReferenciasRespostaTests`: renumeração, referências inventadas, grupos `[F1, F3]`, trechos repetidos.
- `FormatadorRespostaTelegramTests`: lista de fontes, limite de 4096, cortes seguros.
- `FontesRespostaServiceTests` (H2): registro com a resposta, rejeições, isolamento entre empresas, resposta antiga sem fontes, cópia da versão e mensagem enviada ao Telegram.
- `FontesRespostaControllerTests` e `SecurityHttpTests`: 200, 403, 404, 401 sem login e `fontes.js` protegido.
