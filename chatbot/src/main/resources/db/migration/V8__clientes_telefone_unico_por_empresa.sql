-- IDs dos clientes cadastrados pelo fluxo de identificacao. Clientes antigos tem IDs
-- manuais; o servico pula valores ja usados, como no protocolo dos tickets.
CREATE SEQUENCE clientes_id_seq START WITH 1 INCREMENT BY 1;

-- Dados de demonstracao do ExemploAtendimentoService (perfil painel-local) foram
-- gravados com o mesmo telefone na mesma empresa. Cada um recebe um numero proprio,
-- igual ao que o servico passa a gravar, para que a restricao abaixo possa ser criada.
-- So afeta os quatro clientes de exemplo, identificados pelo ID e pelo e-mail de exemplo.
UPDATE clientes
SET numero_telefone = CONCAT('+558199999000', CAST(id - 900000010 AS VARCHAR(4)))
WHERE id BETWEEN 900000010 AND 900000013
  AND email LIKE '%.demo@example.com';

-- O mesmo telefone pode existir em empresas diferentes, mas nao duas vezes na mesma.
-- Garante a regra mesmo com cadastros simultaneos. Se houver duplicatas reais no banco,
-- esta migracao falha de proposito, sem apagar nada. Antes do deploy, confira com:
--   SELECT empresa_id, numero_telefone, COUNT(*) FROM clientes
--   WHERE empresa_id IS NOT NULL
--   GROUP BY empresa_id, numero_telefone HAVING COUNT(*) > 1;
-- (os clientes de exemplo 900000010 a 900000013 sao corrigidos pelo UPDATE acima)
ALTER TABLE clientes
    ADD CONSTRAINT uk_clientes_empresa_telefone UNIQUE (empresa_id, numero_telefone);
