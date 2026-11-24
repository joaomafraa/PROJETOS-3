# Atendimento Telegram no NEXUS

Envie `/menu` para abrir o teclado de opções: suporte, comercial, atendente, validação de identidade e consulta de solicitações. Durante o cadastro, o menu também mantém o botão para compartilhar o próprio telefone. Na verificação, o código deve ser digitado com `/codigo` seguido do código; não é guardado no histórico. A consulta de solicitações não cria novos chamados.

O assistente se apresenta como NEXUS e responde em português, com empatia e objetividade, sem fingir ser humano. Respostas são enviadas como texto simples, sem depender de Markdown; fontes citadas continuam indicadas no rodapé.

O bot recebe mensagens privadas por polling. O processamento só deve ser ativado em **uma instância** por token: não execute o bot local e no Render ao mesmo tempo. A aplicação deve usar HTTPS para suas páginas; o polling não precisa de URL de webhook.

## Configuração

Defina no ambiente, sem registrar valores reais no Git:

- `TELEGRAM_BOT_TOKEN`: token do BotFather.
- `TELEGRAM_POLLING_ENABLED`: `true` para ativar o recebimento, `false` para desativar.
- `TELEGRAM_EMPRESA_ID`: identificador da empresa dona do bot.
- `TELEGRAM_CHATBOT_ID`: identificador do chatbot ativo da mesma empresa.
- `GROQ_API_KEY` e `GROQ_MODEL`: chave da Groq e modelo habilitado para a conta.
- `DB_URL`, `DB_USER`, `DB_PASSWORD`: banco PostgreSQL. Verifique o ambiente e as migrações Flyway antes de publicar.

No ambiente de demonstração usado localmente, empresa e chatbot têm ID `900000001`. Confirme os valores para outros ambientes; não copie IDs de demonstração automaticamente para dados de outra empresa.

## Fluxo e limites

Mensagens privadas são identificadas pelo ID do Telegram e armazenadas por empresa. Antes da IA, o contato compartilha o próprio telefone pelo botão do bot para ser localizado ou cadastrado (HU07.1/HU07.2, ver `identificacao-cadastro-telegram.md`); depois disso as mensagens são encaminhadas para a IA. `/atendente` encaminha à fila e interrompe respostas automáticas. O atendente responsável pode assumir e responder pelo painel.

Mensagens sem confirmação de entrega são marcadas como `INCERTO`; a aplicação não as reenvia automaticamente porque uma resposta pode ter chegado antes de uma falha de rede. Confira a conversa no Telegram antes de tomar qualquer ação. O atendente responsável pode confirmar manualmente a entrega ou pedir um reenvio após verificar a situação. Uma interrupção durante o reenvio pode deixar o envio pendente para investigação. O processamento e o histórico devem ser testados novamente após alterações em múltiplas instâncias e antes de uso de produção.

A aba de leads depende do vínculo explícito entre lead e conversa para apresentar o resumo correto. Combine o contrato com Gabriel Mendes; não associe conversas por nome ou por telefone presumido.
