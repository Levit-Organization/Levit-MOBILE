<?php

namespace App\Services\Recrutamento;

use App\Models\CandidatoModel;
use App\Models\FaseRecrutamentoModel;
use App\Services\Automacao\CamposCandidato;
use CodeIgniter\Events\Events;
use CodeIgniter\HTTP\Files\UploadedFile;

/**
 * Entrada de um candidato numa vaga, pela página pública ou pela equipe:
 * grava na primeira etapa, com o currículo se veio, e avisa as automações.
 */
final class Inscricao
{
    public function __construct(
        private readonly CandidatoModel $candidatoModel,
        private readonly FaseRecrutamentoModel $faseRecrutamentoModel,
    ) {
    }

    /**
     * O arquivo sobe antes do insert; se o banco falhar, ele é apagado.
     *
     * @param array  $modulo            a vaga (id e empresa_id)
     * @param string $mensagemDuplicado o aviso de e-mail repetido, conforme quem cadastra
     *
     * @return false|int|string id do candidato criado
     *
     * @throws \DomainException  e-mail repetido na vaga ou currículo recusado
     * @throws \RuntimeException vaga sem nenhuma etapa
     */
    public function inserir(array $modulo, array $dados, ?UploadedFile $curriculo, string $mensagemDuplicado): false|int|string
    {
        $moduloId = $modulo['id'];

        if ($this->candidatoModel->where('modulo_id', $moduloId)->where('email', $dados['email'])->first()) {
            throw new \DomainException($mensagemDuplicado);
        }

        $primeiraFase = $this->faseRecrutamentoModel->where('modulo_id', $moduloId)->orderBy('ordem', 'ASC')->first();

        if (! $primeiraFase) {
            throw new \RuntimeException('Esta vaga ainda não tem nenhuma etapa configurada.');
        }

        $arquivo = Curriculo::foiEnviado($curriculo) ? Curriculo::salvar($curriculo) : [];

        try {
            $candidatoId = $this->candidatoModel->insert([
                'modulo_id'     => $moduloId,
                'fase_atual_id' => $primeiraFase['id'],
                'nome'          => $dados['nome'],
                'email'         => $dados['email'],
                'telefone'      => ($dados['telefone'] ?? '') ?: null,
                'mensagem'      => ($dados['mensagem'] ?? '') ?: null,
            ] + $arquivo);
        } catch (\Throwable $e) {
            if (! empty($arquivo['curriculo_chave'])) {
                Curriculo::excluir($arquivo['curriculo_chave']);
            }

            throw $e;
        }

        Events::trigger('candidato_criado', [
            'modulo_id'      => $moduloId,
            'registro_id'    => null,
            'dados_registro' => CamposCandidato::dados($dados, $primeiraFase['nome']),
        ]);

        return $candidatoId;
    }
}
