<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AlterHistoricoFaseColumnsToUuid extends Migration
{
    public function up()
    {
        // A tabela nunca chegou a ser usada de verdade nesta sessão
        // (o CandidatoService antigo nunca gravou nada nela) — sem
        // dado real pra migrar, é seguro apagar e recriar as colunas.
        $this->db->query('ALTER TABLE historico_fase DROP COLUMN fase_anterior');
        $this->db->query('ALTER TABLE historico_fase DROP COLUMN fase_nova');

        $this->forge->addColumn('historico_fase', [
            'fase_anterior_id' => ['type' => 'UUID', 'null' => true],
            'fase_nova_id'     => ['type' => 'UUID', 'null' => true],
        ]);

        $this->db->query(
            'ALTER TABLE historico_fase
             ADD CONSTRAINT historico_fase_fase_anterior_id_foreign
             FOREIGN KEY (fase_anterior_id) REFERENCES fase_recrutamento (id)
             ON UPDATE CASCADE ON DELETE SET NULL'
        );

        $this->db->query(
            'ALTER TABLE historico_fase
             ADD CONSTRAINT historico_fase_fase_nova_id_foreign
             FOREIGN KEY (fase_nova_id) REFERENCES fase_recrutamento (id)
             ON UPDATE CASCADE ON DELETE SET NULL'
        );
    }

    public function down()
    {
        $this->db->query('ALTER TABLE historico_fase DROP CONSTRAINT historico_fase_fase_nova_id_foreign');
        $this->db->query('ALTER TABLE historico_fase DROP CONSTRAINT historico_fase_fase_anterior_id_foreign');
        $this->forge->dropColumn('historico_fase', 'fase_nova_id');
        $this->forge->dropColumn('historico_fase', 'fase_anterior_id');

        $this->forge->addColumn('historico_fase', [
            'fase_anterior' => ['type' => 'VARCHAR', 'constraint' => 20],
            'fase_nova'     => ['type' => 'VARCHAR', 'constraint' => 20],
        ]);
    }
}