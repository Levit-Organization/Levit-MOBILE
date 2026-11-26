<?php

namespace App\Services\Modulos;

use App\Exceptions\NaoEncontradoException;
use App\Models\CandidatoModel;
use App\Models\FaseRecrutamentoModel;
use App\Services\AtividadeService;

/**
 * As fases (etapas) de uma tabela de recrutamento: incluir, renomear,
 * reordenar e excluir. Quem chama já conferiu que a tabela é da empresa e
 * que a pessoa pode gerenciá-la.
 */
class FasesDoModulo
{
    public function __construct(
        protected FaseRecrutamentoModel $faseRecrutamentoModel,
        protected CandidatoModel $candidatoModel,
    ) {
    }

    /** Inclui a fase no fim do funil. */
    public function adicionar(string $moduloId, string $nomeFase): void
    {
        if (trim($nomeFase) === '') {
            throw new \DomainException('Toda fase precisa de um nome.');
        }

        $proximaOrdem = $this->faseRecrutamentoModel->where('modulo_id', $moduloId)->countAllResults();

        $this->faseRecrutamentoModel->insert([
            'modulo_id' => $moduloId,
            'nome'      => $nomeFase,
            'ordem'     => $proximaOrdem,
        ]);

        AtividadeService::registrar('etapa_criada', "Adicionou a etapa “{$nomeFase}”", $moduloId);
    }

    /** @throws NaoEncontradoException se a fase não for desta tabela da empresa */
    public function buscarValidada(string $faseId, string $moduloId, string $empresaId): array
    {
        $fase = $this->faseRecrutamentoModel
            ->select('fase_recrutamento.*')
            ->join('modulo', 'modulo.id = fase_recrutamento.modulo_id')
            ->where('fase_recrutamento.id', $faseId)
            ->where('fase_recrutamento.modulo_id', $moduloId)
            ->where('modulo.empresa_id', $empresaId)
            ->first();

        if (! $fase) {
            throw new NaoEncontradoException('Fase não encontrada.');
        }

        return $fase;
    }

    public function renomear(string $faseId, string $moduloId, string $empresaId, string $novoNome): void
    {
        $this->buscarValidada($faseId, $moduloId, $empresaId);

        if (trim($novoNome) === '') {
            throw new \DomainException('Toda fase precisa de um nome.');
        }

        $this->faseRecrutamentoModel->update($faseId, ['nome' => $novoNome]);
    }

    /** `$ordemFaseIds` precisa ter exatamente as fases da vaga. */
    public function reordenar(string $moduloId, array $ordemFaseIds): void
    {
        $idsExistentes = array_column($this->faseRecrutamentoModel->where('modulo_id', $moduloId)->findAll(), 'id');

        $listaBate = count($ordemFaseIds) === count($idsExistentes)
            && empty(array_diff($ordemFaseIds, $idsExistentes));

        if (! $listaBate) {
            throw new \DomainException('A lista enviada não corresponde exatamente às fases desta vaga.');
        }

        $db = db_connect();
        $db->transStart();

        foreach ($ordemFaseIds as $ordem => $faseId) {
            $this->faseRecrutamentoModel->update($faseId, ['ordem' => $ordem]);
        }

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível reordenar as fases. Tente novamente.');
        }
    }

    /** Só exclui fase sem candidatos. */
    public function excluir(string $faseId, string $moduloId, string $empresaId): void
    {
        $this->buscarValidada($faseId, $moduloId, $empresaId);

        $totalCandidatos = $this->candidatoModel->where('fase_atual_id', $faseId)->countAllResults();

        if ($totalCandidatos > 0) {
            throw new \DomainException('Não é possível excluir uma fase que ainda possui candidatos.');
        }

        $this->faseRecrutamentoModel->delete($faseId);
        AtividadeService::registrar('etapa_excluida', 'Excluiu uma etapa do recrutamento', $moduloId);
    }
}
