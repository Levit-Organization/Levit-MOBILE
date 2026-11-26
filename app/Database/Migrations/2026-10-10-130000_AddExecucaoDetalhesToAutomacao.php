<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Histórico de execuções detalhado (passos, duração, tentativa, modo) e pausa
 * automática de automações que falham repetidamente.
 */
class AddExecucaoDetalhesToAutomacao extends Migration
{
    public function up()
    {
        $this->forge->addColumn('automacao_log', [
            'gatilho' => [
                'type'       => 'VARCHAR',
                'constraint' => 20,
                'null'       => true,
            ],
            'tentativa' => [
                'type'    => 'INTEGER',
                'default' => 1,
            ],
            'duracao_ms' => [
                'type' => 'INTEGER',
                'null' => true,
            ],
            // [{ tipo, status, mensagem, duracao_ms }] — um item por ação.
            'passos' => [
                'type' => 'TEXT',
                'null' => true,
            ],
            // Cópia dos dados do registro no momento do disparo: permite reexecutar
            // mesmo que o registro tenha mudado ou sido excluído.
            'dados' => [
                'type' => 'TEXT',
                'null' => true,
            ],
        ]);

        // A tela de histórico lista por automação, do mais recente para o mais antigo.
        $this->db->query('CREATE INDEX automacao_log_automacao_executado_idx ON automacao_log (automacao_id, executado_em)');

        $this->forge->addColumn('automacao', [
            'pausada_por_falhas_em' => [
                'type' => 'TIMESTAMP',
                'null' => true,
            ],
        ]);
    }

    public function down()
    {
        $this->forge->dropColumn('automacao', 'pausada_por_falhas_em');
        $this->db->query('DROP INDEX automacao_log_automacao_executado_idx' . ($this->db->getPlatform() === 'MySQLi' ? ' ON automacao_log' : ''));
        $this->forge->dropColumn('automacao_log', ['gatilho', 'tentativa', 'duracao_ms', 'passos', 'dados']);
    }
}
