<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Uma automação passa a ter vários gatilhos (ex.: criação + atualização + exclusão
 * disparando o mesmo e-mail). A coluna `gatilho` continua existindo (primeiro
 * gatilho) para não quebrar quem ainda lê o formato antigo.
 */
class AddGatilhosToAutomacaoTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('automacao', [
            'gatilhos' => [
                'type' => 'TEXT',
                'null' => true,
            ],
        ]);

        foreach ($this->db->table('automacao')->select('id, gatilho')->get()->getResultArray() as $linha) {
            $this->db->table('automacao')
                ->where('id', $linha['id'])
                ->update(['gatilhos' => json_encode([$linha['gatilho']])]);
        }
    }

    public function down()
    {
        $this->forge->dropColumn('automacao', 'gatilhos');
    }
}
