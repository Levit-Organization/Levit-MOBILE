<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Uma pessoa pode fazer parte de várias equipes (empresas), com um cargo em
 * cada uma. Antes, `usuario` guardava `empresa_id` e `cargo_id` direto, o que
 * prendia cada conta a uma empresa só.
 *
 * - `membro`: o vínculo pessoa × equipe × cargo (um por equipe).
 * - `usuario.ultima_empresa_id`: a equipe aberta por último, onde o login entra.
 * - `usuario_equipe`: visão com os dados da pessoa e do vínculo, no mesmo
 *   formato que as consultas usavam (id, nome, email, empresa_id, cargo_id).
 * - `empresa.cnpj_cpf` passa a ser opcional.
 */
class PessoaEmVariasEquipes extends Migration
{
    public function up()
    {
        /** @var \CodeIgniter\Database\BaseConnection<mixed, mixed> $db */
        $db = $this->db;
        // Tudo ou nada: no Postgres, DDL também volta atrás se algo falhar no meio.
        $db->transException(true)->transStart();

        $db->query('CREATE TABLE membro (
            id          UUID PRIMARY KEY DEFAULT uuidv7(),
            usuario_id  UUID NOT NULL REFERENCES usuario (id) ON UPDATE CASCADE ON DELETE CASCADE,
            empresa_id  UUID NOT NULL REFERENCES empresa (id) ON UPDATE CASCADE ON DELETE CASCADE,
            cargo_id    UUID NOT NULL REFERENCES cargo (id) ON UPDATE CASCADE ON DELETE RESTRICT,
            criado_em   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT membro_usuario_empresa_unique UNIQUE (usuario_id, empresa_id)
        )');
        $db->query('CREATE INDEX membro_empresa_id_index ON membro (empresa_id)');
        $db->query('CREATE INDEX membro_cargo_id_index ON membro (cargo_id)');

        // Quem já existe continua na equipe e no cargo que tinha.
        $db->query('INSERT INTO membro (usuario_id, empresa_id, cargo_id, criado_em)
                    SELECT id, empresa_id, cargo_id, criado_em FROM usuario
                     WHERE empresa_id IS NOT NULL AND cargo_id IS NOT NULL');

        $db->query('ALTER TABLE usuario ADD COLUMN ultima_empresa_id UUID NULL
                    REFERENCES empresa (id) ON UPDATE CASCADE ON DELETE SET NULL');
        $db->query('UPDATE usuario SET ultima_empresa_id = empresa_id');

        // As FKs e índices das colunas saem junto com elas.
        $db->query('ALTER TABLE usuario DROP COLUMN empresa_id, DROP COLUMN cargo_id');

        $db->query('ALTER TABLE empresa ALTER COLUMN cnpj_cpf DROP NOT NULL');
        $db->query("UPDATE empresa SET cnpj_cpf = NULL WHERE TRIM(cnpj_cpf) = ''");

        $db->query('CREATE VIEW usuario_equipe AS
                    SELECT u.id, u.nome, u.email,
                           m.empresa_id, m.cargo_id,
                           m.id AS membro_id,
                           m.criado_em AS criado_em,
                           u.criado_em AS conta_criada_em
                      FROM membro m
                      JOIN usuario u ON u.id = m.usuario_id');

        $db->transComplete();
    }

    public function down()
    {
        /** @var \CodeIgniter\Database\BaseConnection<mixed, mixed> $db */
        $db = $this->db;

        $db->query('DROP VIEW IF EXISTS usuario_equipe');

        $db->query('ALTER TABLE usuario ADD COLUMN empresa_id UUID NULL, ADD COLUMN cargo_id UUID NULL');
        // Volta para uma equipe só: a mais antiga de cada pessoa.
        $db->query('UPDATE usuario u SET empresa_id = m.empresa_id, cargo_id = m.cargo_id
                      FROM (SELECT DISTINCT ON (usuario_id) usuario_id, empresa_id, cargo_id
                              FROM membro ORDER BY usuario_id, criado_em) m
                     WHERE m.usuario_id = u.id');
        $db->query('DELETE FROM usuario WHERE empresa_id IS NULL');
        $db->query('ALTER TABLE usuario ALTER COLUMN empresa_id SET NOT NULL, ALTER COLUMN cargo_id SET NOT NULL');
        $db->query('ALTER TABLE usuario ADD CONSTRAINT usuario_empresa_id_foreign FOREIGN KEY (empresa_id) REFERENCES empresa (id) ON UPDATE CASCADE ON DELETE CASCADE');
        $db->query('ALTER TABLE usuario ADD CONSTRAINT usuario_cargo_id_foreign FOREIGN KEY (cargo_id) REFERENCES cargo (id) ON UPDATE CASCADE ON DELETE RESTRICT');
        $db->query('CREATE INDEX usuario_empresa_id ON usuario (empresa_id)');

        $db->query('ALTER TABLE usuario DROP COLUMN ultima_empresa_id');
        $db->query('DROP TABLE membro');

        // A coluna é única: cada empresa sem documento ganha um marcador próprio.
        $db->query("UPDATE empresa SET cnpj_cpf = LEFT(REPLACE(id::text, '-', ''), 18) WHERE cnpj_cpf IS NULL");
        $db->query('ALTER TABLE empresa ALTER COLUMN cnpj_cpf SET NOT NULL');
    }
}
