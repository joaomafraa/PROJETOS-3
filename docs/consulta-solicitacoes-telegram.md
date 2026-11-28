# Consulta de solicitações no Telegram

O comando `/solicitacoes` consulta até 20 solicitações persistidas, das mais recentes para as mais antigas. A empresa, a conta e a conversa vêm do canal. O serviço exige cadastro concluído com telefone e verifica o vínculo da conta Telegram com o cliente e a conversa da empresa. Cada resultado apresenta protocolo, tipo e estado.

Sem cadastro concluído, o bot orienta compartilhar o próprio telefone. Se a consulta falhar, informa que ela está indisponível e oferece `/atendente`. A consulta de planos mantém a validação adicional por e-mail. A posse de uma conta Telegram e o compartilhamento do telefone não comprovam, por si só, a identidade civil do usuário, inclusive em caso de número reciclado ou conta comprometida.

O [fluxo de planos e serviços](planos-servicos.md) está disponível pelo comando `/planos` após validação por e-mail. A consulta usa dados de demonstração, filtrados pela empresa e pelo cliente.

## Testes

No diretório `chatbot`, com Java e Maven disponíveis, execute com banco H2 isolado e integrações desativadas:

```powershell
$env:TELEGRAM_POLLING_ENABLED = 'false'
$env:TESTAR_GROQ_REAL = 'false'
.\mvnw.cmd '-Dspring.profiles.active=test' '-Dtest=ConsultaSolicitacoesTelegramTests,ValidacaoClienteTests' test
```

A validação ponta a ponta de `/solicitacoes` usa o bot do ambiente e um cadastro de teste com solicitações. A validação de `/planos` também exige SMTP e um e-mail confiável.
