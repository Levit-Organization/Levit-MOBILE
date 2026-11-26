<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateTokenRevogadoTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'jti' => [
                'type'       => 'VARCHAR',
                'constraint' => 36,
                'unique'     => true,
            ],
            'usuario_id' => [
                'type' => 'UUID',
            ],
            'expira_em' => [
                'type' => 'TIMESTAMP',
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addKey('usuario_id');
        $this->forge->addForeignKey('usuario_id', 'usuario', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('token_revogado');
    }

    public function down()
    {
        $this->forge->dropTable('token_revogado');
    }
}