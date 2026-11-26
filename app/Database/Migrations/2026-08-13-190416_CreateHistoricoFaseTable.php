<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateHistoricoFaseTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'candidato_id' => [
                'type' => 'UUID',
            ],
            'fase_anterior' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
            ],
            'fase_nova' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
            ],
            'alterado_por' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'criado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addKey('candidato_id');
        $this->forge->addForeignKey('candidato_id', 'candidato', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('alterado_por', 'usuario', 'id', 'CASCADE', 'SET NULL');
        $this->forge->createTable('historico_fase');
    }

    public function down()
    {
        $this->forge->dropTable('historico_fase');
    }
}