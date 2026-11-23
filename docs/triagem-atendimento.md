# Triagem de atendimento

Após o cadastro, o atendimento automático identifica algumas palavras explícitas de suporte e comercial. Se houver sinais de apenas uma categoria, ela é salva na conversa; se não houver sinais suficientes ou houver sinais das duas, o bot pede ao cliente que escolha `/suporte` ou `/comercial`. A escolha também pode ser usada depois para corrigir a classificação. Para pedir uma pessoa, continua disponível `/atendente`.

A classificação é mantida ao longo da conversa e fica visível no painel. Uma conversa antiga recebe o estado `INDEFINIDA` na migração V21. A classificação não cria lead nem ticket e o serviço não responde durante atendimento humano.

Esta primeira versão usa uma lista limitada de palavras, não uma classificação semântica: expressões fora da lista exigem escolha explícita. Ao pedir esclarecimento, a mensagem inicial fica registrada no histórico, mas o cliente pode precisar repetir sua demanda depois da escolha. A classificação não aplica automaticamente a lista de assuntos da configuração de atendimento; essa integração ainda precisa ser desenvolvida e validada. Testar o fluxo real após integrar as demais partes da IA.
