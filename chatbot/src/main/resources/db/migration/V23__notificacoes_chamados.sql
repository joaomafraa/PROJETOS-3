-- HU05.3: notificacao de novos chamados aos responsaveis da empresa.
-- A notificacao fica salva e aparece no painel do destinatario. Nao ha envio externo
-- (e-mail/Telegram) nesta entrega: o registro nao significa "mensagem enviada".
-- Tabela nova; nenhuma tabela existente e alterada.
-- ATENCAO: combinar o numero V23 com as outras branches antes do merge.

CREATE SEQUENCE notificacoes_chamados_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE notificacoes_chamados (
    id BIGINT PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    ticket_id BIGINT NOT NULL REFERENCES tickets(id),
    atendente_id BIGINT NOT NULL REFERENCES atendentes(id),
    tipo VARCHAR(40) NOT NULL,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    lida_em TIMESTAMP WITH TIME ZONE,
    -- Mesma notificacao nunca e criada duas vezes para a mesma pessoa,
    -- mesmo se o evento do chamado for processado de novo.
    CONSTRAINT uk_notificacao_chamado UNIQUE (ticket_id, atendente_id, tipo),
    CONSTRAINT ck_notificacao_tipo CHECK (tipo IN ('NOVO_TICKET'))
);

CREATE INDEX ix_notificacoes_destinatario
    ON notificacoes_chamados (empresa_id, atendente_id, lida_em);
