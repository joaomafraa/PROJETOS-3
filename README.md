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
- Supabase
- Railway

## Banco de Dados — Supabase

O Supabase será utilizado para hospedar o banco de dados PostgreSQL da aplicação.

O banco será responsável pelo armazenamento das informações necessárias para o
funcionamento do sistema e será acessado pelo backend desenvolvido em Spring Boot.

Fluxo:

Spring Boot → Supabase → PostgreSQL

As informações de conexão com o banco serão configuradas por meio de variáveis
de ambiente, evitando que dados sensíveis, como senhas e credenciais, sejam
armazenados diretamente no código.

## Deploy — Railway

O Railway será utilizado para realizar o deploy do backend da aplicação.

A aplicação Spring Boot será conectada ao repositório do projeto e executada
em ambiente de nuvem.

Fluxo:

GitHub → Railway → Aplicação Spring Boot

As variáveis necessárias para conexão com o Supabase e outros serviços externos
serão configuradas diretamente no ambiente do Railway.

## Arquitetura inicial

Usuário
   ↓
Interface / Chat
   ↓
Spring Boot
   ↓
Spring AI / LLM
   ↓
Serviços externos / APIs / ERP
   ↓
Supabase (PostgreSQL)

## Status do projeto

Projeto em desenvolvimento.

Atualmente estão sendo definidos e configurados:

- Repositório Git
- Ambiente de desenvolvimento
- Banco de dados com Supabase
- Ambiente de deploy com Railway
- Arquitetura do backend em Spring Boot
