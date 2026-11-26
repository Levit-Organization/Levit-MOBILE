<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AddCargoIdToUsuarioTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('usuario', [
            'cargo_id' => [
                'type' => 'UUID',
                'null' => false,
            ],
        ]);

        $this->db->query(
            'ALTER TABLE usuario
             ADD CONSTRAINT usuario_cargo_id_foreign
             FOREIGN KEY (cargo_id) REFERENCES cargo (id)
             ON UPDATE CASCADE ON DELETE RESTRICT'
        );
    }

    public function down()
    {
        $this->db->query('ALTER TABLE usuario DROP CONSTRAINT usuario_cargo_id_foreign');
        $this->forge->dropColumn('usuario', 'cargo_id');
    }
}