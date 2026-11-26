<?php

namespace App\Services\Modulos;

use App\Models\CampoModuloModel;
use App\Models\ModuloModel;
use App\Services\AutorizacaoModuloService;

/**
 * Ligações de uma tabela com as outras, para a tela da tabela: para onde
 * apontam as colunas "vinculo" dela e quais colunas de outras tabelas
 * apontam para ela.
 */
class LigacoesDoModulo
{
    public function __construct(
        protected ModuloModel $moduloModel,
        protected CampoModuloModel $campoModuloModel,
        protected AutorizacaoModuloService $autorizacaoModuloService,
    ) {
    }

    /**
     * Em cada coluna "vinculo", a tabela para onde ela aponta
     * (`modulo_vinculado`); em `ligacoes_recebidas`, as colunas de outras
     * tabelas que apontam para esta. Vêm com as colunas de cada tabela, que a
     * tela usa para filtrar pelos dados ligados. Tabela que a pessoa não vê
     * aparece só com o nome (sem colunas) nas colunas desta, e some das
     * ligações recebidas.
     *
     * `$modulo` já vem com `campos`.
     */
    public function anexar(array $modulo, string $cargoId, bool $acessoTotal): array
    {
        $recebidas = $this->campoModuloModel
            ->select('campo_modulo.id, campo_modulo.nome, campo_modulo.modulo_id')
            ->join('modulo', 'modulo.id = campo_modulo.modulo_id')
            ->where('campo_modulo.modulo_vinculado_id', $modulo['id'])
            ->orderBy('modulo.nome', 'ASC')
            ->orderBy('campo_modulo.ordem', 'ASC')
            ->findAll();

        $alvos = [];
        foreach ($modulo['campos'] as $campo) {
            if ($campo['tipo'] === 'vinculo' && ! empty($campo['modulo_vinculado_id'])) {
                $alvos[] = $campo['modulo_vinculado_id'];
            }
        }

        $envolvidos = array_values(array_unique([...$alvos, ...array_column($recebidas, 'modulo_id')]));
        if ($envolvidos === []) {
            $modulo['ligacoes_recebidas'] = [];

            return $modulo;
        }

        $visiveis = array_flip($this->autorizacaoModuloService->filtrarModulosComNivel($envolvidos, $acessoTotal, $cargoId, 'visualizar'));
        $modulos  = array_column($this->moduloModel->select('id, nome, icone')->whereIn('id', $envolvidos)->findAll(), null, 'id');

        $camposPorModulo = [];
        if ($visiveis !== []) {
            $linhas = $this->campoModuloModel
                ->select('id, modulo_id, nome, tipo, opcoes, modulo_vinculado_id')
                ->whereIn('modulo_id', array_keys($visiveis))
                ->orderBy('ordem', 'ASC')
                ->findAll();
            foreach ($linhas as $linha) {
                $camposPorModulo[$linha['modulo_id']][] = $linha;
            }
        }

        $resumo = static fn (string $id): array => [
            'id'     => $id,
            'nome'   => $modulos[$id]['nome'] ?? '',
            'icone'  => $modulos[$id]['icone'] ?? null,
            'acesso' => isset($visiveis[$id]),
            'campos' => $camposPorModulo[$id] ?? [],
        ];

        foreach ($modulo['campos'] as &$campo) {
            if ($campo['tipo'] === 'vinculo' && ! empty($campo['modulo_vinculado_id'])) {
                $campo['modulo_vinculado'] = $resumo($campo['modulo_vinculado_id']);
            }
        }
        unset($campo);

        $modulo['ligacoes_recebidas'] = [];
        foreach ($recebidas as $recebida) {
            if (isset($visiveis[$recebida['modulo_id']])) {
                $modulo['ligacoes_recebidas'][] = [
                    'campo_id'   => $recebida['id'],
                    'campo_nome' => $recebida['nome'],
                    'modulo'     => $resumo($recebida['modulo_id']),
                ];
            }
        }

        return $modulo;
    }
}
