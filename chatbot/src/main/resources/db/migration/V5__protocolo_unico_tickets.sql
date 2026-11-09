CREATE SEQUENCE tickets_id_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE tickets
    ADD CONSTRAINT uk_tickets_protocolo UNIQUE (protocolo);
