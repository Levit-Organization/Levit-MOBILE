<?php

namespace App\Services\Modulos;

use App\Exceptions\NaoEncontradoException;
use App\Models\AutomacaoModel;
use App\Models\CampoModuloModel;
use App\Models\ModuloModel;
use App\Services\AtividadeService;
use App\Services\AutorizacaoModuloService;

/**
 * As colunas de uma tabela: validação, formato gravado em campo_modulo e
 * as alterações (incluir, renomear, reordenar, excluir). Quem chama já
 * conferiu que a tabela é da empresa e que a pessoa pode gerenciá-la.
 */
class ColunasDoModulo
{
    public const TIPOS_VALIDOS = ['texto', 'numero', 'data', 'selecao', 'responsavel', 'prazo', 'recorrencia', 'vinculo'];

    public function __construct(
        protected ModuloModel $moduloModel,
        protected CampoModuloModel $campoModuloModel,
        protected AutomacaoModel $automacaoModel,
        protected AutorizacaoModuloService $autorizacaoModuloService,
    ) {
    }

    public function validar(array $campo, string $empresaId, string $cargoId, bool $acessoTotal): void
    {
        if (empty($campo['nome'])) {
            throw new \DomainException('Toda coluna precisa de um nome.');
        }

        if (! in_array($campo['tipo'] ?? null, self::TIPOS_VALIDOS, true)) {
            $tipoRecebido = $campo['tipo'] ?? '(vazio)';
            throw new \DomainException("Tipo de coluna inválida: '{$tipoRecebido}'.");
        }

        if ($campo['tipo'] === 'selecao' && empty($campo['opcoes'])) {
            throw new \DomainException('Colunas do tipo lista de opções precisam de ao menos uma opção.');
        }

        if ($campo['tipo'] === 'vinculo') {
            $alvoId = $campo['modulo_vinculado_id'] ?? null;
            $alvo   = is_string($alvoId) && $alvoId !== ''
                ? $this->moduloModel->where('id', $alvoId)->where('empresa_id', $empresaId)->first()
                : null;

            if (! $alvo) {
                throw new \DomainException("Escolha a tabela que a coluna “{$campo['nome']}” vai ligar.");
            }
            if ($alvo['tipo'] !== 'dados') {
                throw new \DomainException('Só dá para ligar a tabelas de lista (não a arquivos, vagas ou entradas e saídas).');
            }
            // Ligar a uma tabela que a pessoa não vê exporia os itens dela.
            $this->autorizacaoModuloService->exigirNivel($acessoTotal, $cargoId, $alvoId, 'visualizar');
        }
    }

    /** Linha de campo_modulo. Ligação: guarda a tabela de destino e se aceita vários itens. */
    public function paraGravar(string $moduloId, array $campo, int $ordem): array
    {
        $vinculo = $campo['tipo'] === 'vinculo';

        return [
            'modulo_id'           => $moduloId,
            'nome'                => $campo['nome'],
            'tipo'                => $campo['tipo'],
            'opcoes'              => $vinculo ? ['multiplo' => ! empty($campo['opcoes']['multiplo'])] : ($campo['opcoes'] ?? null),
            'ordem'               => $ordem,
            'modulo_vinculado_id' => $vinculo ? $campo['modulo_vinculado_id'] : null,
        ];
    }

    /** Valida e inclui a coluna no fim da tabela. */
    public function adicionar(string $moduloId, string $empresaId, string $cargoId, bool $acessoTotal, array $campo): void
    {
        $this->validar($campo, $empresaId, $cargoId, $acessoTotal);

        $proximaOrdem = $this->campoModuloModel->where('modulo_id', $moduloId)->countAllResults();

        $this->campoModuloModel->insert($this->paraGravar($moduloId, $campo, $proximaOrdem));

        AtividadeService::registrar('coluna_criada', "Adicionou a coluna “{$campo['nome']}”", $moduloId);
    }

    /** @throws NaoEncontradoException se a coluna não for desta tabela da empresa */
    public function buscarValidada(string $campoId, string $moduloId, string $empresaId): array
    {
        $campo = $this->campoModuloModel
            ->select('campo_modulo.*')
            ->join('modulo', 'modulo.id = campo_modulo.modulo_id')
            ->where('campo_modulo.id', $campoId)
            ->where('campo_modulo.modulo_id', $moduloId)
            ->where('modulo.empresa_id', $empresaId)
            ->first();

        if (! $campo) {
            throw new NaoEncontradoException('Coluna não encontrada.');
        }

        return $campo;
    }

    public function atualizar(string $campoId, string $moduloId, string $empresaId, array $dados): void
    {
        $campo = $this->buscarValidada($campoId, $moduloId, $empresaId);

        $camposParaAtualizar = [];

        if (isset($dados['nome'])) {
            $camposParaAtualizar['nome'] = $dados['nome'];
        }

        // Ligação: destino e "um ou vários" ficam fixos depois de criada (os itens já guardam nesse formato).
        if (array_key_exists('opcoes', $dados) && $campo['tipo'] !== 'vinculo') {
            $camposParaAtualizar['opcoes'] = $dados['opcoes'];
        }

        if (! empty($camposParaAtualizar)) {
            $this->campoModuloModel->update($campoId, $camposParaAtualizar);
        }
    }

    /** `$ordemCampoIds` precisa ter exatamente as colunas da tabela. */
    public function reordenar(string $moduloId, array $ordemCampoIds): void
    {
        $idsExistentes = array_column($this->campoModuloModel->where('modulo_id', $moduloId)->findAll(), 'id');

        $listaBate = count($ordemCampoIds) === count($idsExistentes)
            && empty(array_diff($ordemCampoIds, $idsExistentes));

        if (! $listaBate) {
            throw new \DomainException('A lista enviada não corresponde exatamente às colunas desta tabela.');
        }

        $db = db_connect();
        $db->transStart();

        foreach ($ordemCampoIds as $ordem => $campoId) {
            $this->campoModuloModel->update($campoId, ['ordem' => $ordem]);
        }

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível reordenar as colunas. Tente novamente.');
        }
    }

    /** Só exclui coluna sem nenhum item preenchido e que não seja condição de automação. */
    public function excluir(string $campoId, string $moduloId, string $empresaId): void
    {
        $this->buscarValidada($campoId, $moduloId, $empresaId);

        $db = db_connect();

        $linha = $db->query(
            'SELECT COUNT(*) AS total FROM registro WHERE modulo_id = ? AND jsonb_exists(dados, ?)',
            [$moduloId, $campoId]
        )->getRow();

        if ((int) $linha->total > 0) {
            throw new \DomainException('Não é possível excluir uma coluna que já possui itens preenchidos.');
        }

        if ($this->automacaoModel->where('campo_condicao_id', $campoId)->first()) {
            throw new \DomainException('Não é possível excluir uma coluna usada como condição em uma automação.');
        }

        $nomeCampo = $this->campoModuloModel->select('nome')->find($campoId)['nome'] ?? 'uma coluna';
        $this->campoModuloModel->delete($campoId);
        AtividadeService::registrar('coluna_excluida', "Excluiu a coluna “{$nomeCampo}”", $moduloId);
    }
}
