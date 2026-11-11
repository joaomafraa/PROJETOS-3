# Administração de atendentes (HU08.3)

Administração de atendentes, perfis de acesso e responsáveis por notificações: serviços, API (`/api/admin/**`) e tela no painel (menu **Administração**). O envio das notificações ainda não existe.

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

## API (`AdministracaoController`)

Todas as rotas exigem login com perfil `ADMINISTRADOR` (regra `/api/admin/**` do `SecurityConfig`) e token CSRF nas alterações. O administrador e a empresa vêm da sessão; nenhuma rota recebe id de solicitante ou de empresa.

| Método | Rota | O que faz | Sucesso |
| --- | --- | --- | --- |
| GET | `/api/admin/atendentes?ativo=` | Lista a equipe da empresa (`ativo` opcional) | 200 |
| GET | `/api/admin/atendentes/{id}` | Busca um atendente | 200 |
| POST | `/api/admin/atendentes` | Cadastra (`nome`, `email`, `perfil`) | 201 |
| PATCH | `/api/admin/atendentes/{id}/perfil` | Altera o perfil (`perfil`) | 200 |
| POST | `/api/admin/atendentes/{id}/ativar` | Reativa | 200 |
| POST | `/api/admin/atendentes/{id}/desativar` | Desativa | 200 |
| PUT | `/api/admin/atendentes/{id}/senha` | Define a senha (`senha`, mínimo 8) | 204 |
| GET | `/api/admin/responsaveis-notificacao` | Lista a configuração | 200 |
| PUT | `/api/admin/responsaveis-notificacao/{tipo}` | Substitui os responsáveis do tipo (`atendenteIds`) | 200 |

Erros voltam como `{"mensagem": "..."}`:

| Situação | HTTP |
| --- | --- |
| Dados inválidos, JSON quebrado, perfil ou tipo inexistente, senha curta, admin alterando o próprio perfil ou se desativando | 400 |
| Sem login | 401 |
| Perfil sem permissão ou falta de CSRF | 403 |
| Atendente inexistente **ou de outra empresa** (mesma resposta) | 404 |
| E-mail repetido na empresa, ativar quem já está ativo, responsável inativo | 409 |

## Tela

Seção `#administracao` em `painel.html`, com script `administracao.js` e estilos no fim de `painel.css`. A navegação está em `tema.js`. A tela cadastra atendentes, troca perfil, ativa/desativa, define senha e escolhe os responsáveis de cada tipo de notificação. Quem não é administrador vê o aviso "Esta área é restrita a administradores."

Testes: `src/test/java/com/wxn/chatbot/integration/AdministracaoApiTests.java`.

## Pendências

- Edição de nome/e-mail pela administração.
- Envio real das notificações.
- O mesmo e-mail pode existir em duas empresas, mas `ContextoAcesso` busca o atendente só pelo e-mail; nesse caso o login dos dois falha. Combinar a regra com a frente de autenticação.
- Quem tem o perfil alterado mantém o papel antigo na sessão aberta até sair e entrar de novo (as operações de admin continuam protegidas, porque os serviços conferem o perfil no banco).
