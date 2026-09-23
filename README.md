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
- Railway
- GitHub

## Banco de Dados — PostgreSQL

O PostgreSQL será utilizado como banco de dados da aplicação.

Durante o desenvolvimento, o banco será executado localmente, permitindo uma
comunicação direta entre o PostgreSQL e o backend desenvolvido em Spring Boot.

Fluxo:

Spring Boot → PostgreSQL Local

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

## Deploy — Railway

O Railway será utilizado para realizar o deploy do backend desenvolvido em
Spring Boot.

A aplicação será conectada ao repositório do projeto no GitHub e executada
em ambiente de nuvem.

Fluxo:

GitHub → Railway → Aplicação Spring Boot

As informações sensíveis necessárias para serviços externos, como chaves de API
do modelo de linguagem, serão configuradas através de variáveis de ambiente
no Railway.

O PostgreSQL utilizado durante o desenvolvimento será executado localmente e,
portanto, não fará parte inicialmente do ambiente de deploy.

## Arquitetura Inicial

O fluxo principal da aplicação será:

Usuário
   ↓
Interface / Chat
   ↓
Spring Boot
   ├──→ Spring AI / LLM
   ├──→ PostgreSQL Local
   └──→ Serviços externos / APIs / ERP

O Spring Boot funcionará como a camada central da aplicação, sendo responsável
pelas regras de negócio e pela comunicação entre o chatbot, o banco de dados,
o modelo de linguagem e os serviços externos.

## Ambiente de Desenvolvimento

Durante o desenvolvimento, os principais componentes serão executados da
seguinte forma:

Spring Boot → Local
PostgreSQL → Local
LLM → API externa
Código-fonte → GitHub
Deploy do backend → Railway

## Status do Projeto

Projeto em desenvolvimento.

Atualmente estão sendo definidos e configurados:

- Repositório Git
- Ambiente de desenvolvimento
- Banco de dados PostgreSQL local
- Backend em Spring Boot
- Integração com Spring AI / LLM
- Ambiente de deploy com Railway
- Arquitetura e integrações da aplicação
