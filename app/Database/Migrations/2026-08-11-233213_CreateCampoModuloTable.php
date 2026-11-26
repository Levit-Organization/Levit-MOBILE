<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateCampoModuloTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'modulo_id' => [
                'type' => 'UUID',
            ],
            'nome' => [
                'type'       => 'VARCHAR',
                'constraint' => 100,
            ],
            'tipo' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
            ],
            'opcoes jsonb',
            'ordem' => [
                'type'    => 'INTEGER',
                'default' => 0,
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
        $this->forge->addKey('modulo_id');
        $this->forge->addForeignKey('modulo_id', 'modulo', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('campo_modulo');
    }

    public function down()
    {
        $this->forge->dropTable('campo_modulo');
    }
}