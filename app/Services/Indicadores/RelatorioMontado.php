<?php

namespace App\Services\Indicadores;

use App\Exceptions\NaoEncontradoException;
use App\Libraries\Relogio;
use App\Services\Pesquisa\Filtros;
use App\Services\RegistroService;

/**
 * Relatório montado pela pessoa: agrupar uma tabela por uma coluna e contar,
 * somar ou tirar a média. Aqui ficam as consultas; as regras puras, em
 * Agrupamento.
 */
class RelatorioMontado
{
    /**
     * Tabelas de dados com as colunas que servem para agrupar e para somar.
     * Uma coluna de ligação agrupa pelo item ligado ("Pedidos por Cliente") e
     * abre as colunas da tabela ligada ("Pedidos por Cliente › Cidade"), com
     * id "ligacao>coluna" e `grupo` com o nome da tabela, para a tela separar.
     */
    public function tabelas(string $empresaId): array
    {
        $db      = db_connect();
        $modulos = $db->table('modulo')->select('id, nome, icone')->where('empresa_id', $empresaId)->where('tipo', 'dados')
            ->orderBy('nome', 'ASC')->get()->getResultArray();
        if ($modulos === []) {
            return [];
        }
        $campos = $db->table('campo_modulo')->select('id, modulo_id, nome, tipo, modulo_vinculado_id')->whereIn('modulo_id', array_column($modulos, 'id'))
            ->orderBy('ordem', 'ASC')->get()->getResultArray();
        $nomes = array_column($modulos, 'nome', 'id');

        $porModulo = [];
        foreach ($campos as $campo) {
            $porModulo[$campo['modulo_id']][] = $campo;
        }
        $agrupaveis = static fn (array $lista, bool $comLigacao) => array_values(array_filter(
            $lista,
            static fn ($c) => in_array($c['tipo'], Agrupamento::TIPOS_AGRUPAVEIS, true) && ($comLigacao || $c['tipo'] !== 'vinculo')
        ));

        return array_map(static function ($m) use ($porModulo, $agrupaveis, $nomes) {
            $doModulo = $porModulo[$m['id']] ?? [];
            $agrupar  = array_map(
                static fn ($c) => ['id' => $c['id'], 'nome' => $c['nome'], 'tipo' => $c['tipo'], 'grupo' => 'Esta tabela'],
                $agrupaveis($doModulo, true)
            );

            foreach ($doModulo as $ligacao) {
                if ($ligacao['tipo'] !== 'vinculo' || ! isset($nomes[$ligacao['modulo_vinculado_id']])) {
                    continue;
                }
                foreach ($agrupaveis($porModulo[$ligacao['modulo_vinculado_id']] ?? [], false) as $c) {
                    $agrupar[] = [
                        'id'    => "{$ligacao['id']}>{$c['id']}",
                        'nome'  => "{$ligacao['nome']} › {$c['nome']}",
                        'tipo'  => $c['tipo'],
                        'grupo' => "{$ligacao['nome']} (tabela {$nomes[$ligacao['modulo_vinculado_id']]})",
                    ];
                }
            }

            return $m + [
                'agrupar' => $agrupar,
                'numeros' => array_values(array_map(
                    static fn ($c) => ['id' => $c['id'], 'nome' => $c['nome']],
                    array_filter($doModulo, static fn ($c) => $c['tipo'] === 'numero')
                )),
            ];
        }, $modulos);
    }

    /**
     * Agrupa os itens de uma tabela por uma coluna e conta, soma ou tira a
     * média de uma coluna de número. Datas e prazos agrupam por mês.
     *
     * - Coluna de ligação: agrupa pelo item ligado (quem liga a vários entra
     *   no grupo de cada um).
     * - "ligacao>coluna": agrupa por uma coluna da tabela ligada.
     * - `filtros` (JSON, o da pesquisa avançada) e o período (pela data de
     *   criação) escolhem quais itens entram.
     */
    public function agrupar(string $empresaId, array $params): array
    {
        $db       = db_connect();
        $moduloId = (string) ($params['modulo_id'] ?? '');
        $modulo   = preg_match('/^[0-9a-f-]{36}$/i', $moduloId)
            ? $db->table('modulo')->select('id, nome')->where('id', $moduloId)->where('empresa_id', $empresaId)->where('tipo', 'dados')->get()->getRowArray()
            : null;
        if (! $modulo) {
            throw new NaoEncontradoException('Tabela não encontrada.');
        }

        // Colunas de todas as tabelas da empresa: as ligações e os filtros podem olhar outras tabelas.
        $todos = self::camposDaEmpresa($empresaId);

        [$grupo, $ligacao] = Agrupamento::colunaDeAgrupamento((string) ($params['agrupar_por'] ?? ''), $moduloId, $todos);

        $operacao = (string) ($params['operacao'] ?? 'contar');
        if (! in_array($operacao, Agrupamento::OPERACOES, true)) {
            throw new \DomainException('Escolha entre contar, somar ou tirar a média.');
        }
        $valor = null;
        if ($operacao !== 'contar') {
            $valor = $todos[(string) ($params['campo_valor'] ?? '')] ?? null;
            if (! $valor || $valor['tipo'] !== 'numero' || $valor['modulo_id'] !== $moduloId) {
                throw new \DomainException('Para somar ou tirar a média, escolha uma coluna de número.');
            }
        }

        $binds = ['modulo' => $moduloId, 'grupo' => $grupo['id'], 'valor' => $valor['id'] ?? $grupo['id']];

        $porMes    = in_array($grupo['tipo'], ['data', 'prazo'], true);
        $expressao = static fn (string $a) => $porMes
            ? "CASE WHEN {$a}.dados->>:grupo: ~ '^[0-9]{4}-[0-9]{2}' THEN substr({$a}.dados->>:grupo:, 1, 7) END"
            : "NULLIF({$a}.dados->>:grupo:, '')";
        $idsLigados = static fn (string $chave) => "jsonb_array_elements_text(CASE jsonb_typeof(r.dados->{$chave}) WHEN 'array' THEN r.dados->{$chave}"
            . " WHEN 'string' THEN jsonb_build_array(r.dados->{$chave}) ELSE '[]'::jsonb END) AS e";

        if ($ligacao !== null) {
            // Coluna da tabela ligada: o valor dela em cada item ligado (repetidos contam uma vez).
            $binds += ['ligacao' => $ligacao['id'], 'destino' => $ligacao['modulo_vinculado_id']];
            $juncao = "LEFT JOIN LATERAL (SELECT DISTINCT {$expressao('l')} AS grupo FROM registro l WHERE l.modulo_id = :destino:"
                . " AND l.id::text IN (SELECT e FROM {$idsLigados(':ligacao:')})) g ON TRUE";
        } elseif ($grupo['tipo'] === 'vinculo') {
            $juncao = "LEFT JOIN LATERAL (SELECT e AS grupo FROM {$idsLigados(':grupo:')}) g ON TRUE";
        } else {
            $juncao = "CROSS JOIN LATERAL (SELECT {$expressao('r')} AS grupo) g";
        }

        $condicoes = ['r.modulo_id = :modulo:'];
        $inicio    = Agrupamento::dataValida($params['inicio'] ?? null);
        $fim       = Agrupamento::dataValida($params['fim'] ?? null);
        if ($inicio && $fim) {
            $condicoes[] = 'r.criado_em >= :inicio: AND r.criado_em < :fim:';
            $binds['inicio'] = Comparacao::utc(new \DateTimeImmutable($inicio . ' 00:00:00', Relogio::fuso()));
            $binds['fim']    = Comparacao::utc((new \DateTimeImmutable($fim . ' 00:00:00', Relogio::fuso()))->modify('+1 day'));
        }

        $filtros = $params['filtros'] ?? null;
        if (is_string($filtros) && $filtros !== '') {
            $filtros = json_decode($filtros, true);
            if (! is_array($filtros)) {
                throw new \DomainException('Filtro inválido. Refaça o filtro.');
            }
            // Relatórios são de quem tem acesso total: enxerga todas as tabelas.
            [$sql, $parametros] = (new Filtros($todos, static fn () => true))->paraSql($filtros, $moduloId, 'r');
            $condicoes[]        = $sql;
            $binds += $parametros;
        }

        $onde   = implode(' AND ', $condicoes);
        $numero = "CASE WHEN r.dados->>:valor: ~ '^-?[0-9]+(\\.[0-9]+)?$' THEN (r.dados->>:valor:)::numeric END";
        $linhas = $db->query(
            "SELECT g.grupo, COUNT(*) AS total, SUM({$numero}) AS soma, AVG({$numero}) AS media
               FROM registro r {$juncao}
              WHERE {$onde}
              GROUP BY g.grupo",
            $binds
        )->getResultArray();

        // Ligação a vários itens põe o mesmo item em mais de um grupo: o total conta cada item uma vez.
        $totalItens = $ligacao !== null || $grupo['tipo'] === 'vinculo'
            ? (int) $db->query("SELECT COUNT(*) AS n FROM registro r WHERE {$onde}", $binds)->getRow()->n
            : null;

        $chaves = array_values(array_filter(array_column($linhas, 'grupo'), static fn ($id) => is_string($id) && Filtros::uuidValido($id)));
        $nomes  = [];
        if ($grupo['tipo'] === 'responsavel' && $chaves !== []) {
            $nomes = array_column(
                $db->table('usuario_equipe')->select('id, nome')->where('empresa_id', $empresaId)->whereIn('id', $chaves)->get()->getResultArray(),
                'nome',
                'id'
            );
        } elseif ($grupo['tipo'] === 'vinculo' && $ligacao === null) {
            $nomes = (new RegistroService())->titulosDosItens($grupo['modulo_vinculado_id'], $chaves);
        }

        return [
            'tabela'       => $modulo['nome'],
            'agrupado_por' => $grupo['nome'],
            'operacao'     => $operacao,
            'coluna_valor' => $valor['nome'] ?? null,
            'tipo_grupo'   => $grupo['tipo'],
        ] + Agrupamento::montarGrupos($linhas, $operacao, $grupo['tipo'], $nomes, $totalItens);
    }

    /** @return array<string, array> colunas de todas as tabelas da empresa, por id */
    private static function camposDaEmpresa(string $empresaId): array
    {
        return array_column(
            db_connect()->query('SELECT c.id, c.modulo_id, c.nome, c.tipo, c.modulo_vinculado_id FROM campo_modulo c JOIN modulo m ON m.id = c.modulo_id WHERE m.empresa_id = ?', [$empresaId])->getResultArray(),
            null,
            'id'
        );
    }

    /**
     * Os filtros do relatório montado em português, com nomes de pessoas e de
     * itens ligados no lugar dos identificadores (para o PDF).
     *
     * @return list<string>
     */
    public function descreverFiltros(string $empresaId, array $filtros): array
    {
        $db      = db_connect();
        $tabelas = array_column($db->table('modulo')->select('id, nome')->where('empresa_id', $empresaId)->get()->getResultArray(), 'nome', 'id');

        $nomeDoValor = static function (array $campo, string $valor) use ($db, $empresaId): string {
            if (! Filtros::uuidValido($valor)) {
                return $valor;
            }
            if ($campo['tipo'] === 'responsavel') {
                return $db->table('usuario_equipe')->select('nome')->where('id', $valor)->where('empresa_id', $empresaId)->get()->getRowArray()['nome'] ?? 'Pessoa removida';
            }
            $titulos = (new RegistroService())->titulosDosItens((string) $campo['modulo_vinculado_id'], [$valor]);

            return array_key_exists($valor, $titulos) ? (($titulos[$valor] ?? '') === '' ? 'Item sem nome' : "“{$titulos[$valor]}”") : 'Item excluído';
        };

        return (new Filtros(self::camposDaEmpresa($empresaId), static fn () => true, $tabelas))->descrever($filtros, $nomeDoValor);
    }
}
