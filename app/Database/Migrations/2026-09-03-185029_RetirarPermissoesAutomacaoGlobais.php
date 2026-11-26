<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class RetirarPermissoesAutomacaoGlobais extends Migration
{
    private array $codigos = ['visualizar_automacoes', 'gerenciar_automacoes'];

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
        // Não recriamos — a decisão de design foi abandonar o modelo
        // global em favor de níveis por módulo, não uma reversão de dado.
    }
}