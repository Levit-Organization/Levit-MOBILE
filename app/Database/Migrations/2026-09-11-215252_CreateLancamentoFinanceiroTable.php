<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

class CreateLancamentoFinanceiroTable extends Migration
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
            'registro_id' => [
                'type' => 'UUID',
            ],
            'descricao' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
            ],
            'data_lancamento' => [
                'type' => 'DATE',
            ],
            // 'entrada' ou 'saida' — validado na aplicação (FinanceiroService),
            // seguindo o mesmo padrão do resto do projeto (nenhuma tabela usa
            // CHECK constraint para enum, ex: modulo.tipo, campo_modulo.tipo).
            'tipo' => [
                'type'       => 'VARCHAR',
                'constraint' => 10,
            ],
            'categoria_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
            'valor' => [
                'type'       => 'DECIMAL',
                'constraint' => '12,2',
            ],
            'conciliado' => [
                'type'    => 'BOOLEAN',
                'default' => false,
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
        $this->forge->addKey('categoria_id');
        $this->forge->addUniqueKey('registro_id');
        $this->forge->addForeignKey('modulo_id', 'modulo', 'id', 'CASCADE', 'CASCADE');
        // CASCADE aqui é proposital: excluímos um lançamento excluindo o
        // registro vinculado (mesmo padrão já usado por ArquivoService),
        // e essa FK garante que a linha de lancamento_financeiro some junto.
        $this->forge->addForeignKey('registro_id', 'registro', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addForeignKey('categoria_id', 'categoria_financeira', 'id', 'CASCADE', 'SET NULL');
        $this->forge->createTable('lancamento_financeiro');
    }

    public function down()
    {
        $this->forge->dropTable('lancamento_financeiro');
    }
}