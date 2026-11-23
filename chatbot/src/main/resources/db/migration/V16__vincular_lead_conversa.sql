CREATE TABLE lead_conversas (
 lead_id BIGINT PRIMARY KEY REFERENCES leads(id),
 conversa_id BIGINT NOT NULL REFERENCES conversas(id),
 empresa_id BIGINT NOT NULL REFERENCES empresas(id)
);
