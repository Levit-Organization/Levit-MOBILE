<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class FinalizeCandidatoVagaStructure extends Migration
{
    public function up()
    {
        $this->forge->dropColumn('candidato', 'empresa_id');
        $this->forge->dropColumn('candidato', 'fase_atual');

        $this->db->query('ALTER TABLE candidato ALTER COLUMN modulo_id SET NOT NULL');
        $this->db->query('ALTER TABLE candidato ALTER COLUMN fase_atual_id SET NOT NULL');

        $this->db->query(
            'ALTER TABLE candidato
             ADD CONSTRAINT candidato_modulo_id_email UNIQUE (modulo_id, email)'
        );
    }

    public function down()
    {
        $this->db->query('ALTER TABLE candidato DROP CONSTRAINT candidato_modulo_id_email');
        $this->db->query('ALTER TABLE candidato ALTER COLUMN fase_atual_id DROP NOT NULL');
        $this->db->query('ALTER TABLE candidato ALTER COLUMN modulo_id DROP NOT NULL');

        $this->forge->addColumn('candidato', [
            'empresa_id' => ['type' => 'UUID', 'null' => true],
            'fase_atual' => ['type' => 'VARCHAR', 'constraint' => 20, 'null' => true],
        ]);
    }
}