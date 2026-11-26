<?php

namespace App\Services\Registros;

use App\Services\Pesquisa\Filtros;
use App\Services\RecorrenciaService;

/**
 * Validação dos dados de um item conforme o tipo de cada coluna. Lembra,
 * durante a requisição, as pessoas e os itens ligados já conferidos (a
 * importação confere tudo de uma vez antes, em preconferirLigacoes).
 *
 * As consultas ao banco ficam em idsExistentes e pessoaExiste.
 */
class ValidadorDeDados
{
    /** Máximo de itens numa coluna de ligação que aceita vários. */
    public const MAX_LIGADOS = 100;

    /** @var array<string, bool> pessoas já conferidas nesta requisição */
    private array $pessoasConferidas = [];

    /** @var array<string, bool> itens ligados já conferidos nesta requisição ("tabela|id" => existe) */
    private array $itensConferidos = [];

    /**
     * Confere cada coluna conforme o tipo e devolve só as preenchidas, já
     * convertidas. `$dadosRecebidos` vem como { dados: { campo_id: valor } }.
     *
     * @throws \DomainException com a mensagem para quem preencheu
     */
    public function validar(array $campos, array $dadosRecebidos, string $moduloId): array
    {
        $dadosRecebidos = $dadosRecebidos['dados'] ?? [];
        $dadosValidados = [];

        foreach ($campos as $campo) {
            $valor = $dadosRecebidos[$campo['id']] ?? null;

            if ($valor === null || $valor === '') {
                continue;
            }

            switch ($campo['tipo']) {
                case 'texto':
                    $dadosValidados[$campo['id']] = (string) $valor;
                    break;

                case 'numero':
                    if (! is_numeric($valor)) {
                        throw new \DomainException("A coluna '{$campo['nome']}' precisa ser um número.");
                    }
                    $dadosValidados[$campo['id']] = $valor + 0;
                    break;

                case 'data':
                case 'prazo':
                    $data = \DateTime::createFromFormat('Y-m-d', $valor);
                    if (! $data || $data->format('Y-m-d') !== $valor) {
                        throw new \DomainException("A coluna '{$campo['nome']}' precisa ser uma data válida (AAAA-MM-DD).");
                    }
                    $dadosValidados[$campo['id']] = $valor;
                    break;

                case 'selecao':
                    if (! in_array($valor, $campo['opcoes'] ?? [], true)) {
                        throw new \DomainException("A coluna '{$campo['nome']}' precisa ser uma das opções válidas.");
                    }
                    $dadosValidados[$campo['id']] = $valor;
                    break;

                case 'recorrencia':
                    if (! isset(RecorrenciaService::PERIODOS[$valor])) {
                        throw new \DomainException("A coluna '{$campo['nome']}' precisa ser uma das repetições: semanal, quinzenal, mensal, trimestral ou anual.");
                    }
                    $dadosValidados[$campo['id']] = (string) $valor;
                    break;

                case 'responsavel':
                    if (! $this->pessoaDaEmpresaDoModulo((string) $valor, $moduloId)) {
                        throw new \DomainException("A coluna '{$campo['nome']}' precisa ser uma pessoa da equipe.");
                    }
                    $dadosValidados[$campo['id']] = (string) $valor;
                    break;

                case 'vinculo':
                    $ligados = $this->validarVinculo($campo, $valor);
                    if ($ligados !== null) {
                        $dadosValidados[$campo['id']] = $ligados;
                    }
                    break;
            }
        }

        return $dadosValidados;
    }

    /**
     * Coluna de ligação: ids de itens que existem na tabela ligada. Guarda um id
     * ou, se a coluna aceita vários, uma lista sem repetidos.
     *
     * @return list<string>|string|null null = nada ligado
     */
    private function validarVinculo(array $campo, mixed $valor): array|string|null
    {
        $ids = array_values(array_unique(array_filter(
            array_map(static fn ($id) => is_scalar($id) ? strtolower(trim((string) $id)) : '', is_array($valor) ? $valor : [$valor]),
            static fn (string $id) => $id !== ''
        )));

        if ($ids === []) {
            return null;
        }

        $multiplo = ! empty($campo['opcoes']['multiplo']);
        if (! $multiplo && count($ids) > 1) {
            throw new \DomainException("A coluna '{$campo['nome']}' liga um item só.");
        }
        if (count($ids) > self::MAX_LIGADOS) {
            throw new \DomainException("A coluna '{$campo['nome']}' liga no máximo " . self::MAX_LIGADOS . ' itens.');
        }
        foreach ($ids as $id) {
            if (! Filtros::uuidValido($id)) {
                throw new \DomainException("A coluna '{$campo['nome']}' precisa apontar para itens da tabela ligada.");
            }
        }
        if ($this->algumInexistente((string) ($campo['modulo_vinculado_id'] ?? ''), $ids)) {
            throw new \DomainException("A coluna '{$campo['nome']}' aponta para um item que não existe mais na tabela ligada.");
        }

        return $multiplo ? $ids : $ids[0];
    }

    /** @param list<string> $ids uuids válidos */
    private function algumInexistente(string $moduloId, array $ids): bool
    {
        $novos = array_values(array_filter($ids, fn (string $id) => ! isset($this->itensConferidos["{$moduloId}|{$id}"])));

        if ($novos !== []) {
            $achados = $moduloId === '' ? [] : $this->idsExistentes($moduloId, $novos);
            foreach ($novos as $id) {
                $this->itensConferidos["{$moduloId}|{$id}"] = in_array($id, $achados, true);
            }
        }

        foreach ($ids as $id) {
            if (! $this->itensConferidos["{$moduloId}|{$id}"]) {
                return true;
            }
        }

        return false;
    }

    /** Importação: confere de uma vez os itens ligados de todas as linhas (uma consulta por coluna). */
    public function preconferirLigacoes(array $campos, array $itens): void
    {
        foreach ($campos as $campo) {
            if ($campo['tipo'] !== 'vinculo' || empty($campo['modulo_vinculado_id'])) {
                continue;
            }

            $ids = [];
            foreach ($itens as $item) {
                $valor = is_array($item['dados'] ?? null) ? ($item['dados'][$campo['id']] ?? null) : null;
                foreach (is_array($valor) ? $valor : [$valor] as $id) {
                    if (is_string($id) && Filtros::uuidValido($id)) {
                        $ids[strtolower($id)] = true;
                    }
                }
            }

            foreach (array_chunk(array_keys($ids), 500) as $lote) {
                $this->algumInexistente($campo['modulo_vinculado_id'], $lote);
            }
        }
    }

    /** O responsável precisa ser alguém da mesma empresa da tabela. */
    private function pessoaDaEmpresaDoModulo(string $usuarioId, string $moduloId): bool
    {
        if (! preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i', $usuarioId)) {
            return false;
        }

        return $this->pessoasConferidas["{$usuarioId}|{$moduloId}"] ??= $this->pessoaExiste($usuarioId, $moduloId);
    }

    /**
     * Dos ids pedidos, os que existem na tabela.
     *
     * @param list<string> $ids
     *
     * @return list<mixed>
     */
    protected function idsExistentes(string $moduloId, array $ids): array
    {
        return array_column(
            db_connect()->query('SELECT id FROM registro WHERE modulo_id = ? AND id IN ?', [$moduloId, $ids])->getResultArray(),
            'id'
        );
    }

    protected function pessoaExiste(string $usuarioId, string $moduloId): bool
    {
        return (bool) db_connect()->query(
            'SELECT 1 FROM usuario_equipe u JOIN modulo m ON m.empresa_id = u.empresa_id WHERE u.id = ? AND m.id = ?',
            [$usuarioId, $moduloId]
        )->getRow();
    }
}
