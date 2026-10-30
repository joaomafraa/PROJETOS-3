# Chatbot WXN

Projeto acadêmico de atendimento com Java e Spring Boot, organizado em entidades, acesso a dados, serviços de negócio e controladores HTTP.

Endereço de publicação do projeto: https://projetos-3.onrender.com/

## Funcionalidades implementadas

- Entidades Cliente, Atendente, Chatbot, Conversa, Mensagem, Ticket e Empresa, com atributos encapsulados, relacionamentos e validações.
- Persistência com JPA e migrações SQL com Flyway.
- Consulta da fila pelo `FilaConversasService`: exige atendente ativo, filtra pela empresa e pelo status `AGUARDANDO_ATENDENTE`, ordenando pela data de início da conversa e pelo ID.
- Histórico com conteúdo, autor e data das mensagens.
- Página local de histórico, com atualização da lista e abertura de conversas.
- Botão local para gravar um atendimento fictício no banco configurado.
- Persistência de tickets e consultas por empresa com `TicketRepository`. O ticket mantém seu próprio ciclo: pendente, em atendimento e concluído.

Veja o [modelo de classes e suas regras](docs/modelo-de-classes.md).

## Situação das entregas

| Etapa | Situação |
| --- | --- |
| Modelagem das entidades em POO | Classes e diagrama disponíveis |
| Persistência e negócio da fila | Repositório, serviço e testes disponíveis |
| Tela consumindo o negócio da fila | Próxima etapa: a página atual consulta o histórico geral |

A lista do painel consulta diretamente o repositório; a consulta das mensagens passa pelo `HistoricoConversasService`. A tela ainda não chama o `FilaConversasService` e não possui filtros por tipo, status e responsável. O exemplo atual fica em atendimento humano, portanto não aparece na fila de espera.

A persistência de Ticket é uma base para as próximas histórias. Ainda não há serviço de aplicação, API ou tela para abrir, assumir ou concluir tickets. Os IDs dos tickets são informados por quem os cria, seguindo o modelo atual; não há geração automática de protocolo nem regra de unicidade do protocolo.

WhatsApp, Spring AI/LLM, documentos oficiais, CRM e demais sistemas externos são integrações planejadas. Autenticação de usuários também não está implementada.

## Tecnologias

- Java 21 e Spring Boot.
- Spring Web MVC e Spring Data JPA/Hibernate.
- PostgreSQL (Neon) para o banco da aplicação.
- Flyway para criar e evoluir as tabelas.
- H2, JUnit e Mockito para os testes.
- Maven Wrapper, Docker e configuração de publicação no Render.

## Organização do projeto

```text
PROJETOS-3/
├── README.md
├── docs/modelo-de-classes.md
├── render.yaml
└── chatbot/
    ├── pom.xml
    ├── Dockerfile
    ├── .env.example
    └── src/
        ├── main/
        │   ├── java/com/wxn/chatbot/
        │   │   ├── ChatbotApplication.java
        │   │   ├── model/        # Entidades e suas regras
        │   │   ├── repository/   # Consultas e gravação no banco
        │   │   ├── service/      # Casos de uso e transações
        │   │   ├── dto/          # Dados devolvidos pelas consultas
        │   │   ├── web/          # Rotas HTTP do painel local
        │   │   └── demo/         # Demonstração de POO no terminal
        │   └── resources/
        │       ├── static/      # Página HTML e JavaScript
        │       ├── db/migration/
        │       └── application*.properties
        └── test/                # Testes das entidades e integrações
```

## Executar o painel local (PowerShell)

É necessário Java 21 ou superior. A partir da raiz do repositório:

1. Entre no módulo:

   ```powershell
   cd chatbot
   ```

2. Se ainda não existir `.env`, copie `.env.example` para `.env` e preencha os dados do seu banco Neon. Preserve um `.env` que já esteja configurado.

   ```properties
   DB_URL=jdbc:postgresql://ep-seu-host.sua-regiao.aws.neon.tech/neondb?sslmode=require
   DB_USER=seu-usuario
   DB_PASSWORD=sua-senha
   ```

   Use uma URL JDBC, com apenas um prefixo `jdbc:` e sem credenciais dentro da URL. O arquivo `.env` é ignorado pelo Git.

3. Carregue explicitamente o arquivo e habilite o painel:

   ```powershell
   $env:SPRING_CONFIG_IMPORT = 'file:.env[.properties]'
   $env:SPRING_PROFILES_ACTIVE = 'painel-local'
   .\mvnw.cmd spring-boot:run
   ```

4. Abra http://localhost:8080/ para consultar o histórico.

O perfil `painel-local` escuta apenas em `127.0.0.1` e usa o atendente de ID 1 por padrão. Para usar outro, configure `PAINEL_ATENDENTE_ID` com um ID ativo. Esse atendente fixo serve à apresentação local; não representa login.

O botão **Criar atendimento de exemplo** grava dados fictícios no banco configurado (inclusive no Neon, se ele estiver configurado). Repetir o clique reutiliza o exemplo existente. O exemplo usa o ID `900000001` para empresa, cliente, chatbot e conversa quando necessário, e rejeita as colisões detectadas nos identificadores.

Se a lista retornar erro de atendente inexistente, crie o exemplo ou configure um atendente ativo já cadastrado. Se as consultas retornarem 404, confira se o perfil `painel-local` está ativo. Spring Boot não carrega `.env` automaticamente.

## Demonstração de POO no terminal

Dentro de `chatbot`:

```powershell
.\mvnw.cmd compile
java -cp target/classes com.wxn.chatbot.demo.Demonstracao
```

Essa demonstração cria os objetos em memória e imprime as mensagens e o ciclo do ticket. Ela não grava no banco. Para persistir, o código precisa usar um repositório dentro do contexto da aplicação; as anotações JPA sozinhas não salvam objetos.

## Banco e migrações

| Migração | Conteúdo |
| --- | --- |
| V1 | Empresas, clientes, chatbots, atendentes, conversas e índice da fila |
| V2 | Mensagens, identificação do autor e ordenação do histórico |
| V3 | Tickets, relacionamentos e restrições de tipo e estado |

Flyway aplica migrações pendentes ao iniciar a aplicação com acesso ao banco. O Hibernate usa `validate` por padrão para conferir o mapeamento; alterações futuras de estrutura devem ser feitas em novas migrações.

Tickets referenciam empresa, cliente, conversa de origem e, quando atribuído, um atendente responsável. Salvar um ticket não cria nem remove automaticamente essas entidades relacionadas: elas devem existir no banco.

## Testes

Dentro de `chatbot`:

```powershell
.\mvnw.cmd clean test
```

Os testes usam H2 em memória e aplicam as migrações, sem precisar de credenciais Neon. Verificam regras de domínio, consultas por empresa, fila, histórico, persistência de mensagens e tickets e comportamento do painel local. Eles não substituem uma validação no PostgreSQL real.

## Publicação no Render

O `render.yaml` configura o serviço Docker a partir da pasta `chatbot` e solicita `DB_URL`, `DB_USER` e `DB_PASSWORD` como variáveis do ambiente. O Dockerfile usa Java 21, executa os testes no build e inicia a aplicação na porta `PORT`.

O endpoint `/actuator/health` verifica a saúde da aplicação, incluindo o banco. O HTML da página inicial é servido em `/`, mas suas rotas de consulta e criação de exemplo só existem com `painel-local`. Portanto, a publicação padrão ainda não disponibiliza um painel funcional de histórico ou fila.

Não habilite o perfil local como solução de publicação: ele usa um atendente fixo e limita a escuta a `127.0.0.1`. O painel publicado será tratado em uma etapa própria, com configuração de acesso adequada.

## Grupo

- João Carlos Mafra
- Arthur Almeida
- Gabriel Malta
- Márcio Rodriguez
- Gabriel Mendes
- Pedro Martins
- Hélio de Moraes
- Guilherme Guimarães
