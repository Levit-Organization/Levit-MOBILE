<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateRegistroTable extends Migration
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
            'dados jsonb',
            'criado_por' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'atualizado_por' => [
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
        $this->forge->addKey('modulo_id');
        $this->forge->addForeignKey('modulo_id', 'modulo', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('criado_por', 'usuario', 'id', 'CASCADE', 'SET NULL');
        $this->forge->addForeignKey('atualizado_por', 'usuario', 'id', 'CASCADE', 'SET NULL');
        $this->forge->createTable('registro');
    }

    public function down()
    {
        $this->forge->dropTable('registro');
    }
}