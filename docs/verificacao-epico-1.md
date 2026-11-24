# Verificação do Épico 1 — 24/11/2026

Referência: histórias do PDF WXN atualizado e código da main após o merge das respostas fundamentadas. O canal adotado na implementação é Telegram; a história original HU01.1 menciona WhatsApp, site e Instagram. A troca do canal é uma decisão do grupo, não uma implementação de WhatsApp.

| História | Evidências e limitações |
|---|---|
| HU01.1 | Telegram por polling e identificação existentes. WhatsApp e link para iniciar atendimento a partir do site/Instagram não foram comprovados. Telegram foi escolhido pelo grupo para demonstração. |
| HU01.2 | Triagem por palavras e comandos com intenção persistida. Perguntas gerais podem seguir para a busca documental sem seleção obrigatória. Sinais conflitantes pedem esclarecimento; uma frase fora do vocabulário pode permanecer indefinida e a demanda ambígua pode precisar ser repetida. |
| HU01.3 | Configuração de horários e assuntos existe; a transferência informa quando a equipe humana está fora do expediente. A lista de assuntos automáticos não está integralmente aplicada à triagem/geração. Disponibilidade 24 horas depende da hospedagem, banco, canal e provedor; não foi demonstrada. |
| HU02.1 | Busca em documentos ativos por empresa, verificação dos trechos e geração integrada. Requer teste no Telegram real com Groq e documentos do ambiente certo. |
| HU02.2 | Prompt pede resposta clara e citações; fontes podem ser vistas no painel. A qualidade e pertinência precisam ser avaliadas em casos reais, incluindo perguntas com sinônimos. |
| HU02.3 | Política trata ausência de documentos, cobertura insuficiente e perguntas vagas; informa limitação e oferece atendimento humano. Cobertura textual e instruções não garantem ausência absoluta de alucinação. |
| HU03.1 | Histórico é persistido e limitado no contexto enviado à IA. A pergunta feita antes de concluir o cadastro não é automaticamente retomada; o cliente pode precisar repeti-la. |
| HU03.2 | `/atendente` encaminha à fila, assunção e resposta humana existem, IA respeita estado da conversa. Precisa validar no Render na versão implantada. |
| HU03.3 | Histórico e serviço de resumo estruturado existem. O resumo também está disponível no contexto do painel pelo endpoint autenticado `/api/historico/{id}/resumo`, limitado à empresa; é uma citação da primeira mensagem, não uma interpretação garantida da demanda. |

## Validação necessária antes de declarar o épico concluído

1. Confirmar com o professor se Telegram substitui WhatsApp e se as histórias adaptadas devem ser atualizadas no documento oficial.
2. Em ambiente real, testar documento ativo, pergunta respondível e fontes; pergunta fora da base; cliente pede humano; atendente assume, responde e conclui; nova mensagem inicia nova conversa.
3. Testar perguntas gerais, intenções ambíguas, troca entre suporte e comercial e fluxo de cadastro sem perder a pergunta inicial.
4. Validar isolamento entre empresas, documento desativado, falha do provedor, horários e comportamento fora do expediente com dados de teste autorizados.
5. Resolver a aplicação dos assuntos automáticos e verificar expectativa de disponibilidade contínua no plano de hospedagem usado.

Não usar o bot local com polling ativo ao mesmo tempo que o Render para o mesmo token. Não usar dados reais ou alterar o banco de produção para criar cenários de teste sem planejamento.
