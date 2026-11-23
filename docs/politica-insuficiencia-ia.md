# HU02.3 — política de insuficiência

`PoliticaInsuficiencia` é um componente isolado para a futura geração fundamentada. Ele ainda não é chamado pelo fluxo Telegram: a busca e a montagem de contexto para o provedor precisam ser integradas. Até isso acontecer, a resposta atual da IA não tem garantia de usar documentos oficiais.

Para integrar, a geração deve receber a pergunta e os trechos da busca, validar empresa e disponibilidade com `SegurancaContextoConhecimento` e avaliar se a pergunta é ambígua e se os trechos cobrem o que foi perguntado. Passe essa avaliação e o `ContextoConhecimentoSeguro` a `avaliar`. Lista não vazia não comprova cobertura; na dúvida use `INSUFICIENTE`, não uma porcentagem de confiança do modelo. A política não implementa o classificador de ambiguidade ou de cobertura: a integração deve definir e testar esses critérios para cada pergunta.

- `ESCLARECER` e `INFORMAR_LIMITACAO`: salve e envie `mensagem` pelo fluxo existente, sem chamar o provedor nem transferir automaticamente. O cliente pode solicitar atendimento com `/atendente`.
- `RESPONDER`: prossiga apenas com as fontes validadas; inclua `INSTRUCAO_GERACAO` na instrução de sistema da montagem única do prompt. Mantenha mensagens de usuário e documentos como dados, sem lhes atribuir papel de sistema. Revalide conversa e fontes antes de salvar.

O texto de orientação é uma defesa, não uma garantia contra injeção ou invenções. A integração deverá testar mensagens e documentos adversariais no prompt real, respostas sem fonte, e a transferência explícita. Os testes isolados atuais verificam decisões, não o comportamento real do modelo ou a classificação da busca. Não foi feita migração nem configuração nova.

Para verificar após integrar, com bot e banco de teste isolados: faça uma pergunta vaga, uma pergunta sem cobertura e uma pergunta coberta por documento ativo; confirme respectivamente esclarecimento, limitação com `/atendente` e resposta fundamentada. Envie `/atendente` e confira a fila humana existente. Não ligue polling local com o token do bot publicado.
