<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AddResetPasswordFieldsToUsuario extends Migration
{
    public function up()
    {
        $this->forge->addColumn('usuario', [
            'reset_hash' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
                'null'       => true,
            ],
            'reset_expires_at' => [
                'type' => 'TIMESTAMP',
                'null' => true,
            ],
        ]);
    }

    public function down()
    {
        $this->forge->dropColumn('usuario', ['reset_hash', 'reset_expires_at']);
    }
}
