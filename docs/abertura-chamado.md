# Abertura de chamados

`AbrirChamadoService` cria um ticket de suporte a partir de uma conversa já persistida. A empresa e o cliente do ticket vêm da conversa; o chamador não envia esses relacionamentos separadamente.

## Contrato do serviço

```java
TicketAberto abrir(Long empresaId, Long conversaId, String descricao)
```

O `empresaId` deve vir do contexto autenticado ou de um canal já validado. O serviço não expõe uma rota HTTP nesta etapa porque o projeto ainda não possui esse contexto em produção.

A abertura segue estas regras:

- a conversa deve existir na empresa informada;
- o cliente, o chatbot e o responsável da conversa, quando houver, devem pertencer à mesma empresa;
- o ticket é criado com o tipo `SUPORTE` e o status `PENDENTE`;
- a sequência `tickets_id_seq` gera o ID sem usar contagem de linhas ou `max(id) + 1`;
- o protocolo usa o formato `CH-` seguido por 20 dígitos e possui restrição única no banco.

O retorno `TicketAberto` contém ID, protocolo, descrição, tipo, status, data de abertura e os IDs da empresa, do cliente e da conversa.

## Migração

`V5__protocolo_unico_tickets.sql` cria a sequência usada por novos chamados e adiciona a restrição `uk_tickets_protocolo`.

Se o banco já contiver protocolos repetidos, a migração falha. Os dados precisam ser revisados antes da aplicação em um ambiente compartilhado.

## Testes

Na pasta `chatbot`, execute:

```powershell
.\mvnw.cmd '-Dtest=AbrirChamadoServiceTests,IsolamentoEmpresaTests' test
```

Os testes cobrem persistência, retorno dos dados, geração de protocolos distintos, conversa de outra empresa, cliente sem vínculo e tentativas de consulta ou alteração entre duas empresas.

Para rodar a suíte completa:

```powershell
.\mvnw.cmd test
```

## Dependências restantes

A abertura pelo WhatsApp depende da integração do canal. Uma API pública também depende do contexto de autenticação para obter a empresa sem confiar em um identificador livre enviado pela requisição.
