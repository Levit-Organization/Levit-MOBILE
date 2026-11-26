<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Itens que se repetem (coluna "Repetição"): quando um é concluído, o Levit
 * cria o próximo e guarda aqui de qual item ele veio. Serve para nunca criar
 * duas ocorrências a partir do mesmo item, mesmo que ele seja reaberto e
 * concluído de novo.
 */
class AddRecorrenciaOrigemToRegistro extends Migration
{
    public function up()
    {
        $this->forge->addColumn('registro', [
            'recorrencia_origem_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
        ]);
        $this->db->query('CREATE INDEX IF NOT EXISTS registro_recorrencia_origem_idx ON registro (recorrencia_origem_id) WHERE recorrencia_origem_id IS NOT NULL');
    }

    public function down()
    {
        $this->db->query('DROP INDEX IF EXISTS registro_recorrencia_origem_idx');
        $this->forge->dropColumn('registro', 'recorrencia_origem_id');
    }
}
