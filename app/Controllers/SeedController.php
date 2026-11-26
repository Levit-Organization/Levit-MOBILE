<?php

namespace App\Controllers;

use App\Models\EmpresaModel;
use App\Models\CategoriaFinanceiraModel;
use CodeIgniter\HTTP\ResponseInterface;

class SeedController extends BaseController
{
    /**
     * Utilitário de desenvolvimento. A rota não tem autenticação e grava em
     * todas as empresas, então fora de `development` responde 404 (a rota
     * continua existindo para não mudar o mapa da API).
     */
    public function seedCategorias(): ResponseInterface
    {
        if (ENVIRONMENT !== 'development') {
            return $this->response->setStatusCode(404)->setJSON(['status' => 'error', 'message' => 'Recurso não encontrado.']);
        }

        $empresaModel = new EmpresaModel();
        $categoriaModel = new CategoriaFinanceiraModel();

        $empresas = $empresaModel->findAll();
        if (empty($empresas)) {
            return $this->response->setJSON(['error' => 'Nenhuma empresa encontrada no banco.']);
        }

        $categoriasIniciais = ['Fixo', 'Receita', 'Variável', 'Investimento'];
        $resultados = [];

        foreach ($empresas as $empresa) {
            $empresaId = $empresa['id'];
            foreach ($categoriasIniciais as $nome) {
                $existe = $categoriaModel->where('empresa_id', $empresaId)->where('nome', $nome)->first();
                if (!$existe) {
                    $categoriaModel->insert([
                        'empresa_id' => $empresaId,
                        'nome' => $nome
                    ]);
                    $resultados[] = "Categoria '$nome' criada para a empresa {$empresaId}";
                }
            }
        }
        return $this->response->setJSON(['status' => 'ok', 'resultados' => $resultados]);
    }
}
