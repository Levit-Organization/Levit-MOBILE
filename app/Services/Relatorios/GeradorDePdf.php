<?php

namespace App\Services\Relatorios;

use App\Libraries\Relogio;
use App\Services\IndicadoresService;
use App\Services\PrazoService;

/**
 * Junta os dados dos PDFs (histórico de atividades, prazos e indicadores) e
 * gera o arquivo. A montagem do HTML fica em HtmlDoRelatorio e HtmlDoAgrupamento.
 */
class GeradorDePdf
{
    /**
     * PDF do relatório agendado (ou gerado na hora) com tudo o que foi feito no período.
     *
     * @return array{conteudo: string, total: int, pessoas: int, empresa: string}
     */
    public function relatorio(string $empresaId, \DateTimeImmutable $inicio, \DateTimeImmutable $fim, string $titulo, ?array $secoes = null): array
    {
        $secoes = $secoes ?? Agenda::SECOES_PADRAO;
        $db  = db_connect();
        $utc = new \DateTimeZone('UTC');

        $empresa = $db->table('empresa')->select('nome')->where('id', $empresaId)->get()->getRowArray()['nome'] ?? '';

        $atividades = $db->table('atividade')
            ->select('usuario_nome, acao, modulo_nome, descricao, criado_em')
            ->where('empresa_id', $empresaId)
            ->where('criado_em >=', $inicio->setTimezone($utc)->format('Y-m-d H:i:s'))
            ->where('criado_em <', $fim->setTimezone($utc)->format('Y-m-d H:i:s'))
            ->orderBy('criado_em', 'ASC')
            ->orderBy('id', 'ASC')
            ->limit(HtmlDoRelatorio::MAX_ATIVIDADES + 1)
            ->get()->getResultArray();

        $cortado = count($atividades) > HtmlDoRelatorio::MAX_ATIVIDADES;
        $atividades = array_slice($atividades, 0, HtmlDoRelatorio::MAX_ATIVIDADES);

        // Situação dos prazos no momento da geração.
        $prazos  = new PrazoService();
        $modulos = $prazos->modulosDeDados($empresaId);
        $hoje    = Relogio::hoje();
        $abertos = $prazos->comNomesDosResponsaveis(
            $prazos->itensEmAberto($modulos, array_column($modulos, 'id'), $hoje, 0),
            $empresaId
        );

        $html = HtmlDoRelatorio::montar([
            'titulo'     => $titulo,
            'empresa'    => $empresa,
            'periodo'    => Agenda::textoDoPeriodo($inicio, $fim),
            'gerado_em'  => Relogio::agora()->format('d/m/Y \à\s H:i'),
            'atividades' => $atividades,
            'cortado'    => $cortado,
            'atrasados'  => array_values(array_filter($abertos, static fn ($i) => $i['prazo'] < $hoje)),
            'hoje'       => $hoje,
            'secoes'     => $secoes,
            'indicadores' => array_intersect($secoes, ['resumo', 'recrutamento', 'financeiro', 'prazos']) === []
                ? null
                : (new IndicadoresService())->painel($empresaId, $inicio, $fim),
        ]);

        $conteudo = Pdf::renderizar($html);

        return [
            'conteudo' => $conteudo,
            'total'    => count($atividades),
            'pessoas'  => count(array_unique(array_filter(array_column($atividades, 'usuario_nome')))),
            'empresa'  => $empresa,
        ];
    }

    /**
     * PDF do relatório montado: os mesmos parâmetros de IndicadoresService::agrupar
     * (tabela, coluna, cálculo, período e filtros), com os critérios por extenso.
     *
     * @return array{conteudo: string, arquivo: string}
     */
    public function agrupamento(string $empresaId, array $params, string $titulo = ''): array
    {
        $indicadores = new IndicadoresService();
        $resultado   = $indicadores->agrupar($empresaId, $params); // valida tudo antes de descrever

        $filtros = is_string($params['filtros'] ?? null) ? json_decode($params['filtros'], true) : null;
        $inicio  = is_string($params['inicio'] ?? null) ? \DateTimeImmutable::createFromFormat('!Y-m-d', $params['inicio']) : false;
        $fim     = is_string($params['fim'] ?? null) ? \DateTimeImmutable::createFromFormat('!Y-m-d', $params['fim']) : false;
        $titulo  = $titulo !== '' ? $titulo : "{$resultado['tabela']} por {$resultado['agrupado_por']}";

        $html = HtmlDoAgrupamento::montar([
            'titulo'    => $titulo,
            'empresa'   => db_connect()->table('empresa')->select('nome')->where('id', $empresaId)->get()->getRowArray()['nome'] ?? '',
            'gerado_em' => Relogio::agora()->format('d/m/Y \\à\\s H:i'),
            'periodo'   => $inicio && $fim ? 'Itens criados de ' . $inicio->format('d/m/Y') . ' a ' . $fim->format('d/m/Y') : 'Todos os itens da tabela',
            'filtros'   => is_array($filtros) ? $indicadores->descreverFiltros($empresaId, $filtros) : [],
            'logica'    => ($filtros['logica'] ?? 'e') === 'ou' ? 'ou' : 'e',
            'resultado' => $resultado,
        ]);

        return [
            'conteudo' => Pdf::renderizar($html),
            'arquivo'  => Agenda::nomeDoArquivo($titulo, new \DateTimeImmutable(Relogio::hoje(1))),
        ];
    }
}
