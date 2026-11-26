<?php

use App\Services\IndicadoresService;
use App\Services\RelatorioService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class IndicadoresTest extends CIUnitTestCase
{
    private const PERIODOS = [
        'atual'    => ['2026-10-01 00:00:00', '2026-10-08 00:00:00'],
        'anterior' => ['2026-09-24 00:00:00', '2026-10-01 00:00:00'],
    ];

    private const FASES = [
        ['id' => 'f1', 'nome' => 'Inscrito', 'ordem' => 1],
        ['id' => 'f2', 'nome' => 'Entrevista', 'ordem' => 2],
        ['id' => 'f3', 'nome' => 'Contratado', 'ordem' => 3],
        ['id' => 'fx', 'nome' => 'Reprovado', 'ordem' => 4],
    ];

    public function testVariacaoEPeriodoAnterior(): void
    {
        $this->assertSame(50.0, IndicadoresService::variacao(15, 10));
        $this->assertSame(-25.0, IndicadoresService::variacao(3, 4));
        $this->assertSame(0.0, IndicadoresService::variacao(0, 0));
        $this->assertNull(IndicadoresService::variacao(5, 0)); // sem base: "novo"

        $inicio = new DateTimeImmutable('2026-10-01');
        [$antInicio, $antFim] = IndicadoresService::periodoAnterior($inicio, new DateTimeImmutable('2026-10-08'));
        $this->assertSame('2026-09-24', $antInicio->format('Y-m-d'));
        $this->assertSame('2026-10-01', $antFim->format('Y-m-d'));
    }

    public function testFunilContaAteOndeCadaInscritoChegou(): void
    {
        $candidatos = [
            // Inscrito no período, foi à entrevista e foi contratado.
            ['id' => 'c1', 'fase_atual_id' => 'f3', 'criado_em' => '2026-10-01 10:00:00'],
            // Inscrito no período, entrevistado e reprovado: conta até a entrevista.
            ['id' => 'c2', 'fase_atual_id' => 'fx', 'criado_em' => '2026-10-02 10:00:00'],
            // Inscrito no período, parado na primeira fase.
            ['id' => 'c3', 'fase_atual_id' => 'f1', 'criado_em' => '2026-10-03 10:00:00'],
            // Inscrito no período anterior: fora do funil deste período.
            ['id' => 'c4', 'fase_atual_id' => 'f2', 'criado_em' => '2026-09-25 10:00:00'],
        ];
        $historico = [
            ['candidato_id' => 'c1', 'fase_anterior_id' => 'f1', 'fase_nova_id' => 'f2', 'criado_em' => '2026-10-03 10:00:00'],
            ['candidato_id' => 'c1', 'fase_anterior_id' => 'f2', 'fase_nova_id' => 'f3', 'criado_em' => '2026-10-05 10:00:00'],
            ['candidato_id' => 'c2', 'fase_anterior_id' => 'f1', 'fase_nova_id' => 'f2', 'criado_em' => '2026-10-04 10:00:00'],
            ['candidato_id' => 'c2', 'fase_anterior_id' => 'f2', 'fase_nova_id' => 'fx', 'criado_em' => '2026-10-06 10:00:00'],
            ['candidato_id' => 'c4', 'fase_anterior_id' => 'f1', 'fase_nova_id' => 'f2', 'criado_em' => '2026-09-26 10:00:00'],
        ];

        $funil = IndicadoresService::funil(self::FASES, $candidatos, $historico, self::PERIODOS, '2026-10-08 12:00:00');
        $fases = array_column($funil['fases'], null, 'id');

        $this->assertSame(3, $fases['f1']['chegaram']);
        $this->assertSame(2, $fases['f2']['chegaram']);
        $this->assertSame(1, $fases['f3']['chegaram']);
        $this->assertTrue($fases['fx']['saida']);
        $this->assertNull($fases['fx']['chegaram']);

        $this->assertSame(1, $fases['f3']['atual']);
        $this->assertSame(1, $fases['fx']['atual']);

        // Em Inscrito: c1 ficou 2 dias, c2 ficou 2 dias (c4 saiu no período anterior).
        $this->assertSame(2.0, $fases['f1']['dias_medios']);

        $this->assertSame(['atual' => 3, 'anterior' => 1, 'variacao' => 200.0], $funil['inscritos']);
        $this->assertSame(1, $funil['concluidos']['atual']);
        $this->assertSame(1, $funil['sairam']['atual']);
        $this->assertSame(4, $funil['total']);
    }

    public function testCandidatoParadoHaMaisDeDuasSemanas(): void
    {
        $candidatos = [
            ['id' => 'c1', 'fase_atual_id' => 'f2', 'criado_em' => '2026-09-01 10:00:00'],
            ['id' => 'c2', 'fase_atual_id' => 'f3', 'criado_em' => '2026-09-01 10:00:00'], // contratado: não conta
            ['id' => 'c3', 'fase_atual_id' => 'f1', 'criado_em' => '2026-10-07 10:00:00'], // recente
        ];
        $funil = IndicadoresService::funil(self::FASES, $candidatos, [], self::PERIODOS, '2026-10-08 12:00:00');
        $this->assertSame(1, $funil['parados']);
    }

    public function testFaseDeSaidaIgnoraAcentoEMaiuscula(): void
    {
        $this->assertTrue(IndicadoresService::faseDeSaida('Não aprovado'));
        $this->assertTrue(IndicadoresService::faseDeSaida('DESISTIU'));
        $this->assertFalse(IndicadoresService::faseDeSaida('Entrevista'));
    }

    public function testMontarGruposOrdenaETrataVazios(): void
    {
        $linhas = [
            ['grupo' => 'A fazer', 'total' => 2, 'soma' => '10.5', 'media' => '5.25'],
            ['grupo' => null, 'total' => 1, 'soma' => null, 'media' => null],
            ['grupo' => 'Feito', 'total' => 5, 'soma' => '20', 'media' => '4'],
        ];
        $r = IndicadoresService::montarGrupos($linhas, 'contar', 'selecao');
        $this->assertSame(['Feito', 'A fazer', 'Sem valor'], array_column($r['grupos'], 'rotulo'));
        $this->assertSame(8, $r['total_itens']);

        $soma = IndicadoresService::montarGrupos($linhas, 'somar', 'selecao');
        $this->assertSame([20.0, 10.5, 0.0], array_column($soma['grupos'], 'valor'));

        $uuid   = '0190a000-0000-7000-8000-000000000001';
        $pessoa = IndicadoresService::montarGrupos([['grupo' => $uuid, 'total' => 3, 'soma' => null, 'media' => null]], 'contar', 'responsavel', [$uuid => 'Ana']);
        $this->assertSame('Ana', $pessoa['grupos'][0]['rotulo']);

        $meses = IndicadoresService::montarGrupos([
            ['grupo' => '2026-10', 'total' => 1, 'soma' => null, 'media' => null],
            ['grupo' => '2026-08', 'total' => 9, 'soma' => null, 'media' => null],
        ], 'contar', 'prazo');
        $this->assertSame(['2026-08', '2026-10'], array_column($meses['grupos'], 'rotulo'));
    }

    public function testSecoesDoRelatorio(): void
    {
        $this->assertSame(['resumo', 'prazos', 'atividades'], RelatorioService::secoesValidas(['atividades', 'resumo', 'xss', 'prazos']));
        $this->assertSame(RelatorioService::SECOES_PADRAO, RelatorioService::secoesValidas([]));
        // Relatórios antigos (sem seções gravadas) continuam com o formato de antes.
        $this->assertSame(['atrasados', 'ranking', 'atividades'], RelatorioService::secoesDoRelatorio(['secoes' => null]));
        $this->assertSame(['financeiro'], RelatorioService::secoesDoRelatorio(['secoes' => '["financeiro"]']));
    }

    public function testRelatorioDiario(): void
    {
        $this->assertSame('Todo dia', RelatorioService::descreverAgenda('diario', 1));
        $this->assertTrue(RelatorioService::estaDevido('diario', 1, '2026-10-11', '2026-10-10 10:00:00', '2026-10-01 12:00:00'));
        $this->assertFalse(RelatorioService::estaDevido('diario', 1, '2026-10-11', '2026-10-11 10:00:00', '2026-10-01 12:00:00'));
        $this->assertSame('2026-10-12', RelatorioService::proximaData('diario', 1, '2026-10-11', '2026-10-11 10:00:00'));

        [$inicio, $fim] = RelatorioService::periodo('diario', '2026-10-11');
        $this->assertSame('10/10/2026', RelatorioService::textoDoPeriodo($inicio, $fim));
    }

    public function testPdfMostraSoAsSecoesEscolhidasComVariacao(): void
    {
        $indicadores = [
            'periodo_anterior' => ['inicio' => '2026-09-24', 'fim' => '2026-09-30'],
            'resumo'           => array_fill_keys(['atividades', 'pessoas_ativas', 'itens_criados', 'candidatos'], ['atual' => 12, 'anterior' => 10, 'variacao' => 20.0]),
            'recrutamento'     => [],
            'financeiro'       => [[
                'nome'                   => '<Caixa>',
                'entradas'               => ['atual' => 1000.0, 'anterior' => 500.0, 'variacao' => 100.0],
                'saidas'                 => ['atual' => 800.0, 'anterior' => 400.0, 'variacao' => 100.0],
                'saldo'                  => ['atual' => 200.0, 'anterior' => 100.0, 'variacao' => 100.0],
                'saidas_por_categoria'   => [['categoria' => 'Fixo', 'total' => 800.0]],
                'entradas_por_categoria' => [],
                'meses'                  => [],
            ]],
            'prazos' => [],
        ];
        $html = RelatorioService::htmlDoRelatorio([
            'titulo' => 'Semanal', 'empresa' => 'Acme', 'periodo' => 'x', 'gerado_em' => 'y', 'cortado' => false, 'hoje' => '2026-10-11',
            'atrasados' => [], 'atividades' => [], 'secoes' => ['resumo', 'financeiro'], 'indicadores' => $indicadores,
        ]);

        $this->assertStringContainsString('Comparado ao período anterior', $html);
        $this->assertStringContainsString('R$ 1.000,00', $html);
        $this->assertStringContainsString('&lt;Caixa&gt;', $html);
        // Saídas subindo é ruim (vermelho); entradas subindo é bom (verde).
        $this->assertMatchesRegularExpression('/Saídas.*?class="ruim">\+100%/s', $html);
        $this->assertMatchesRegularExpression('/Entradas.*?class="bom">\+100%/s', $html);
        $this->assertStringNotContainsString('Quem mais fez', $html);
        $this->assertStringNotContainsString('Tudo o que foi feito', $html);
    }

    public function testAgruparPorLigacaoMostraONomeDoItemEContaCadaItemUmaVez(): void
    {
        $ana   = '0190a000-0000-7000-8000-00000000000a';
        $sumiu = '0190a000-0000-7000-8000-00000000000b';
        $semNome = '0190a000-0000-7000-8000-00000000000c';
        $r = IndicadoresService::montarGrupos([
            ['grupo' => $ana, 'total' => 3, 'soma' => null, 'media' => null],
            ['grupo' => $sumiu, 'total' => 1, 'soma' => null, 'media' => null],
            ['grupo' => $semNome, 'total' => 1, 'soma' => null, 'media' => null],
            ['grupo' => null, 'total' => 2, 'soma' => null, 'media' => null],
        ], 'contar', 'vinculo', [$ana => 'Ana', $semNome => null], 5);

        $this->assertSame(['Ana', 'Item excluído', 'Item sem nome', 'Sem item ligado'], array_column($r['grupos'], 'rotulo'));
        $this->assertSame(5, $r['total_itens'], 'item ligado a vários clientes aparece em vários grupos, mas conta uma vez');
    }

    public function testColunaDeAgrupamentoAceitaColunaDaTabelaLigada(): void
    {
        $campos = [
            'cliente' => ['id' => 'cliente', 'modulo_id' => 'ped', 'nome' => 'Cliente', 'tipo' => 'vinculo', 'modulo_vinculado_id' => 'cli'],
            'status'  => ['id' => 'status', 'modulo_id' => 'ped', 'nome' => 'Status', 'tipo' => 'selecao', 'modulo_vinculado_id' => null],
            'cidade'  => ['id' => 'cidade', 'modulo_id' => 'cli', 'nome' => 'Cidade', 'tipo' => 'texto', 'modulo_vinculado_id' => null],
            'matriz'  => ['id' => 'matriz', 'modulo_id' => 'cli', 'nome' => 'Matriz', 'tipo' => 'vinculo', 'modulo_vinculado_id' => 'cli'],
        ];

        [$coluna, $ligacao] = IndicadoresService::colunaDeAgrupamento('cliente>cidade', 'ped', $campos);
        $this->assertSame('Cliente › Cidade', $coluna['nome']);
        $this->assertSame('cliente', $ligacao['id']);

        [$direta, $semLigacao] = IndicadoresService::colunaDeAgrupamento('cliente', 'ped', $campos);
        $this->assertSame('vinculo', $direta['tipo']);
        $this->assertNull($semLigacao);

        foreach (['cidade', 'cliente>matriz', 'status>cidade', 'cliente>status', 'x>y'] as $invalida) {
            try {
                IndicadoresService::colunaDeAgrupamento($invalida, 'ped', $campos);
                $this->fail("Deveria recusar {$invalida}");
            } catch (\DomainException) {
                $this->addToAssertionCount(1);
            }
        }
    }
}
