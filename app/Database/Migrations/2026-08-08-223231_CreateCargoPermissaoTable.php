<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateCargoPermissaoTable extends Migration
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
            'permissao_id' => [
                'type' => 'UUID',
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addUniqueKey(['cargo_id', 'permissao_id']);
        $this->forge->addForeignKey('cargo_id', 'cargo', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('permissao_id', 'permissao', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('cargo_permissao');
    }

    public function down()
    {
        $this->forge->dropTable('cargo_permissao');
    }
}