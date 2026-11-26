<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Valor de origem da condição "mudou de A para B" (o destino continua em
 * `condicao_valor`).
 */
class AddCondicaoValorDeToAutomacao extends Migration
{
    public function up()
    {
        $this->forge->addColumn('automacao', [
            'condicao_valor_de' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
                'null'       => true,
            ],
        ]);
    }

    public function down()
    {
        $this->forge->dropColumn('automacao', 'condicao_valor_de');
    }
}
