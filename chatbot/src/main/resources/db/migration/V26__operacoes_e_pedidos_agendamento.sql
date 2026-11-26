-- HU05.1, HU05.2 e HU06.2 (Pedro Martins).
-- Tabelas novas; a tabela tickets e os dados existentes nao sao alterados.

-- Chave da operacao de origem (ex.: a atualizacao do Telegram que pediu o registro).
-- Repetir a mesma operacao devolve a mesma solicitacao em vez de criar outra.
-- A chave primaria tambem barra duas transacoes simultaneas com a mesma chave.
CREATE TABLE operacoes_solicitacao (
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    chave_operacao VARCHAR(120) NOT NULL,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id),
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_operacoes_solicitacao PRIMARY KEY (empresa_id, chave_operacao),
    CONSTRAINT uk_operacao_ticket UNIQUE (ticket_id)
);

-- Preferencia de horario informada pelo cliente em pedidos de demonstracao,
-- reuniao ou visita tecnica. E apenas preferencia: o horario ainda depende da equipe.
CREATE TABLE preferencias_agendamento (
    ticket_id BIGINT PRIMARY KEY REFERENCES tickets(id),
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    preferencia_horario VARCHAR(300) NOT NULL,
    CONSTRAINT ck_preferencia_preenchida CHECK (LENGTH(TRIM(preferencia_horario)) > 0)
);

CREATE INDEX ix_preferencias_agendamento_empresa ON preferencias_agendamento (empresa_id, ticket_id);
