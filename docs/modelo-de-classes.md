# Modelo de classes do Chatbot WXN

Representa as classes implementadas em `chatbot/src/main/java/com/wxn/chatbot/model`.
O diagrama usa Mermaid, visualizável diretamente no GitHub. Para facilitar a
leitura, apresenta os principais métodos; getters, construtores e validações
auxiliares foram omitidos. As associações representam referências entre objetos,
sem exigir listas inversas em todas as classes.

## Classes e relacionamentos

```mermaid
classDiagram
    direction TB
    class Empresa {
        -Long id
        -String nome
        +alterarNome(String nome)
    }
    class Cliente {
        -Long id
        -String nome
        -String email
        -String numeroTelefone
        -boolean identidadeConfirmada
        -String empresaInformada
        +alterarNome(String nome)
        +atualizarEmail(String email)
        +atualizarTelefone(String telefone)
        +informarEmpresa(String nome)
        +confirmarIdentidade()
        +vincularEmpresa(Empresa empresa)
        +trocarEmpresa(Empresa empresa, Atendente solicitante)
    }
    class Atendente {
        -Long id
        -String nome
        -String email
        -PerfilAcesso perfil
        -boolean ativo
        +verificarPodeAtender(Empresa empresa)
        +alterarPerfil(PerfilAcesso perfil, Atendente solicitante)
        +ativar(Atendente solicitante)
        +desativar(Atendente solicitante)
    }
    class Chatbot {
        -Long id
        -String nome
        -String mensagemBoasVindas
        -boolean ativo
        +verificarPodeAtender(Empresa empresa)
        +alterarMensagemBoasVindas(String mensagem, Atendente solicitante)
        +ativar(Atendente solicitante)
        +desativar(Atendente solicitante)
    }
    class Conversa {
        -Long id
        -Instant dataInicio
        -Instant dataEncerramento
        -Status status
        +adicionarMensagem(Long id, String conteudo, TipoRemetente remetente) Mensagem
        +adicionarMensagem(Long id, String conteudo, TipoRemetente remetente, Atendente autor) Mensagem
        +solicitarAtendimentoHumano()
        +iniciarAtendimentoHumano(Atendente atendente)
        +encerrar()
    }
    class Mensagem {
        -Long codigo
        -Long id
        -int ordem
        -String conteudo
        -Instant dataEnvio
        -TipoRemetente remetente
    }
    class Ticket {
        -Long id
        -String protocolo
        -String descricao
        -TipoSolicitacao tipo
        -Instant dataAbertura
        -StatusTicket status
        -Instant dataConclusao
        +atribuirResponsavel(Atendente atendente)
        +iniciarAtendimento()
        +concluir()
    }

    Cliente "0..*" --> "0..1" Empresa : cadastro
    Atendente "0..*" --> "1" Empresa : pertence
    Chatbot "0..*" --> "1" Empresa : atende
    Conversa "0..*" --> "1" Empresa : ambiente historico
    Conversa "0..*" --> "1" Cliente : participante
    Conversa "0..*" --> "1" Chatbot : atendimento automatico
    Conversa "0..*" --> "0..1" Atendente : responsavel
    Conversa "1" *-- "0..*" Mensagem : historico
    Mensagem "0..*" --> "0..1" Atendente : autor humano
    Ticket "0..*" --> "1" Conversa : origem
    Ticket "0..*" --> "1" Empresa : ambiente historico
    Ticket "0..*" --> "1" Cliente : solicitante
    Ticket "0..*" --> "0..1" Atendente : responsavel
```

`1` significa obrigatório; `0..1`, opcional; `0..*`, zero ou muitos.
A composição entre conversa e mensagem expressa que a mensagem pertence àquela
conversa. Ela não indica exclusão em cascata: o mapeamento atual propaga apenas
persistência e atualização das mensagens.

## Valores dos enumeradores

| Enumerador | Valores |
| --- | --- |
| Atendente.PerfilAcesso | ADMINISTRADOR, COORDENADOR, ATENDENTE |
| Conversa.Status | EM_ATENDIMENTO_AUTOMATICO, AGUARDANDO_ATENDENTE, EM_ATENDIMENTO_HUMANO, ENCERRADA |
| Mensagem.TipoRemetente | CLIENTE, CHATBOT, ATENDENTE |
| Ticket.TipoSolicitacao | SUPORTE, ORCAMENTO, DEMONSTRACAO, REUNIAO, VISITA_TECNICA |
| Ticket.StatusTicket | PENDENTE, EM_ATENDIMENTO, CONCLUIDO |

## Encapsulamento e regras

- Os atributos são privados. Alterações passam pelos métodos do domínio,
  que validam dados, responsáveis e transições de estado.
- Um cliente pode começar sem empresa cadastrada. `empresaInformada` é um
  texto fornecido pelo usuário e não concede vínculo ou acesso à empresa.
- Conversa e ticket mantêm a empresa de origem, mesmo que o cadastro do cliente mude.
- Atendentes responsáveis precisam estar ativos e pertencer à empresa do atendimento.
- A conversa começa em atendimento automático, pode aguardar um atendente e
  passar ao atendimento humano. Pode ser encerrada em qualquer estado aberto.
- Mensagens não podem ser adicionadas após o encerramento. A mensagem humana
  exige o autor responsável; o chatbot só responde no estado automático.
- `getMensagens()` devolve uma cópia não modificável da lista.
- Ticket começa pendente, exige responsável para iniciar atendimento e só pode
  ser concluído após iniciar. Concluir um ticket não encerra a conversa.
- Um ticket concluído não pode receber outro responsável nem ser reaberto pelos métodos atuais.

## Persistência

Todas as sete classes são entidades JPA. Construtores sem argumentos protegidos
permitem que o JPA recarregue os objetos; a criação pela aplicação usa os
construtores públicos com validações. Os atributos antes `final` de Ticket
permanecem sem setters, com colunas de origem que não são atualizadas pelo JPA.

| Classe | Tabela | Migração |
| --- | --- | --- |
| Empresa | empresas | V1 |
| Cliente | clientes | V1 |
| Atendente | atendentes | V1 |
| Chatbot | chatbots | V1 |
| Conversa | conversas | V1 |
| Mensagem | mensagens | V2 |
| Ticket | tickets | V3 |

`Mensagem.codigo` é gerado pelo banco. Seu `id` é único dentro de uma conversa.
Os IDs das demais entidades são fornecidos pela aplicação. A tabela de tickets
valida tipo e coerência entre status, responsável e data de conclusão; as regras
de atividade e pertencimento do atendente são verificadas pelos métodos Java.

`ConversaRepository` consulta fila e histórico. `TicketRepository` grava tickets
e oferece consultas com filtro por empresa. Esse filtro não substitui autenticação:
um futuro serviço de tickets deverá validar o solicitante antes de consultar.

## Escopo da próxima entrega

A tela da fila ainda precisa consumir `FilaConversasService`. O painel existente
exibe histórico geral. Este diagrama documenta o domínio atual e não implica que
todas as operações das entidades já tenham uma API ou botão na interface.
