<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateAutomacaoTable extends Migration
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
            'gatilho' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
            ],
            'campo_condicao_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'condicao_operador' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
                'null'       => true,
            ],
            'condicao_valor' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
                'null'       => true,
            ],
            'ativo' => [
                'type'    => 'BOOLEAN',
                'default' => true,
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
        $this->forge->addKey('modulo_id');
        $this->forge->addForeignKey('modulo_id', 'modulo', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('campo_condicao_id', 'campo_modulo', 'id', 'CASCADE', 'RESTRICT');
        $this->forge->addForeignKey('criado_por', 'usuario', 'id', 'CASCADE', 'SET NULL');
        $this->forge->createTable('automacao');
    }

    public function down()
    {
        $this->forge->dropTable('automacao');
    }
}