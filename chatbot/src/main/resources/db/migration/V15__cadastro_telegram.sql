ALTER TABLE telegram_contatos
    ADD COLUMN etapa_cadastro VARCHAR(30) DEFAULT 'AGUARDANDO_TELEFONE' NOT NULL;

-- Numero compartilhado pelo proprio usuario (contato do Telegram), guardado so ate
-- o cadastro ser concluido pelo IdentificacaoClienteService.
ALTER TABLE telegram_contatos
    ADD COLUMN telefone_informado VARCHAR(32);

ALTER TABLE telegram_contatos
    ADD CONSTRAINT ck_telegram_contatos_etapa CHECK (etapa_cadastro IN
        ('AGUARDANDO_TELEFONE', 'AGUARDANDO_NOME', 'AGUARDANDO_EMPRESA', 'CONCLUIDO'));

-- Contatos ja ligados a um cliente com telefone nao precisam passar pela identificacao.
UPDATE telegram_contatos
SET etapa_cadastro = 'CONCLUIDO'
WHERE cliente_id IN (SELECT id FROM clientes WHERE numero_telefone IS NOT NULL);
