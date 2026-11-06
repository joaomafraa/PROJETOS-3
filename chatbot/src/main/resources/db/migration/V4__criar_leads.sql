CREATE SEQUENCE leads_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE leads (
    id BIGINT PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresas(id),
    nome VARCHAR(255),
    tipo_contato VARCHAR(16) NOT NULL,
    contato VARCHAR(254) NOT NULL,
    origem VARCHAR(32) NOT NULL,
    necessidade VARCHAR(500) NOT NULL,
    necessidade_chave VARCHAR(500) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_lead_tipo_contato CHECK (tipo_contato IN ('EMAIL', 'TELEFONE')),
    CONSTRAINT ck_lead_origem CHECK (origem IN
        ('WHATSAPP', 'SITE', 'INDICACAO', 'EVENTO', 'OUTRO')),
    -- Mesmo contato pode ter varios interesses; so a repeticao do mesmo interesse e duplicata.
    CONSTRAINT uk_lead_empresa_contato_necessidade
        UNIQUE (empresa_id, contato, necessidade_chave)
);

CREATE INDEX idx_leads_empresa_criacao ON leads (empresa_id, data_criacao, id);
