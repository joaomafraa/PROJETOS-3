# Resumo de conversas para leads

Na aba Clientes e leads, escolha uma conversa da mesma empresa e clique em Vincular conversa. A associação é explícita e pode ser alterada; o sistema não associa automaticamente por nome ou telefone. O painel exibe a primeira mensagem do cliente e os trechos recentes retornados pelo resumo estruturado existente. Esses trechos são citações, não uma interpretação gerada por IA.

A operação usa o atendente autenticado e valida lead e conversa dentro da empresa. Leads antigos continuam sem vínculo até que um atendente escolha a conversa. A lista de conversas usa a consulta existente do histórico, limitada a 100 registros.

A migração Flyway V16 cria a tabela de vínculos. Combine sua numeração com a equipe antes de integrá-la, caso outra branch tenha adicionado migrações novas. Para verificar: execute `mvnw.cmd test` na pasta chatbot (ou `./mvnw test` no macOS/Linux).
