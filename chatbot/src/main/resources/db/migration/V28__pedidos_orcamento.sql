-- HU06.1 (Guilherme Guimaraes): pedido de orcamento pelo Telegram.
-- Tabelas novas; a tabela tickets e os dados existentes nao sao alterados.

-- Contato para retorno da equipe comercial, guardado junto da solicitacao (ticket do tipo ORCAMENTO).
-- E o contato vigente quando o pedido foi feito: o cadastro do cliente pode mudar depois.
CREATE TABLE pedidos_orcamento (
    ticket_id BIGINT PRIMARY KEY REFERENCES tickets(id),
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    contato VARCHAR(300) NOT NULL,
    CONSTRAINT ck_pedido_orcamento_contato CHECK (LENGTH(TRIM(contato)) > 0)
);

CREATE INDEX ix_pedidos_orcamento_empresa ON pedidos_orcamento (empresa_id, ticket_id);

-- Etapa temporaria do pedido no Telegram. Some quando o pedido e registrado ou cancelado.
-- A chave composta impede ligar uma conversa a uma empresa diferente da sua.
CREATE TABLE telegram_pedido_orcamento (
    conversa_id BIGINT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    etapa VARCHAR(32) NOT NULL,
    necessidade VARCHAR(2000),
    CONSTRAINT fk_orcamento_telegram_conversa_empresa
        FOREIGN KEY (conversa_id, empresa_id) REFERENCES conversas (id, empresa_id),
    CONSTRAINT ck_orcamento_telegram_etapa CHECK (etapa IN ('NECESSIDADE', 'CONTATO')),
    CONSTRAINT ck_orcamento_telegram_necessidade CHECK (etapa = 'NECESSIDADE' OR necessidade IS NOT NULL)
);
