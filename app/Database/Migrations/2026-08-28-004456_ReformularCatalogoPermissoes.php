<?php

namespace App\Database\Migrations;

use CodeIgniter\Database\Migration;

class ReformularCatalogoPermissoes extends Migration
{
    private array $renomeacoes = [
        'gerenciar_modulos' => 'criar_modulos',
    ];

    private array $novasPermissoes = [
        ['codigo' => 'visualizar_equipe',      'descricao' => 'Ver a lista de membros da equipe'],
        ['codigo' => 'gerenciar_cargos',       'descricao' => 'Criar e configurar cargos e suas permissões'],
        ['codigo' => 'visualizar_automacoes',  'descricao' => 'Ver as automações configuradas'],
        ['codigo' => 'exportar_dados',         'descricao' => 'Exportar backup e CSV dos módulos'],
        ['codigo' => 'resetar_dados',          'descricao' => 'Resetar os dados da empresa (exige também ser administrador principal)'],
    ];

    public function up()
    {
        helper('uuid');

        // 1. Renomear códigos que continuam existindo
        foreach ($this->renomeacoes as $antigo => $novo) {
            $this->db->query('UPDATE permissao SET codigo = ? WHERE codigo = ?', [$novo, $antigo]);
        }

        // 2. Criar as permissões novas (idempotente, como já fazemos no Seeder)
        foreach ($this->novasPermissoes as $permissao) {
            $existe = $this->db->table('permissao')->where('codigo', $permissao['codigo'])->get()->getRow();

            if (! $existe) {
                $this->db->table('permissao')->insert([
                    'id'        => generate_uuid_v7(),
                    'codigo'    => $permissao['codigo'],
                    'descricao' => $permissao['descricao'],
                ]);
            }
        }

        // 3. Aposentar gerenciar_recrutamento — mas antes, propagar o acesso:
        //    todo cargo que tinha essa permissão ganha "gerenciar" em TODOS
        //    os módulos de recrutamento que já existem hoje, pra ninguém
        //    perder o acesso que já tinha na hora da troca.
        $permissaoAntiga = $this->db->table('permissao')->where('codigo', 'gerenciar_recrutamento')->get()->getRow();

        if ($permissaoAntiga) {
            $cargosComRecrutamento = $this->db->table('cargo_permissao')
                ->where('permissao_id', $permissaoAntiga->id)
                ->get()
                ->getResultArray();

            $modulosRecrutamento = $this->db->table('modulo')
                ->where('tipo', 'recrutamento')
                ->get()
                ->getResultArray();

            foreach ($cargosComRecrutamento as $vinculo) {
                foreach ($modulosRecrutamento as $modulo) {
                    $jaTem = $this->db->table('cargo_modulo_permissao')
                        ->where('cargo_id', $vinculo['cargo_id'])
                        ->where('modulo_id', $modulo['id'])
                        ->get()
                        ->getRow();

                    if (! $jaTem) {
                        $this->db->table('cargo_modulo_permissao')->insert([
                            'id'        => generate_uuid_v7(),
                            'cargo_id'  => $vinculo['cargo_id'],
                            'modulo_id' => $modulo['id'],
                            'nivel'     => 'gerenciar',
                        ]);
                    }
                }
            }

            $this->db->table('cargo_permissao')->where('permissao_id', $permissaoAntiga->id)->delete();
            $this->db->table('permissao')->where('id', $permissaoAntiga->id)->delete();
        }

        // 4. gerenciar_dados divide-se em duas — todo cargo que tinha a
        //    antiga ganha AMBAS as novas, preservando o que já podia fazer.
        $permissaoDadosAntiga = $this->db->table('permissao')->where('codigo', 'gerenciar_dados')->get()->getRow();

        if ($permissaoDadosAntiga) {
            $cargosComDados = $this->db->table('cargo_permissao')
                ->where('permissao_id', $permissaoDadosAntiga->id)
                ->get()
                ->getResultArray();

            $novasDados = $this->db->table('permissao')->whereIn('codigo', ['exportar_dados', 'resetar_dados'])->get()->getResultArray();

            foreach ($cargosComDados as $vinculo) {
                foreach ($novasDados as $novaPermissao) {
                    $jaTem = $this->db->table('cargo_permissao')
                        ->where('cargo_id', $vinculo['cargo_id'])
                        ->where('permissao_id', $novaPermissao['id'])
                        ->get()
                        ->getRow();

                    if (! $jaTem) {
                        $this->db->table('cargo_permissao')->insert([
                            'id'            => generate_uuid_v7(),
                            'cargo_id'      => $vinculo['cargo_id'],
                            'permissao_id'  => $novaPermissao['id'],
                        ]);
                    }
                }
            }

            $this->db->table('cargo_permissao')->where('permissao_id', $permissaoDadosAntiga->id)->delete();
            $this->db->table('permissao')->where('id', $permissaoDadosAntiga->id)->delete();
        }
    }

    public function down()
    {
        // Reversão simplificada: renomeia de volta o que foi renomeado.
        // A recriação exata dos vínculos antigos de gerenciar_recrutamento
        // e gerenciar_dados não é reconstruída automaticamente aqui, dado
        // que a migração de dados nesse sentido é ambígua (informação já
        // foi convertida em granularidade maior).
        foreach ($this->renomeacoes as $antigo => $novo) {
            $this->db->query('UPDATE permissao SET codigo = ? WHERE codigo = ?', [$antigo, $novo]);
        }
    }
}