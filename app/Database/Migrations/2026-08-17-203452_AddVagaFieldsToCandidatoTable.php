<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AddVagaFieldsToCandidatoTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('candidato', [
            'modulo_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'fase_atual_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
        ]);

        $this->db->query(
            'ALTER TABLE candidato
             ADD CONSTRAINT candidato_modulo_id_foreign
             FOREIGN KEY (modulo_id) REFERENCES modulo (id)
             ON UPDATE CASCADE ON DELETE CASCADE'
        );

        $this->db->query(
            'ALTER TABLE candidato
             ADD CONSTRAINT candidato_fase_atual_id_foreign
             FOREIGN KEY (fase_atual_id) REFERENCES fase_recrutamento (id)
             ON UPDATE CASCADE ON DELETE RESTRICT'
        );
    }

    public function down()
    {
        $this->db->query('ALTER TABLE candidato DROP CONSTRAINT candidato_fase_atual_id_foreign');
        $this->db->query('ALTER TABLE candidato DROP CONSTRAINT candidato_modulo_id_foreign');
        $this->forge->dropColumn('candidato', 'fase_atual_id');
        $this->forge->dropColumn('candidato', 'modulo_id');
    }
}