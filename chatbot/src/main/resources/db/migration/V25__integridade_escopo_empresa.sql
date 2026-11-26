-- As chaves compostas impedem que IDs individualmente validos sejam combinados
-- entre empresas, clientes, conversas e atendentes diferentes.
ALTER TABLE clientes
    ADD CONSTRAINT uk_clientes_id_empresa UNIQUE (id, empresa_id);

ALTER TABLE chatbots
    ADD CONSTRAINT uk_chatbots_id_empresa UNIQUE (id, empresa_id);

ALTER TABLE atendentes
    ADD CONSTRAINT uk_atendentes_id_empresa UNIQUE (id, empresa_id);

ALTER TABLE conversas
    ADD CONSTRAINT uk_conversas_id_cliente_empresa UNIQUE (id, cliente_id, empresa_id);

ALTER TABLE conversas
    ADD CONSTRAINT uk_conversas_id_empresa UNIQUE (id, empresa_id);

ALTER TABLE conversas
    ADD CONSTRAINT fk_conversas_chatbot_empresa
    FOREIGN KEY (chatbot_id, empresa_id) REFERENCES chatbots (id, empresa_id);

ALTER TABLE conversas
    ADD CONSTRAINT fk_conversas_responsavel_empresa
    FOREIGN KEY (responsavel_id, empresa_id) REFERENCES atendentes (id, empresa_id);

ALTER TABLE telegram_contatos
    ADD CONSTRAINT fk_telegram_contato_cliente_empresa
    FOREIGN KEY (cliente_id, empresa_id) REFERENCES clientes (id, empresa_id);

ALTER TABLE telegram_contatos
    ADD CONSTRAINT fk_telegram_contato_conversa_empresa
    FOREIGN KEY (conversa_id, empresa_id) REFERENCES conversas (id, empresa_id);

ALTER TABLE telegram_atualizacoes
    ADD CONSTRAINT fk_telegram_atualizacao_conversa_empresa
    FOREIGN KEY (conversa_id, empresa_id) REFERENCES conversas (id, empresa_id);

ALTER TABLE tickets
    ADD CONSTRAINT fk_ticket_conversa_escopo
    FOREIGN KEY (conversa_origem_id, cliente_id, empresa_id)
    REFERENCES conversas (id, cliente_id, empresa_id);

ALTER TABLE tickets
    ADD CONSTRAINT fk_ticket_responsavel_empresa
    FOREIGN KEY (responsavel_id, empresa_id) REFERENCES atendentes (id, empresa_id);
