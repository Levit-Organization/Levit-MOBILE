<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateModuloTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'empresa_id' => [
                'type' => 'UUID',
            ],
            'nome' => [
                'type'       => 'VARCHAR',
                'constraint' => 100,
            ],
            'icone' => [
                'type'       => 'VARCHAR',
                'constraint' => 50,
                'null'       => true,
            ],
            'criado_por' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
            'atualizado_em' => [
                'type' => 'TIMESTAMP',
                'null' => true,
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addKey('empresa_id');
        $this->forge->addForeignKey('empresa_id', 'empresa', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('criado_por', 'usuario', 'id', 'CASCADE', 'SET NULL');
        $this->forge->createTable('modulo');
    }

    public function down()
    {
        $this->forge->dropTable('modulo');
    }
}