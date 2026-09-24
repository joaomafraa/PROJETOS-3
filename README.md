# Chatbot WXN

## Sobre o projeto

Este projeto tem como objetivo desenvolver um chatbot inteligente para atendimento,
capaz de compreender solicitações dos usuários e integrar-se a sistemas externos,
bancos de dados e APIs.

O backend da aplicação será desenvolvido utilizando Java com Spring Boot.

## Tecnologias

- Java
- Spring Boot
- Spring AI
- PostgreSQL
- Neon
- Render
- GitHub

## Banco de Dados — PostgreSQL

O PostgreSQL será utilizado como banco de dados da aplicação.

O banco será hospedado no Neon, permitindo uma comunicação segura entre o
PostgreSQL e o backend desenvolvido em Spring Boot, localmente ou no Render.

Fluxo:

Spring Boot → PostgreSQL (Neon)

O banco será responsável pelo armazenamento das informações necessárias para
o funcionamento da aplicação.

As informações de conexão com o banco serão configuradas no ambiente da aplicação,
evitando que dados sensíveis, como senhas e credenciais, sejam armazenados
diretamente no código.

## Integração com LLM

O Spring AI será utilizado para realizar a integração entre o backend da aplicação
e o modelo de linguagem (LLM).

O modelo será responsável por auxiliar na compreensão das solicitações dos usuários
e na geração das respostas do chatbot.

Fluxo:

Usuário → Chatbot → Spring Boot → Spring AI / LLM

Quando necessário, o backend também poderá consultar o banco de dados ou serviços
externos para obter informações utilizadas durante o atendimento.

## Deploy — Render

O Render será utilizado para realizar o deploy do backend desenvolvido em
Spring Boot.

A aplicação será conectada ao repositório do projeto no GitHub e executada
em ambiente de nuvem.

Fluxo:

GitHub → Render → Aplicação Spring Boot

As informações sensíveis necessárias para serviços externos, como chaves de API
do modelo de linguagem, serão configuradas através de variáveis de ambiente
no Render.

O PostgreSQL será hospedado no Neon e conectado à aplicação no Render através
de variáveis de ambiente. Neon e Render são serviços separados.

## Arquitetura Inicial

O fluxo principal da aplicação será:

Usuário
   ↓
Interface / Chat
   ↓
Spring Boot
   ├──→ Spring AI / LLM
   ├──→ PostgreSQL (Neon)
   └──→ Serviços externos / APIs / ERP

O Spring Boot funcionará como a camada central da aplicação, sendo responsável
pelas regras de negócio e pela comunicação entre o chatbot, o banco de dados,
o modelo de linguagem e os serviços externos.

## Ambiente de Desenvolvimento

Durante o desenvolvimento, os principais componentes serão executados da
seguinte forma:

Spring Boot → Local
PostgreSQL → Neon
LLM → API externa
Código-fonte → GitHub
Deploy do backend → Render

## Status do Projeto

Projeto em desenvolvimento.

Atualmente estão sendo definidos e configurados:

- Repositório Git
- Ambiente de desenvolvimento
- Banco de dados PostgreSQL no Neon
- Backend em Spring Boot
- Integração com Spring AI / LLM
- Ambiente de deploy com Render
- Arquitetura e integrações da aplicação

## Configuração atual

O backend usa Java 21. A integração com Spring AI/LLM descrita acima está planejada.
A página inicial está disponível em `/` e a verificação de saúde em `/actuator/health`.

## Configurar o Neon

1. Crie um projeto no Neon e selecione o banco e o usuário no painel **Connect**.
2. Copie o host, o nome do banco, o usuário e a senha para as variáveis abaixo.
3. Use a URL no formato JDBC, com TLS:

```text
DB_URL=jdbc:postgresql://ep-seu-host.sua-regiao.aws.neon.tech/neondb?sslmode=require
DB_USER=neondb_owner
DB_PASSWORD=sua-senha
```

Substitua host, banco e usuário pelos valores reais do painel. Não coloque usuário
ou senha dentro de `DB_URL`. A URI `postgresql://usuario:senha@host/banco` fornecida
pelo Neon não pode ser colada diretamente como URL JDBC.

Para esta instância inicial, o host direto do Neon funciona com o pool Hikari
limitado a 5 conexões. Nunca versione credenciais.

## Deploy no Render

1. Envie este projeto para seu repositório GitHub, incluindo `render.yaml`.
2. No Render, escolha **New > Blueprint** e conecte o repositório.
3. Informe `DB_URL`, `DB_USER` e `DB_PASSWORD` quando solicitado.
4. Confirme a criação do serviço `chatbot-wxn`.

O Blueprint usa o plano gratuito, compila e testa com Java 21 via Docker e inicia
o backend na porta definida automaticamente pelo Render (`PORT`).
O banco continua no Neon; nenhum banco é criado no Render.

Caso crie um **Web Service** manualmente, selecione Docker, configure
**Root Directory** como `chatbot`, **Dockerfile Path** como `./Dockerfile`, as mesmas
três variáveis de ambiente, **Docker Build Context Directory** como `.` e
**Health Check Path** como `/actuator/health`.

Depois do deploy, abra `https://<seu-servico>.onrender.com/actuator/health`.
A resposta deve conter `"status":"UP"`; ela também verifica a conexão com o banco.
A rota `/` exibe a página inicial com a mensagem **Hello World!**.

## Executar localmente (PowerShell)

Instale Java 21 ou superior. O Maven Wrapper está incluído no projeto.
Na raiz do repositório:

```powershell
cd chatbot
$env:DB_URL = 'jdbc:postgresql://ep-seu-host.sua-regiao.aws.neon.tech/neondb?sslmode=require'
$env:DB_USER = 'neondb_owner'
$env:DB_PASSWORD = 'sua-senha'
.\mvnw.cmd spring-boot:run
```

Abra `http://localhost:8080/` para ver a página inicial.
Verifique `http://localhost:8080/actuator/health` para consultar a saúde da aplicação.
`chatbot/.env.example` é uma referência: Spring Boot não carrega `.env`
automaticamente. Configure as variáveis no terminal ou na sua IDE.

## Schema e testes

O Hibernate usa `validate` por padrão, sem alterar tabelas na inicialização.
Atualmente não existem entidades ou migrações. Ao adicionar entidades, crie
migrações SQL antes do deploy. Para protótipos em um banco de desenvolvimento,
é possível definir `DB_DDL_AUTO=update`; não use isso como estratégia de migração
de produção.

```powershell
cd chatbot
.\mvnw.cmd -B verify
```

Os testes usam H2 em memória, sem credenciais Neon. Eles verificam a inicialização
do Spring e a resposta HTTP do endpoint de saúde, mas não substituem um teste de
integração com PostgreSQL real.

## Referências

- [Neon com Java](https://neon.com/docs/guides/java)
- [Blueprints do Render](https://render.com/docs/blueprint-spec)
- [Health checks do Render](https://render.com/docs/health-checks)
