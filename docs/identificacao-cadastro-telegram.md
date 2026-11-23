# Identificação e cadastro no início do atendimento (HU07.1 e HU07.2)

Responsável: Arthur Almeida. Canal atual: Telegram.

## O que acontece no chat

1. Na primeira mensagem, o bot pede o telefone com o botão **"Compartilhar meu telefone"** (`request_contact` do Telegram). A IA só responde depois da identificação.
2. **Número já cadastrado na empresa do bot (HU07.1):** o contato e a conversa passam a apontar para o cliente existente. O bot responde que localizou um cadastro e orienta o uso de `/validar` para dados restritos. Não mostra nome, e-mail nem outros dados do cadastro.
3. **Número desconhecido (HU07.2):** o bot pede o nome completo e depois a empresa que a pessoa representa (`/pular` se não representar nenhuma). O cadastro é concluído por `IdentificacaoClienteService.identificarOuCadastrar`, que já trata normalização, duplicidade e concorrência.
4. Depois disso a conversa segue para a IA normalmente.

Os comandos `/atendente`, `/validar`, `/codigo` e `/solicitacoes` continuam funcionando em qualquer etapa. Enquanto a conversa estiver com atendente, o bot não pede telefone. A identificação recomeça quando a conversa volta ao atendimento automático.

## Contrato com o canal (combinar com João)

- **Empresa e chatbot** vêm da configuração do bot (`TELEGRAM_EMPRESA_ID`, `TELEGRAM_CHATBOT_ID`), nunca do texto do chat.
- **Telefone:** só é aceito o contato da **própria conta**, ou seja, `message.contact.user_id` igual ao remetente. Ele chega em `MensagemTelegram.telefone`, no formato internacional (`+55...`).
- Um número **digitado** ou o **contato de outra pessoa** nunca localiza nem cadastra ninguém. O bot pede de novo o botão.
- O número compartilhado não entra no histórico nem no contexto da IA. A mensagem é gravada como `[contato compartilhado]`.
- **Telefone reconhecido não comprova identidade:** `identidadeConfirmada` continua `false`. A validação adicional é a HU07.3 (Gabriel Malta).

## Onde está no código

| Parte | Arquivo |
|---|---|
| Etapas da identificação e do cadastro | `telegram/CadastroTelegramService.java` |
| Leitura do contato compartilhado | `telegram/MensagemTelegram.java` |
| Botão de telefone e remoção do teclado | `telegram/TelegramCliente.java` (`Teclado`) |
| Chamada antes da IA | `telegram/ProcessarMensagemTelegramService.java` |
| Troca do cliente provisório pelo identificado | `model/Conversa.identificarCliente` |
| Etapa e telefone pendente por contato | `db/migration/V15__cadastro_telegram.sql` |

A etapa fica em `telegram_contatos.etapa_cadastro`, com os valores `AGUARDANDO_TELEFONE`, `AGUARDANDO_NOME`, `AGUARDANDO_EMPRESA` e `CONCLUIDO`. A resposta de cada etapa é gravada no histórico e preparada na fila de envio na mesma transação. Se a mesma atualização do Telegram for processada de novo, a etapa não é aplicada duas vezes.

## Testes

- `integration/CadastroTelegramTests`: cobre número conhecido, número desconhecido com nome e empresa, `/pular`, número digitado, contato de outra pessoa, telefone de outra empresa, `/atendente` durante o cadastro e reprocessamento da mesma atualização.
- `telegram/MensagemTelegramTests`, `telegram/TelegramClienteTests`, `model/ConversaTests` e `telegram/ProcessarMensagemTelegramServiceTests`: cobrem as partes isoladas.

Para rodar, na pasta `chatbot`: `.\mvnw.cmd test` (Windows) ou `./mvnw test`.

## Limites e dependências

- **Migração V15:** confira com o grupo antes do merge se ninguém mais usou o número 15.
- **Contatos antigos:** os que ainda não têm telefone passam a receber o pedido de telefone na próxima mensagem. Os que já têm telefone ficam como `CONCLUIDO`.
- **Cliente provisório:** o cliente criado sem telefone no primeiro contato continua no banco depois da identificação. Conversas encerradas antes dela continuam ligadas a ele. Não há limpeza automática.
- **Formato do telefone:** a busca usa o formato internacional (`+5581...`). Um cliente cadastrado no painel em formato local (`81...`) não é encontrado pelo Telegram. Nesse caso, a pessoa segue pelo fluxo de cadastro novo.
- **Mensagens:** são somente texto. O e-mail não é pedido no chat, porque o e-mail usado na validação vem de processo confiável (HU07.3).
- **Leads:** o interesse ou serviço procurado não é registrado como lead aqui. Isso é a HU09.1 (Gabriel Mendes).
- **WhatsApp:** não está integrado. O fluxo acima vale para o Telegram, que é o canal configurado hoje.
