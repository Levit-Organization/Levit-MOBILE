<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateAutomacaoAcaoTable extends Migration
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
            'tipo' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
            ],
            'configuracao jsonb',
            'ordem' => [
                'type'    => 'INTEGER',
                'default' => 0,
            ],
        ]);

        $this->forge->addPrimaryKey('id');
        $this->forge->addKey('automacao_id');
        $this->forge->addForeignKey('automacao_id', 'automacao', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('automacao_acao');
    }

    public function down()
    {
        $this->forge->dropTable('automacao_acao');
    }
}