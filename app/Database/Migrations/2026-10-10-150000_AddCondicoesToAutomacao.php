<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Condições múltiplas (E/OU) em JSON. As automações com a condição única antiga
 * são convertidas; as colunas antigas continuam existindo, mas deixam de ser
 * preenchidas em automações novas.
 */
class AddCondicoesToAutomacao extends Migration
{
    public function up()
    {
        $this->forge->addColumn('automacao', [
            'condicoes' => [
                'type' => 'TEXT',
                'null' => true,
            ],
        ]);

        $antigas = $this->db->table('automacao')
            ->select('id, campo_condicao_id, condicao_operador, condicao_valor, condicao_valor_de')
            ->where('campo_condicao_id IS NOT NULL', null, false)
            ->get()
            ->getResultArray();

        foreach ($antigas as $linha) {
            $this->db->table('automacao')->where('id', $linha['id'])->update([
                'condicoes' => json_encode([
                    'logica' => 'e',
                    'regras' => [[
                        'campo'    => $linha['campo_condicao_id'],
                        'operador' => $linha['condicao_operador'] ?: 'igual',
                        'valor'    => $linha['condicao_valor'],
                        'valor_de' => $linha['condicao_valor_de'],
                    ]],
                ]),
            ]);
        }
    }

    public function down()
    {
        $this->forge->dropColumn('automacao', 'condicoes');
    }
}
