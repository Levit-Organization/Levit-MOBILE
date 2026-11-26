<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateCargoModuloPermissaoTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'cargo_id' => [
                'type' => 'UUID',
            ],
            'modulo_id' => [
                'type' => 'UUID',
            ],
            'nivel' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addKey('cargo_id');
        $this->forge->addKey('modulo_id');
        $this->forge->addUniqueKey(['cargo_id', 'modulo_id']);
        $this->forge->addForeignKey('cargo_id', 'cargo', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('modulo_id', 'modulo', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('cargo_modulo_permissao');
    }

    public function down()
    {
        $this->forge->dropTable('cargo_modulo_permissao');
    }
}