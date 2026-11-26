<?php

namespace App\Services\Recrutamento;

use App\Exceptions\NaoEncontradoException;
use App\Models\CandidatoModel;
use App\Models\FaseRecrutamentoModel;
use App\Models\HistoricoFaseModel;
use App\Services\Automacao\CamposCandidato;
use CodeIgniter\Events\Events;

/**
 * Passagem de um candidato para outra etapa da vaga: a etapa nova e o
 * histórico gravados juntos, depois o aviso para as automações.
 * Quem chama já conferiu vaga, permissão e etapa de destino.
 */
final class MudancaDeEtapa
{
    public function __construct(
        private readonly CandidatoModel $candidatoModel,
        private readonly FaseRecrutamentoModel $faseRecrutamentoModel,
        private readonly HistoricoFaseModel $historicoFaseModel,
    ) {
    }

    /**
     * Etapa da vaga com esse nome, sem diferença de maiúsculas nem de
     * espaços nas pontas (a primeira que bater).
     *
     * @throws NaoEncontradoException
     */
    public function etapaPeloNome(string $moduloId, string $nomeFase): array
    {
        $nomeFaseNormalizado = strtolower(trim($nomeFase));

        foreach ($this->faseRecrutamentoModel->where('modulo_id', $moduloId)->findAll() as $fase) {
            if (strtolower(trim($fase['nome'])) === $nomeFaseNormalizado) {
                return $fase;
            }
        }

        throw new NaoEncontradoException("A vaga deste candidato não possui uma fase chamada '{$nomeFase}'.");
    }

    /**
     * @param array $candidato a linha do candidato antes da mudança
     *
     * @throws \RuntimeException se a transação falhar
     */
    public function gravar(string $candidatoId, array $candidato, string $moduloId, string $novaFaseId, string $usuarioId): void
    {
        $db = db_connect();
        $db->transStart();

        $this->candidatoModel->update($candidatoId, ['fase_atual_id' => $novaFaseId]);

        $this->historicoFaseModel->insert([
            'candidato_id'     => $candidatoId,
            'fase_anterior_id' => $candidato['fase_atual_id'],
            'fase_nova_id'     => $novaFaseId,
            'alterado_por'     => $usuarioId,
        ]);

        $db->transComplete();

        if ($db->transStatus() === false) {
            throw new \RuntimeException('Não foi possível mover o candidato. Tente novamente.');
        }

        $nomes = array_column(
            $this->faseRecrutamentoModel->select('id, nome')->whereIn('id', [$candidato['fase_atual_id'], $novaFaseId])->findAll(),
            'nome',
            'id'
        );
        $etapaAnterior = $nomes[$candidato['fase_atual_id']] ?? null;
        $etapaNova     = $nomes[$novaFaseId] ?? null;

        Events::trigger('candidato_mudou_etapa', [
            'modulo_id'        => $moduloId,
            'registro_id'      => null,
            'dados_registro'   => CamposCandidato::dados($candidato, $etapaNova, $etapaAnterior),
            'dados_anteriores' => CamposCandidato::dados($candidato, $etapaAnterior),
        ]);
    }
}
