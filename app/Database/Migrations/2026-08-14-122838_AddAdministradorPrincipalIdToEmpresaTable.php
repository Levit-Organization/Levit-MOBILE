<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AddAdministradorPrincipalIdToEmpresaTable extends Migration
{
    public function up()
    {
        $this->forge->addColumn('empresa', [
            'administrador_principal_id' => [
                'type' => 'UUID',
                'null' => true,
            ],
        ]);

        $this->db->query(
            'ALTER TABLE empresa
             ADD CONSTRAINT empresa_administrador_principal_id_foreign
             FOREIGN KEY (administrador_principal_id) REFERENCES usuario (id)
             ON UPDATE CASCADE ON DELETE SET NULL'
        );
    }

    public function down()
    {
        $this->db->query('ALTER TABLE empresa DROP CONSTRAINT empresa_administrador_principal_id_foreign');
        $this->forge->dropColumn('empresa', 'administrador_principal_id');
    }
}