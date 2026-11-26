<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Cada relatório agendado escolhe as partes do PDF (resumo comparativo,
 * recrutamento, financeiro, prazos, atrasados, ranking, atividades). Nulo
 * mantém o formato antigo para quem já tinha relatório agendado.
 *
 * Índice em candidato para contar inscritos por período nos indicadores.
 */
class AddSecoesToRelatorioAgendado extends Migration
{
    public function up()
    {
        $this->db->query('ALTER TABLE relatorio_agendado ADD COLUMN IF NOT EXISTS secoes jsonb NULL');
        $this->db->query('CREATE INDEX IF NOT EXISTS candidato_modulo_criado_idx ON candidato (modulo_id, criado_em)');
    }

    public function down()
    {
        $this->db->query('DROP INDEX IF EXISTS candidato_modulo_criado_idx');
        $this->db->query('ALTER TABLE relatorio_agendado DROP COLUMN IF EXISTS secoes');
    }
}
