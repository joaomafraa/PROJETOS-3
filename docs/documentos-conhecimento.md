# Documentos oficiais para a IA

O administrador da empresa acessa **Administração → Documentos da IA**, informa um título e cola o conteúdo ou escolhe um arquivo `.txt` codificado em UTF-8. Depois, salva o documento. É possível editar, ativar e desativar. Cada edição de título ou conteúdo incrementa a versão. O armazenamento é no banco da aplicação e pertence à empresa do administrador; não depende do disco do Render.

O limite é de 200.000 caracteres por documento e 800 KB por arquivo selecionado no navegador. PDF, Word, imagens e OCR não são suportados; exporte ou copie o texto para TXT e revise-o antes do cadastro. O sistema não interpreta o documento como comandos.

## Contrato para a equipe

`DocumentosConhecimentoService.ativosDaEmpresa(empresaId)` retorna documentos ativos com ID, título, conteúdo e versão. É uma API Java interna: o chamador deve usar um ID de empresa já validado no contexto do canal. Artur pode utilizá-la para criar e atualizar os trechos de busca; a versão deve ser convertida em texto no contrato `FonteDisponivel`. Desativação impede o retorno em novas buscas; índices ou caches criados pela equipe também precisam ser invalidados.

O cadastro por si só **não conecta os documentos ao LLM**. A busca, a geração fundamentada, a revalidação em caso de alteração concorrente e o registro de fontes dependem da integração com as partes de Artur e Guilherme. O histórico das fontes existente guarda suas próprias cópias.

As rotas administrativas estão em `/api/admin/documentos`: GET lista, POST cria, PUT `/{id}` edita e PATCH `/{id}/estado` ativa ou desativa. Exigem administrador, autenticação e CSRF para alterações. A empresa é obtida da sessão. A migração Flyway é `V19__documentos_conhecimento.sql`; coordene sua numeração antes de integrar outras branches. Teste PostgreSQL isolado e faça backup antes de publicar migrações no banco usado pelo Render.

## Validação manual

1. Entre como administrador e abra Administração.
2. Cadastre um texto, edite e confira a versão. Desative e reative o documento.
3. Entre com um administrador de outra empresa e confirme que ele não encontra o documento.
4. Reinicie a aplicação e confirme que o documento continua cadastrado.
5. Após integrar a busca, confirme que documentos inativos e de outras empresas não chegam ao contexto da IA.
