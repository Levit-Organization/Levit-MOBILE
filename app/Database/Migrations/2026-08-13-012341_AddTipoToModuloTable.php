<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AddTipoToModuloTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('modulo', [
            'tipo' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
                'default'    => 'dados',
            ],
        ]);
    }

    public function down()
    {
        $this->forge->dropColumn('modulo', 'tipo');
    }
}