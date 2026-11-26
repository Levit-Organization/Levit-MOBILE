<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

/**
 * Currículo anexado à candidatura. O arquivo fica no storage; aqui só a chave
 * e o que é preciso para devolver o download com o nome e o tipo certos.
 */
class AddCurriculoToCandidatoTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('candidato', [
            'curriculo_chave' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
                'null'       => true,
            ],
            'curriculo_nome' => [
                'type'       => 'VARCHAR',
                'constraint' => 255,
                'null'       => true,
            ],
            'curriculo_mime' => [
                'type'       => 'VARCHAR',
                'constraint' => 100,
                'null'       => true,
            ],
            'curriculo_tamanho' => [
                'type' => 'BIGINT',
                'null' => true,
            ],
        ]);
    }

    public function down()
    {
        $this->forge->dropColumn('candidato', ['curriculo_chave', 'curriculo_nome', 'curriculo_mime', 'curriculo_tamanho']);
    }
}
