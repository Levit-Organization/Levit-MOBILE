<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateAutomacaoLogTable extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id' => [
                'type'    => 'UUID',
                'default' => new RawSql('uuidv7()'),
            ],
            'automacao_id' => [
                'type' => 'UUID',
            ],
            'registro_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'status' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
            ],
            'detalhes' => [
                'type' => 'TEXT',
                'null' => true,
            ],
            'executado_em' => [
                'type'    => 'TIMESTAMP',
                'default' => new RawSql('CURRENT_TIMESTAMP'),
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addKey('automacao_id');
        $this->forge->addForeignKey('automacao_id', 'automacao', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('registro_id', 'registro', 'id', 'CASCADE', 'SET NULL');
        $this->forge->createTable('automacao_log');
    }

    public function down()
    {
        $this->forge->dropTable('automacao_log');
    }
}