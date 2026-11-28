# Consulta de solicitações no Telegram

O comando `/solicitacoes` consulta até 20 solicitações persistidas, das mais recentes para as mais antigas. A empresa, a conta e a conversa vêm do canal. O serviço exige cadastro concluído com telefone e verifica o vínculo da conta Telegram com o cliente e a conversa da empresa. Cada resultado apresenta protocolo, tipo e estado.

Sem cadastro concluído, o bot orienta compartilhar o próprio telefone. Se a consulta falhar, informa que ela está indisponível e oferece `/atendente`. A consulta de planos mantém a validação adicional por e-mail. A posse de uma conta Telegram e o compartilhamento do telefone não comprovam, por si só, a identidade civil do usuário, inclusive em caso de número reciclado ou conta comprometida.

## Contrato proposto para a consulta de planos

Para integrar a implementação de HU04.1, combinar com Artur os mesmos quatro resultados: dados encontrados, consulta concluída sem dados, validação necessária e consulta indisponível. A consulta deve receber empresa e conversa de origem confiável, verificar a identidade e filtrar por empresa e cliente. Uma falha não deve virar lista vazia nem expor detalhes ao cliente. Este documento é uma proposta de integração, não indica que o serviço de planos já exista ou que o contrato tenha sido acordado.

## Testes

No diretório `chatbot`, com Java e Maven disponíveis, execute com banco H2 isolado e integrações desativadas:

```powershell
$env:TELEGRAM_POLLING_ENABLED = 'false'
$env:TESTAR_GROQ_REAL = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=ConsultaSolicitacoesTelegramTests,ValidacaoClienteTests' test
```

Não há migração ou variável adicional para esta alteração. A validação da jornada real depende de ambiente autorizado e da configuração já existente de Telegram e e-mail.
