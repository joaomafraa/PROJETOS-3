ALTER TABLE atendentes ADD COLUMN removido BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE conversas_arquivadas (
 atendente_id BIGINT NOT NULL REFERENCES atendentes(id),
 conversa_id BIGINT NOT NULL REFERENCES conversas(id),
 PRIMARY KEY (atendente_id, conversa_id)
);
