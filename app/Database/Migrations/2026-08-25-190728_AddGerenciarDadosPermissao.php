<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class AddGerenciarDadosPermissao extends Migration
{
    public function up()
    {
        $permissaoExistente = $this->db->table('permissao')
            ->where('codigo', 'gerenciar_dados')
            ->get()
            ->getRow();

        if (! $permissaoExistente) {
            $this->db->query(
                "INSERT INTO permissao (id, codigo, descricao) VALUES (uuidv7(), 'gerenciar_dados', 'Exportar e resetar os dados da empresa')"
            );

            $permissaoExistente = $this->db->table('permissao')
                ->where('codigo', 'gerenciar_dados')
                ->get()
                ->getRow();
        }

        $novaPermissaoId = $permissaoExistente->id;

        // Backfill: todo cargo que já tinha as 5 permissões anteriores
        // (ou seja, já era um "Admin" completo) recebe a nova automaticamente.
        $cargosComTodasPermissoes = $this->db->query(
            'SELECT cargo_id FROM cargo_permissao GROUP BY cargo_id HAVING COUNT(*) = 5'
        )->getResultArray();

        foreach ($cargosComTodasPermissoes as $linha) {
            $jaTem = $this->db->table('cargo_permissao')
                ->where('cargo_id', $linha['cargo_id'])
                ->where('permissao_id', $novaPermissaoId)
                ->get()
                ->getRow();

            if (! $jaTem) {
                $this->db->query(
                    'INSERT INTO cargo_permissao (id, cargo_id, permissao_id) VALUES (uuidv7(), ?, ?)',
                    [$linha['cargo_id'], $novaPermissaoId]
                );
            }
        }
    }

    public function down()
    {
        $permissao = $this->db->table('permissao')->where('codigo', 'gerenciar_dados')->get()->getRow();

        if ($permissao) {
            $this->db->table('cargo_permissao')->where('permissao_id', $permissao->id)->delete();
            $this->db->table('permissao')->where('id', $permissao->id)->delete();
        }
    }
}