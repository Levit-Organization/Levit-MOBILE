<?php

namespace App\Database\Seeds;

use CodeIgniter\Database\Seeder;

class PermissaoSeeder extends Seeder
{
    public function run()
    {
        helper('uuid');

        $permissoes = [
            ['codigo' => 'criar_modulos',     'descricao' => 'Criar novas tabelas'],
            ['codigo' => 'visualizar_equipe', 'descricao' => 'Ver a lista de membros da equipe'],
            ['codigo' => 'gerenciar_equipe',  'descricao' => 'Convidar e remover membros da equipe'],
            ['codigo' => 'gerenciar_cargos',  'descricao' => 'Criar e configurar cargos e suas permissões'],
            ['codigo' => 'exportar_dados',    'descricao' => 'Exportar backup e CSV das tabelas'],
            ['codigo' => 'resetar_dados',     'descricao' => 'Resetar os dados da empresa (exige também ser administrador principal)'],
            ['codigo' => 'ver_relatorios',    'descricao' => 'Visualizar relatórios e exportações'],
        ];

        foreach ($permissoes as $permissao) {
            $jaExiste = $this->db->table('permissao')
                ->where('codigo', $permissao['codigo'])
                ->get()
                ->getRow();

            if ($jaExiste) {
                continue;
            }

            $this->db->table('permissao')->insert([
                'id'        => generate_uuid_v7(),
                'codigo'    => $permissao['codigo'],
                'descricao' => $permissao['descricao'],
            ]);
        }
    }
}