<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class RetirarPermissoesAntigasDefinitivo extends Migration
{
    private array $codigos = ['gerenciar_dados', 'gerenciar_modulos', 'gerenciar_recrutamento'];

    public function up()
    {
        foreach ($this->codigos as $codigo) {
            $permissao = $this->db->table('permissao')->where('codigo', $codigo)->get()->getRow();

            if ($permissao) {
                $this->db->table('cargo_permissao')->where('permissao_id', $permissao->id)->delete();
                $this->db->table('permissao')->where('id', $permissao->id)->delete();
            }
        }
    }

    public function down()
    {
        // Decisão de design definitiva — sem reversão automática.
    }
}