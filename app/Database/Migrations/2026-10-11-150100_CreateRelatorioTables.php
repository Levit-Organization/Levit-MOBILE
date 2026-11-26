<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;
use CodeIgniter\Database\RawSql;

/**
 * Relatórios agendados (semanal ou mensal, num dia escolhido), o histórico de
 * cada envio e o controle das rotinas diárias (para nunca rodarem duas vezes
 * no mesmo dia, mesmo que o cron dispare de novo).
 */
class CreateRelatorioTables extends Migration
{
    public function up()
    {
        $this->forge->addField([
            'id'         => ['type' => 'UUID', 'default' => new RawSql('uuidv7()')],
            'empresa_id' => ['type' => 'UUID'],
            'nome'       => ['type' => 'VARCHAR', 'constraint' => 100],
            // semanal: dia 1 (segunda) a 7 (domingo). mensal: dia 1 a 28.
            'frequencia' => ['type' => 'VARCHAR', 'constraint' => 10],
            'dia'        => ['type' => 'SMALLINT'],
            // Ids de quem recebe o PDF por e-mail.
            'destinatarios jsonb NOT NULL DEFAULT \'[]\'',
            'ativo'           => ['type' => 'BOOLEAN', 'default' => true],
            'criado_por'      => ['type' => 'UUID', 'null' => true],
            'ultimo_envio_em' => ['type' => 'TIMESTAMP', 'null' => true],
            'criado_em'       => ['type' => 'TIMESTAMP', 'default' => new RawSql('CURRENT_TIMESTAMP')],
            'atualizado_em'   => ['type' => 'TIMESTAMP', 'null' => true],
        ]);
        $this->forge->addPrimaryKey('id');
        $this->forge->addForeignKey('empresa_id', 'empresa', 'id', 'CASCADE', 'CASCADE');
        $this->forge->addKey('empresa_id');
        $this->forge->createTable('relatorio_agendado');

        $this->forge->addField([
            'id'               => ['type' => 'UUID', 'default' => new RawSql('uuidv7()')],
            'relatorio_id'     => ['type' => 'UUID'],
            'periodo_inicio'   => ['type' => 'TIMESTAMP'],
            'periodo_fim'      => ['type' => 'TIMESTAMP'],
            'total_atividades' => ['type' => 'INTEGER', 'default' => 0],
            // enviado | erro
            'status'           => ['type' => 'VARCHAR', 'constraint' => 10],
            'erro'             => ['type' => 'TEXT', 'null' => true],
            'enviado_em'       => ['type' => 'TIMESTAMP', 'default' => new RawSql('CURRENT_TIMESTAMP')],
        ]);
        $this->forge->addPrimaryKey('id');
        $this->forge->addForeignKey('relatorio_id', 'relatorio_agendado', 'id', 'CASCADE', 'CASCADE');
        $this->forge->createTable('relatorio_envio');
        $this->db->query('CREATE INDEX relatorio_envio_relatorio_idx ON relatorio_envio (relatorio_id, enviado_em DESC)');

        $this->forge->addField([
            'rotina'       => ['type' => 'VARCHAR', 'constraint' => 60],
            'referencia'   => ['type' => 'VARCHAR', 'constraint' => 60],
            'executada_em' => ['type' => 'TIMESTAMP', 'default' => new RawSql('CURRENT_TIMESTAMP')],
        ]);
        $this->forge->addPrimaryKey(['rotina', 'referencia']);
        $this->forge->createTable('rotina_execucao');
    }

    public function down()
    {
        $this->forge->dropTable('rotina_execucao');
        $this->forge->dropTable('relatorio_envio');
        $this->forge->dropTable('relatorio_agendado');
    }
}
