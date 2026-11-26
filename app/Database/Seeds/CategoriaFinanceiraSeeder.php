<?php

namespace App\Database\Seeds;

use CodeIgniter\Database\Seeder;

/**
 * Cria as categorias financeiras iniciais em todas as empresas que ainda não
 * as têm. Substitui a antiga rota pública `api/v1/seed/categorias`, que gravava
 * em todas as empresas sem autenticação: agora só roda pela linha de comando.
 *
 *   php spark db:seed CategoriaFinanceiraSeeder
 */
class CategoriaFinanceiraSeeder extends Seeder
{
    public function run()
    {
        $categoriasIniciais = ['Fixo', 'Receita', 'Variável', 'Investimento'];

        $empresas = $this->db->table('empresa')->select('id')->get()->getResultArray();
        if (empty($empresas)) {
            echo "Nenhuma empresa encontrada no banco.\n";
            return;
        }

        $criadas = 0;
        foreach ($empresas as $empresa) {
            foreach ($categoriasIniciais as $nome) {
                $jaExiste = $this->db->table('categoria_financeira')
                    ->where('empresa_id', $empresa['id'])
                    ->where('nome', $nome)
                    ->get()
                    ->getRow();

                if ($jaExiste) {
                    continue;
                }

                model('CategoriaFinanceiraModel')->insert([
                    'empresa_id' => $empresa['id'],
                    'nome'       => $nome,
                ]);
                $criadas++;
            }
        }

        echo "Categorias criadas: {$criadas}.\n";
    }
}
