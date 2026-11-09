# API de visualização da fila

Responsável pela entrega: Arthur Almeida.

## Consulta

```http
GET /api/historico/fila
Accept: application/json
```

A rota existe somente com o perfil `painel-local`. O controlador chama
`FilaConversasService.visualizarFila(Atendente)`, que consulta o banco e aplica
as regras de empresa e status. Não há paginação nesta entrega.

O atendente vem da configuração do servidor `PAINEL_ATENDENTE_ID` (padrão: `1`).
Ele deve existir e estar ativo. A empresa é obtida desse atendente; parâmetros
como `empresaId`, `atendenteId` e `status` na URL não alteram a consulta.
Esse mecanismo é exclusivo da demonstração local e não implementa login.

## Resposta de sucesso — HTTP 200

O corpo é um array JSON de `ConversaResumo`, sem envelope. São incluídas somente
conversas com status `AGUARDANDO_ATENDENTE` da empresa do atendente. A ordenação
é por `dataInicio` crescente e, em caso de empate, por `id` crescente. A data
representa o início da conversa, não o momento em que ela entrou na fila.

Exemplo ilustrativo do contrato:

```json
[
  {
    "id": 42,
    "clienteId": 7,
    "clienteNome": "Marina",
    "numeroTelefone": "+5581999999999",
    "chatbotNome": "Assistente WXN",
    "dataInicio": "2026-10-01T12:00:00Z",
    "status": "AGUARDANDO_ATENDENTE"
  }
]
```

| Campo | Tipo JSON | Uso no frontend |
| --- | --- | --- |
| `id` | número inteiro | Identifica a conversa e permite abrir seu histórico. |
| `clienteId` | número inteiro | Identifica o cliente persistido. |
| `clienteNome` | string ou `null` | Nome do cliente; pode não estar cadastrado. |
| `numeroTelefone` | string | Telefone cadastrado do cliente. |
| `chatbotNome` | string | Nome do chatbot vinculado à conversa. |
| `dataInicio` | string | Instante em ISO 8601/UTC, com sufixo `Z`; formatar para exibição no frontend. |
| `status` | string | Nesta rota, sempre `AGUARDANDO_ATENDENTE`. |

Quando não houver conversas elegíveis, retorna **HTTP 200 com `[]`**.
Não retorna 404 nem 204 para fila vazia.

O DTO não fornece protocolo, resumo, responsável ou mensagens. Campos ausentes
devem ser apresentados como “Não disponível”, quando aparecerem no painel.
O histórico é consultado separadamente em
`GET /api/historico/{id}/mensagens`, usando o `id` da conversa selecionada.
`GET /api/historico` é uma consulta geral de compatibilidade e não substitui a fila.

## Erros

| Código HTTP | Significado | Tratamento pelo frontend |
| --- | --- | --- |
| `403` | Atendente configurado inexistente ou inativo, ou acesso recusado pelo serviço. | Informar que o painel requer um atendente ativo configurado. |
| `404` | Rota indisponível, por exemplo quando `painel-local` não está ativo. | Conferir o perfil de execução. |
| `500` | Falha inesperada na consulta, incluindo falhas de persistência. | Mostrar erro e permitir nova tentativa; não apresentar como fila vazia. |

O frontend deve usar o código HTTP para identificar o erro. O formato do corpo
de erro segue o tratamento padrão do Spring Boot e não faz parte do contrato
de `ConversaResumo`. Não dependa da presença de `message` nem exiba detalhes
internos de exceções. A recusa de acesso do serviço é traduzida para 403 com
uma descrição genérica.

## Verificação local

Com o painel iniciado conforme o README e um atendente ativo configurado:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/historico/fila'
```

Para executar os testes específicos da API, dentro de `chatbot`:

```powershell
.\mvnw.cmd '-Dtest=FilaApiTests,FilaControllerTests' test
```

Os testes usam H2 e MockMvc para validar o contrato JSON, dados persistidos,
fila vazia, exclusão dos demais status, isolamento entre empresas, impossibilidade
de escolher outro atendente pela URL, atendente inexistente/inativo, nome ausente,
delegação ao serviço, preservação da ordem retornada e tradução da recusa de acesso.

Para executar toda a suíte:

```powershell
.\mvnw.cmd test
```
