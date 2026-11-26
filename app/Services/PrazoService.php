<?php

namespace App\Services;

use App\Libraries\AuthenticatedUser;
use App\Libraries\Relogio;

/**
 * Prazos de todas as tabelas que a pessoa enxerga: o que está atrasado, o que
 * vence hoje e o que vence nos próximos dias. É a base de "ninguém esquece
 * nada": a tela inicial mostra isso para cada pessoa.
 *
 * Um item conta como concluído (e sai da lista) quando alguma coluna de lista
 * dele está numa opção de fim de trabalho (Concluído, Entregue, Pago...).
 * Essa regra existe igual no frontend (utils/prazos.js).
 */
class PrazoService
{
    /** Opções de lista que encerram o trabalho, já normalizadas (minúsculas, sem acento). */
    public const OPCOES_DE_CONCLUSAO = [
        'concluido', 'concluida', 'feito', 'feita', 'entregue', 'pago', 'paga', 'finalizado', 'finalizada',
        'fechado', 'fechada', 'cancelado', 'cancelada', 'perdido', 'perdida', 'publicado', 'publicada',
        'resolvido', 'resolvida', 'encerrado', 'encerrada', 'arquivado', 'arquivada', 'contratado', 'contratada',
    ];

    private const DIAS_A_FRENTE = 7;

    private const LIMITE = 300;

    public static function normalizar(string $texto): string
    {
        // Sem depender de iconv/locale do servidor: troca os acentos do português à mão.
        $semAcento = strtr(mb_strtolower(trim($texto)), [
            'á' => 'a', 'à' => 'a', 'â' => 'a', 'ã' => 'a', 'é' => 'e', 'ê' => 'e', 'í' => 'i',
            'ó' => 'o', 'ô' => 'o', 'õ' => 'o', 'ú' => 'u', 'ü' => 'u', 'ç' => 'c',
        ]);

        return preg_replace('/[^a-z ]/', '', $semAcento);
    }

    public static function opcaoDeConclusao(?string $valor): bool
    {
        return $valor !== null && in_array(self::normalizar($valor), self::OPCOES_DE_CONCLUSAO, true);
    }

    /**
     * @param 'meus'|'todos' $escopo
     *
     * @return array{atrasados: array, hoje: array, proximos: array, total_atrasados: int}
     */
    public function listar(AuthenticatedUser $usuario, string $escopo = 'todos', ?string $hoje = null): array
    {
        $hoje    = $hoje ?? Relogio::hoje();
        $modulos = $this->modulosDeDados((string) $usuario->empresaId);
        $ids     = (new AutorizacaoModuloService())->filtrarModulosComNivel(
            array_column($modulos, 'id'),
            $usuario->acessoTotal,
            (string) $usuario->cargoId,
            'visualizar'
        );

        $itens = array_map(
            static fn ($i) => $i + ['meu' => $i['responsavel_id'] !== null && $i['responsavel_id'] === $usuario->id],
            $this->itensEmAberto($modulos, $ids, $hoje, self::DIAS_A_FRENTE)
        );
        if ($escopo === 'meus') {
            $itens = array_values(array_filter($itens, static fn ($i) => $i['meu']));
        }
        $itens = $this->comNomesDosResponsaveis($itens, (string) $usuario->empresaId);

        $grupos = ['atrasados' => [], 'hoje' => [], 'proximos' => []];
        foreach ($itens as $item) {
            $grupos[self::grupo($item['prazo'], $hoje)][] = $item;
        }

        return $grupos + ['total_atrasados' => count($grupos['atrasados'])];
    }

    public static function grupo(string $prazo, string $hoje): string
    {
        return $prazo < $hoje ? 'atrasados' : ($prazo === $hoje ? 'hoje' : 'proximos');
    }

    /** @return array<int, array{id: string, nome: string, icone: ?string}> */
    public function modulosDeDados(string $empresaId): array
    {
        return db_connect()->table('modulo')
            ->select('id, nome, icone')
            ->where('empresa_id', $empresaId)
            ->where('tipo', 'dados')
            ->get()->getResultArray();
    }

    /**
     * Itens não concluídos com prazo até `hoje + $diasAFrente` (inclui os já
     * vencidos), nas tabelas `$ids`. Três consultas, qualquer que seja o número
     * de tabelas. Ordenados pelo prazo.
     *
     * @param array<int, array{id: string, nome: string, icone: ?string}> $modulos
     * @param string[]                                                     $ids
     */
    public function itensEmAberto(array $modulos, array $ids, string $hoje, int $diasAFrente): array
    {
        if ($ids === []) {
            return [];
        }

        $limite = (new \DateTimeImmutable($hoje))->modify("+{$diasAFrente} days")->format('Y-m-d');
        $db     = db_connect();

        $campos = $db->table('campo_modulo')
            ->select('id, modulo_id, nome, tipo, ordem')
            ->whereIn('modulo_id', $ids)
            ->whereIn('tipo', ['prazo', 'responsavel', 'selecao', 'texto'])
            ->orderBy('ordem', 'ASC')
            ->get()->getResultArray();

        $porModulo = [];
        foreach ($campos as $c) {
            $porModulo[$c['modulo_id']][$c['tipo']][] = $c;
        }

        // Uma consulta para todos os itens com prazo até o limite, em todas as tabelas.
        $filtros  = [];
        $bindings = [];
        foreach ($porModulo as $moduloId => $tipos) {
            foreach ($tipos['prazo'] ?? [] as $prazo) {
                $filtros[]  = "(modulo_id = ? AND dados->>? IS NOT NULL AND dados->>? <> '' AND dados->>? <= ?)";
                array_push($bindings, $moduloId, $prazo['id'], $prazo['id'], $prazo['id'], $limite);
            }
        }
        if ($filtros === []) {
            return [];
        }

        $linhas = $db->query(
            'SELECT id, modulo_id, dados FROM registro WHERE ' . implode(' OR ', $filtros) . ' LIMIT ' . self::LIMITE,
            $bindings
        )->getResultArray();

        $nomesModulos = array_column($modulos, null, 'id');
        $itens        = [];

        foreach ($linhas as $linha) {
            $dados = json_decode($linha['dados'], true) ?: [];
            $tipos = $porModulo[$linha['modulo_id']];

            if ($this->concluido($dados, $tipos['selecao'] ?? [])) {
                continue;
            }

            $responsavelId = null;
            foreach ($tipos['responsavel'] ?? [] as $r) {
                if (! empty($dados[$r['id']])) {
                    $responsavelId = $dados[$r['id']];
                    break;
                }
            }

            $titulo = null;
            foreach ($tipos['texto'] ?? [] as $t) {
                if (! empty($dados[$t['id']])) {
                    $titulo = (string) $dados[$t['id']];
                    break;
                }
            }

            foreach ($tipos['prazo'] as $prazo) {
                $data = $dados[$prazo['id']] ?? null;
                if (! is_string($data) || $data === '' || $data > $limite) {
                    continue;
                }
                $itens[] = [
                    'registro_id'    => $linha['id'],
                    'modulo_id'      => $linha['modulo_id'],
                    'modulo_nome'    => $nomesModulos[$linha['modulo_id']]['nome'] ?? '',
                    'modulo_icone'   => $nomesModulos[$linha['modulo_id']]['icone'] ?? null,
                    'titulo'         => $titulo,
                    'coluna_prazo'   => $prazo['nome'],
                    'prazo'          => $data,
                    'responsavel_id' => $responsavelId,
                ];
            }
        }

        usort($itens, static fn ($a, $b) => [$a['prazo'], $a['titulo'] ?? ''] <=> [$b['prazo'], $b['titulo'] ?? '']);

        return $itens;
    }

    /** @param array<int, array{id: string}> $listas */
    private function concluido(array $dados, array $listas): bool
    {
        foreach ($listas as $lista) {
            if (self::opcaoDeConclusao(isset($dados[$lista['id']]) ? (string) $dados[$lista['id']] : null)) {
                return true;
            }
        }

        return false;
    }

    public function comNomesDosResponsaveis(array $itens, string $empresaId): array
    {
        $ids = array_values(array_unique(array_filter(
            array_column($itens, 'responsavel_id'),
            static fn ($id) => is_string($id) && preg_match('/^[0-9a-f-]{36}$/i', $id)
        )));
        if ($ids === []) {
            return array_map(static fn ($i) => $i + ['responsavel_nome' => null], $itens);
        }

        $nomes = array_column(
            db_connect()->table('usuario_equipe')->select('id, nome')->where('empresa_id', $empresaId)->whereIn('id', $ids)->get()->getResultArray(),
            'nome',
            'id'
        );

        return array_map(static fn ($i) => $i + ['responsavel_nome' => $nomes[$i['responsavel_id']] ?? null], $itens);
    }
}
