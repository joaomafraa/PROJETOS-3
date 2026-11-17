# Leads no atendimento

Na seção **Clientes e leads** do painel, um atendente autenticado registra nome opcional, telefone ou e-mail, origem e necessidade. A lista permite buscar nos registros carregados e atualizar a consulta. A empresa vem da sessão do atendente, não dos campos fornecidos pelo navegador.

`GET /api/leads` lista leads da empresa; `GET /api/leads?contato=...` consulta pelo contato normalizado. `POST /api/leads` recebe `nome`, `contato`, `origem` e `necessidade`, exige autenticação e token CSRF e retorna 201. Dados inválidos retornam 400; contato e necessidade já registrados na mesma empresa retornam 409. A restrição de unicidade existente também protege cadastros concorrentes. O mesmo contato pode registrar necessidades diferentes ou ser usado em outra empresa.

O DTO retornado fornece ID local, empresa, nome, tipo e valor de contato, origem, necessidade e data de criação para futura integração. A necessidade é o texto informado, não um resumo produzido por IA. O modelo atual não vincula lead a conversa nem mantém estado de sincronização: esses contratos e a integração com CRM devem ser definidos com João. Não há captura automática do lead pelo canal nesta entrega; o cadastro no painel é manual. A política de perfis comerciais deve ser alinhada com Gabriel Malta; a nova operação segue a política atual de acesso autenticado da listagem.

Para executar os testes do backend, no diretório `chatbot`, use `./mvnw test` (Windows: `mvnw.cmd test`). Com a aplicação e uma conta ativa configuradas, entre no painel, abra **Clientes e leads**, registre um interessado e tente repetir o cadastro para observar a mensagem de duplicidade.
