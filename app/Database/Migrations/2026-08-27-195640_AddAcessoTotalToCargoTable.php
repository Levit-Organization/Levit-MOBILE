<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AddAcessoTotalToCargoTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('cargo', [
            'acesso_total' => [
                'type'    => 'BOOLEAN',
                'default' => false,
            ],
        ]);

        // Backfill: todo cargo que já possuía TODAS as permissões existentes
        // até este momento (ou seja, já era um "Admin" completo antes desta
        // reformulação) recebe acesso_total automaticamente.
        $this->db->query(
            "UPDATE cargo SET acesso_total = true
             WHERE id IN (
                 SELECT cargo_id FROM cargo_permissao
                 GROUP BY cargo_id
                 HAVING COUNT(DISTINCT permissao_id) = (SELECT COUNT(*) FROM permissao)
             )"
        );
    }

    public function down()
    {
        $this->forge->dropColumn('cargo', 'acesso_total');
    }
}