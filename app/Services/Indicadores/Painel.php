<?php

namespace App\Services\Indicadores;

use App\Libraries\Relogio;
use App\Services\PrazoService;

/**
 * Consultas do painel de indicadores: resumo da atividade, funil de cada
 * vaga, entradas e saídas do financeiro e cumprimento de prazos, cada um com
 * o período anterior para comparar. Os cálculos ficam em Comparacao e
 * FunilRecrutamento.
 */
class Painel
{
    public function painel(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim): array
    {
        [$antInicio, $antFim] = Comparacao::periodoAnterior($inicio, $fim);

        return [
            'periodo'          => Comparacao::descreverPeriodo($inicio, $fim),
            'periodo_anterior' => Comparacao::descreverPeriodo($antInicio, $antFim),
            'resumo'           => $this->resumo($empresaId, $inicio, $fim, $antInicio, $antFim),
            'recrutamento'     => $this->recrutamento($empresaId, $inicio, $fim, $antInicio, $antFim),
            'financeiro'       => $this->financeiro($empresaId, $inicio, $fim, $antInicio, $antFim),
            'prazos'           => $this->prazos($empresaId, $inicio, $fim, $antInicio, $antFim),
        ];
    }

    // ------------------------------------------------------------------
    // Resumo
    // ------------------------------------------------------------------

    private function resumo(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim, \DateTimeImmutable $antInicio, \DateTimeImmutable $antFim): array
    {
        $db = db_connect();

        $contar = static function (string $sql, \DateTimeImmutable $de, \DateTimeImmutable $ate) use ($db, $empresaId): int {
            return (int) ($db->query($sql, [$empresaId, Comparacao::utc($de), Comparacao::utc($ate)])->getRowArray()['n'] ?? 0);
        };

        $sqlAtividades = 'SELECT COUNT(*) AS n FROM atividade WHERE empresa_id = ? AND criado_em >= ? AND criado_em < ?';
        $sqlPessoas    = 'SELECT COUNT(DISTINCT usuario_id) AS n FROM atividade WHERE empresa_id = ? AND usuario_id IS NOT NULL AND criado_em >= ? AND criado_em < ?';
        $sqlItens      = 'SELECT COUNT(*) AS n FROM registro r JOIN modulo m ON m.id = r.modulo_id WHERE m.empresa_id = ? AND r.criado_em >= ? AND r.criado_em < ?';
        $sqlCandidatos = 'SELECT COUNT(*) AS n FROM candidato c JOIN modulo m ON m.id = c.modulo_id WHERE m.empresa_id = ? AND c.criado_em >= ? AND c.criado_em < ?';

        $resumo = [];
        foreach (['atividades' => $sqlAtividades, 'pessoas_ativas' => $sqlPessoas, 'itens_criados' => $sqlItens, 'candidatos' => $sqlCandidatos] as $chave => $sql) {
            $resumo[$chave] = Comparacao::comparar($contar($sql, $inicio, $fim), $contar($sql, $antInicio, $antFim));
        }

        return $resumo;
    }

    // ------------------------------------------------------------------
    // Recrutamento
    // ------------------------------------------------------------------

    private function recrutamento(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim, \DateTimeImmutable $antInicio, \DateTimeImmutable $antFim): array
    {
        $db      = db_connect();
        $modulos = $db->table('modulo')->select('id, nome')->where('empresa_id', $empresaId)->where('tipo', 'recrutamento')
            ->orderBy('nome', 'ASC')->get()->getResultArray();
        if ($modulos === []) {
            return [];
        }
        $ids = array_column($modulos, 'id');

        $fases = $db->table('fase_recrutamento')->select('id, modulo_id, nome, ordem')->whereIn('modulo_id', $ids)
            ->orderBy('ordem', 'ASC')->get()->getResultArray();
        $candidatos = $db->table('candidato')->select('id, modulo_id, fase_atual_id, criado_em')->whereIn('modulo_id', $ids)
            ->get()->getResultArray();
        $historico = $candidatos === [] ? [] : $db->query(
            'SELECT h.candidato_id, h.fase_anterior_id, h.fase_nova_id, h.criado_em
               FROM historico_fase h JOIN candidato c ON c.id = h.candidato_id
              WHERE c.modulo_id IN ? ORDER BY h.criado_em ASC, h.id ASC',
            [$ids]
        )->getResultArray();

        $periodos = [
            'atual'    => [Comparacao::utc($inicio), Comparacao::utc($fim)],
            'anterior' => [Comparacao::utc($antInicio), Comparacao::utc($antFim)],
        ];
        $agora = Comparacao::utc(Relogio::agora());

        $resultado = [];
        foreach ($modulos as $modulo) {
            $resultado[] = ['modulo_id' => $modulo['id'], 'nome' => $modulo['nome']] + FunilRecrutamento::funil(
                array_values(array_filter($fases, static fn ($f) => $f['modulo_id'] === $modulo['id'])),
                array_values(array_filter($candidatos, static fn ($c) => $c['modulo_id'] === $modulo['id'])),
                $historico,
                $periodos,
                $agora
            );
        }

        return $resultado;
    }

    // ------------------------------------------------------------------
    // Financeiro
    // ------------------------------------------------------------------

    private function financeiro(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim, \DateTimeImmutable $antInicio, \DateTimeImmutable $antFim): array
    {
        $db      = db_connect();
        $modulos = $db->table('modulo')->select('id, nome')->where('empresa_id', $empresaId)->where('tipo', 'financeiro')
            ->orderBy('nome', 'ASC')->get()->getResultArray();
        if ($modulos === []) {
            return [];
        }

        // data_lancamento é DATE local: compara com as datas locais do período.
        $totais = static function (string $moduloId, \DateTimeImmutable $de, \DateTimeImmutable $ate) use ($db): array {
            $linhas = $db->query(
                'SELECT tipo, COALESCE(SUM(valor), 0) AS total FROM lancamento_financeiro
                  WHERE modulo_id = ? AND data_lancamento >= ? AND data_lancamento < ? GROUP BY tipo',
                [$moduloId, $de->format('Y-m-d'), $ate->format('Y-m-d')]
            )->getResultArray();
            $porTipo = array_column($linhas, 'total', 'tipo');

            return ['entradas' => (float) ($porTipo['entrada'] ?? 0), 'saidas' => (float) ($porTipo['saida'] ?? 0)];
        };

        // Série dos 6 meses que terminam no mês do fim do período.
        $ultimoMes   = $fim->modify('-1 day')->modify('first day of this month');
        $primeiroMes = $ultimoMes->modify('-5 months');
        $meses       = [];
        for ($m = $primeiroMes; $m <= $ultimoMes; $m = $m->modify('+1 month')) {
            $meses[] = $m->format('Y-m');
        }

        $resultado = [];
        foreach ($modulos as $modulo) {
            $atual    = $totais($modulo['id'], $inicio, $fim);
            $anterior = $totais($modulo['id'], $antInicio, $antFim);

            $categorias = $db->query(
                "SELECT l.tipo, COALESCE(c.nome, 'Sem categoria') AS categoria, SUM(l.valor) AS total
                   FROM lancamento_financeiro l LEFT JOIN categoria_financeira c ON c.id = l.categoria_id
                  WHERE l.modulo_id = ? AND l.data_lancamento >= ? AND l.data_lancamento < ?
                  GROUP BY l.tipo, COALESCE(c.nome, 'Sem categoria') ORDER BY total DESC",
                [$modulo['id'], $inicio->format('Y-m-d'), $fim->format('Y-m-d')]
            )->getResultArray();

            $serie = array_fill_keys($meses, ['entradas' => 0.0, 'saidas' => 0.0]);
            foreach ($db->query(
                "SELECT to_char(data_lancamento, 'YYYY-MM') AS mes, tipo, SUM(valor) AS total FROM lancamento_financeiro
                  WHERE modulo_id = ? AND data_lancamento >= ? AND data_lancamento < ? GROUP BY 1, 2",
                [$modulo['id'], $primeiroMes->format('Y-m-d'), $ultimoMes->modify('+1 month')->format('Y-m-d')]
            )->getResultArray() as $l) {
                $serie[$l['mes']][$l['tipo'] === 'entrada' ? 'entradas' : 'saidas'] = (float) $l['total'];
            }

            $porCategoria = static fn (string $tipo) => array_values(array_map(
                static fn ($c) => ['categoria' => $c['categoria'], 'total' => (float) $c['total']],
                array_filter($categorias, static fn ($c) => $c['tipo'] === $tipo)
            ));

            $resultado[] = [
                'modulo_id' => $modulo['id'],
                'nome'      => $modulo['nome'],
                'entradas'  => Comparacao::comparar($atual['entradas'], $anterior['entradas']),
                'saidas'    => Comparacao::comparar($atual['saidas'], $anterior['saidas']),
                'saldo'     => Comparacao::comparar(
                    round($atual['entradas'] - $atual['saidas'], 2),
                    round($anterior['entradas'] - $anterior['saidas'], 2)
                ),
                'saidas_por_categoria'   => $porCategoria('saida'),
                'entradas_por_categoria' => $porCategoria('entrada'),
                'meses'                  => array_map(
                    static fn ($mes, $v) => ['mes' => $mes] + $v,
                    array_keys($serie),
                    $serie
                ),
            ];
        }

        return $resultado;
    }

    // ------------------------------------------------------------------
    // Prazos
    // ------------------------------------------------------------------

    private function prazos(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim, \DateTimeImmutable $antInicio, \DateTimeImmutable $antFim): array
    {
        $servico = new PrazoService();
        $modulos = $servico->modulosDeDados($empresaId);
        $ids     = array_column($modulos, 'id');
        $hoje    = Relogio::hoje();

        $atual    = $this->prazosVencendoEntre($ids, $inicio->format('Y-m-d'), $fim->format('Y-m-d'), $hoje);
        $anterior = $this->prazosVencendoEntre($ids, $antInicio->format('Y-m-d'), $antFim->format('Y-m-d'), $hoje);

        $atrasados = array_values(array_filter(
            $servico->comNomesDosResponsaveis($servico->itensEmAberto($modulos, $ids, $hoje, 0), $empresaId),
            static fn ($i) => $i['prazo'] < $hoje
        ));
        $porResponsavel = [];
        foreach ($atrasados as $i) {
            $nome                  = $i['responsavel_nome'] ?: 'Sem responsável';
            $porResponsavel[$nome] = ($porResponsavel[$nome] ?? 0) + 1;
        }
        arsort($porResponsavel);

        $taxa = static fn (array $p) => $p['total'] === 0 ? null : round($p['concluidos'] / $p['total'] * 100);

        return [
            'vencendo'        => Comparacao::comparar($atual['total'], $anterior['total']),
            'concluidos'      => Comparacao::comparar($atual['concluidos'], $anterior['concluidos']),
            'taxa_conclusao'  => ['atual' => $taxa($atual), 'anterior' => $taxa($anterior)],
            'atrasados_agora' => count($atrasados),
            'atrasados_por_responsavel' => array_map(
                static fn ($nome, $n) => ['responsavel' => $nome, 'total' => $n],
                array_keys(array_slice($porResponsavel, 0, 10, true)),
                array_slice($porResponsavel, 0, 10, true)
            ),
        ];
    }

    /**
     * Prazos que vencem em [de, ate) e quantos deles já estão concluídos (alguma
     * coluna de lista numa opção de fim de trabalho, mesma regra do PrazoService).
     *
     * @return array{total: int, concluidos: int}
     */
    private function prazosVencendoEntre(array $moduloIds, string $de, string $ate, string $hoje): array
    {
        if ($moduloIds === []) {
            return ['total' => 0, 'concluidos' => 0];
        }
        $db     = db_connect();
        $campos = $db->table('campo_modulo')->select('id, modulo_id, tipo')->whereIn('modulo_id', $moduloIds)
            ->whereIn('tipo', ['prazo', 'selecao'])->get()->getResultArray();

        $porModulo = [];
        foreach ($campos as $c) {
            $porModulo[$c['modulo_id']][$c['tipo']][] = $c['id'];
        }

        $filtros  = [];
        $bindings = [];
        foreach ($porModulo as $moduloId => $tipos) {
            foreach ($tipos['prazo'] ?? [] as $prazoId) {
                $filtros[] = "(modulo_id = ? AND dados->>? >= ? AND dados->>? < ?)";
                array_push($bindings, $moduloId, $prazoId, $de, $prazoId, $ate);
            }
        }
        if ($filtros === []) {
            return ['total' => 0, 'concluidos' => 0];
        }

        $total      = 0;
        $concluidos = 0;
        foreach ($db->query('SELECT modulo_id, dados FROM registro WHERE ' . implode(' OR ', $filtros), $bindings)->getResultArray() as $linha) {
            $dados = json_decode($linha['dados'], true) ?: [];
            $tipos = $porModulo[$linha['modulo_id']];
            $feito = false;
            foreach ($tipos['selecao'] ?? [] as $listaId) {
                if (PrazoService::opcaoDeConclusao(isset($dados[$listaId]) ? (string) $dados[$listaId] : null)) {
                    $feito = true;
                    break;
                }
            }
            foreach ($tipos['prazo'] as $prazoId) {
                $data = $dados[$prazoId] ?? null;
                if (is_string($data) && $data >= $de && $data < $ate) {
                    $total++;
                    $concluidos += $feito ? 1 : 0;
                }
            }
        }

        return ['total' => $total, 'concluidos' => $concluidos];
    }
}
