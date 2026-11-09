# Administração de atendentes (HU08.3)

Backend de administração de atendentes e responsáveis por notificações. Ainda não há API, tela nem envio de notificações.

## Serviços

| Serviço | Método | Regra |
| --- | --- | --- |
| `AdministracaoAtendentesService` | `cadastrar(solicitanteId, comando)` | Só administrador ativo. Atendente criado na empresa do administrador, ativo, ID por sequência. E-mail único por empresa. |
| | `listar(solicitanteId, ativo)` | Só administrador. Lista apenas a própria empresa; `ativo` nulo = todos. |
| | `buscar(solicitanteId, atendenteId)` | Atendente de outra empresa = não encontrado. |
| | `alterarPerfil(...)`, `desativar(...)`, `ativar(...)` | Usa as regras de `Atendente` (administrador não altera o próprio perfil nem se desativa). Solicitante e alvo bloqueados em ordem de ID. |
| `ResponsaveisNotificacaoService` | `definir(solicitanteId, tipo, ids)` | Substitui a lista do tipo. Atendentes devem ser ativos e da mesma empresa; lista vazia remove todos. |
| | `listar(solicitanteId)` | Configuração da empresa do administrador. |
| | `destinatariosAtivos(empresaId, tipo)` | Uso interno futuro: só atendentes ativos. |

Tipos de notificação: `NOVO_TICKET`, `ATENDIMENTO_HUMANO_SOLICITADO`, `NOVO_LEAD`.

O `solicitanteId` deve vir do contexto autenticado (frente de autenticação), nunca do corpo da requisição. O cadastro não cria senha nem credencial.

## Migração V6

- Sequência `atendentes_id_seq` a partir de 1000.
- Restrição única `(empresa_id, email)` em `atendentes`.
- Tabela `responsaveis_notificacao`, única por `(empresa_id, tipo, atendente_id)`.

## Pendências

- API/rotas protegidas e tela de administração.
- Credenciais e login (frente de autenticação).
- Edição de nome/e-mail pela administração.
- Envio real das notificações.
