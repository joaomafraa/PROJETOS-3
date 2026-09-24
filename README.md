# Chatbot WXN

Backend em Java 21 e Spring Boot, com PostgreSQL no Neon e deploy no Render.
O projeto está na estrutura inicial; a integração com Spring AI/LLM ainda não foi implementada.

## Arquitetura

```text
Cliente -> Spring Boot (Render) -> PostgreSQL (Neon)
```

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
três variáveis de ambiente e **Health Check Path** como `/actuator/health`.

Depois do deploy, abra `https://<seu-servico>.onrender.com/actuator/health`.
A resposta deve conter `"status":"UP"`; ela também verifica a conexão com o banco.
A rota `/` ainda não possui uma página ou endpoint e pode retornar 404.

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

Verifique `http://localhost:8080/actuator/health`.
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
