<?php

use App\Services\IndicadoresService;
use App\Services\RelatorioService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Montagem do HTML dos PDFs (relatório agendado e relatório montado) nas
 * partes que os outros testes não cobrem: ranking, itens atrasados, seções
 * de recrutamento e prazos, variações e avisos.
 *
 * @internal
 */
final class RelatorioHtmlTest extends CIUnitTestCase
{
    private function base(array $extra = []): array
    {
        return $extra + [
            'titulo' => 'Semanal', 'empresa' => 'Acme', 'periodo' => 'x', 'gerado_em' => 'y', 'cortado' => false, 'hoje' => '2026-10-11',
            'atrasados' => [], 'atividades' => [],
        ];
    }

    private function indicadores(array $extra = []): array
    {
        $c = static fn ($a, $b) => IndicadoresService::comparar($a, $b);

        return $extra + [
            'periodo_anterior' => ['inicio' => '2026-09-24', 'fim' => '2026-09-30'],
            'resumo'           => ['atividades' => $c(12, 0), 'pessoas_ativas' => $c(3, 3), 'itens_criados' => $c(0, 0), 'candidatos' => $c(5, 10)],
            'recrutamento'     => [[
                'nome' => 'Vaga <Dev>', 'total' => 7, 'parados' => 2,
                'inscritos' => $c(4, 2), 'concluidos' => $c(1, 1), 'sairam' => $c(2, 1),
                'fases' => [
                    ['nome' => 'Inscrito', 'atual' => 3, 'chegaram' => 4, 'saida' => false, 'dias_medios' => 2.5],
                    ['nome' => 'Reprovado', 'atual' => 2, 'chegaram' => null, 'saida' => true, 'dias_medios' => null],
                ],
            ]],
            'financeiro' => [[
                'nome' => 'Caixa', 'entradas' => $c(0, 0), 'saidas' => $c(20.25, 0), 'saldo' => $c(-20.25, 0),
                'saidas_por_categoria' => [], 'entradas_por_categoria' => [], 'meses' => [],
            ]],
            'prazos' => [
                'vencendo' => $c(10, 8), 'concluidos' => $c(7, 8), 'taxa_conclusao' => ['atual' => 70.0, 'anterior' => null],
                'atrasados_agora' => 3, 'atrasados_por_responsavel' => [['responsavel' => 'Bia', 'total' => 3]],
            ],
        ];
    }

    public function testRelatorioAntigoMostraAtrasadosRankingEAtividades(): void
    {
        $atrasados = [];
        for ($i = 0; $i < 22; $i++) {
            $atrasados[] = ['titulo' => $i === 0 ? '' : "Item {$i}", 'modulo_nome' => 'Tarefas', 'responsavel_nome' => $i === 0 ? '' : 'Ana', 'prazo' => '2026-10-10'];
        }
        $atividades = [
            ['usuario_nome' => 'Ana', 'acao' => 'item_criado', 'modulo_nome' => 'Clientes', 'descricao' => 'Criou', 'criado_em' => '2026-10-10 12:00:00'],
            ['usuario_nome' => 'Ana', 'acao' => 'item_criado', 'modulo_nome' => 'Clientes', 'descricao' => 'Criou', 'criado_em' => '2026-10-10 13:00:00'],
            ['usuario_nome' => '', 'acao' => 'item_criado', 'modulo_nome' => null, 'descricao' => 'Rodou', 'criado_em' => '2026-10-09 12:00:00'],
        ];

        // Sem `secoes`: relatório criado antes da escolha de seções.
        $html = RelatorioService::htmlDoRelatorio($this->base(['atrasados' => $atrasados, 'atividades' => $atividades, 'cortado' => true]));

        $this->assertStringContainsString('Itens atrasados agora', $html);
        $this->assertStringContainsString('Item sem título', $html);
        $this->assertStringContainsString('Sem responsável', $html);
        $this->assertStringContainsString('1 dia', $html);
        $this->assertStringContainsString('e mais 2 itens atrasados.', $html);
        $this->assertStringContainsString('Quem mais fez', $html);
        $this->assertStringContainsString('Onde mais mexeram', $html);
        $this->assertStringContainsString('O período teve mais de 3000 atividades', $html);
        $this->assertStringContainsString('Sexta, 09/10/2026', $html);
        $this->assertStringContainsString('<span class="n">3</span><span class="r">atividades no período</span>', $html);
        // "Sistema" (sem nome) não conta como pessoa ativa.
        $this->assertStringContainsString('<span class="n">1</span><span class="r">pessoa ativa</span>', $html);
        $this->assertStringContainsString('<span class="n">22</span><span class="r">itens atrasados hoje</span>', $html);
        $this->assertStringNotContainsString('Comparado ao período anterior', $html);
    }

    public function testSemAtividadesNemAtrasados(): void
    {
        $html = RelatorioService::htmlDoRelatorio($this->base(['secoes' => ['atrasados', 'atividades']]));

        $this->assertStringContainsString('Nenhum item atrasado no momento', $html);
        $this->assertStringContainsString('Nenhuma atividade registrada neste período.', $html);
        $this->assertStringNotContainsString('Quem mais fez', $html);
    }

    public function testSecoesDeIndicadoresSemDadosNaoAparecem(): void
    {
        $html = RelatorioService::htmlDoRelatorio($this->base(['secoes' => ['resumo', 'prazos'], 'indicadores' => null]));

        $this->assertStringNotContainsString('Comparado ao período anterior', $html);
        $this->assertStringNotContainsString('<h2>Prazos</h2>', $html);
    }

    public function testRecrutamentoEPrazos(): void
    {
        $html = RelatorioService::htmlDoRelatorio($this->base(['secoes' => ['recrutamento', 'prazos'], 'indicadores' => $this->indicadores()]));

        $this->assertStringContainsString('<h2>Recrutamento</h2>', $html);
        $this->assertStringContainsString('Vaga &lt;Dev&gt;', $html);
        $this->assertStringContainsString('7 candidatos no total', $html);
        $this->assertStringContainsString('2 candidato(s) parado(s) na mesma etapa há mais de ' . IndicadoresService::DIAS_PARADO . ' dias.', $html);
        $this->assertStringContainsString('2,5 dias', $html);
        $this->assertStringContainsString('<span class="muted">saída</span>', $html);
        // Mais gente saindo é ruim.
        $this->assertMatchesRegularExpression('/Saíram do processo.*?class="ruim">\+100%/s', $html);

        $this->assertStringContainsString('<h2>Prazos</h2>', $html);
        $this->assertStringContainsString('Taxa de conclusão: <b>70%</b>', $html);
        $this->assertStringNotContainsString('(período anterior:', $html);
        $this->assertStringContainsString('3 item(ns) atrasado(s) agora.', $html);
        $this->assertStringContainsString('Atrasados por responsável', $html);
        $this->assertStringContainsString('Bia', $html);
    }

    public function testVariacaoNovaIgualENegativaEMoedaNegativa(): void
    {
        $html = RelatorioService::htmlDoRelatorio($this->base(['secoes' => ['resumo', 'financeiro'], 'indicadores' => $this->indicadores()]));

        $this->assertStringContainsString('Período anterior: 24/09/2026 a 30/09/2026', $html);
        $this->assertMatchesRegularExpression('/Atividades na plataforma.*?<span class="muted">novo<\/span>/s', $html);
        $this->assertMatchesRegularExpression('/Pessoas ativas.*?<span class="muted">igual<\/span>/s', $html);
        $this->assertMatchesRegularExpression('/Candidatos inscritos.*?class="ruim">-50%/s', $html);
        $this->assertStringContainsString('-R$ 20,25', $html);
        $this->assertStringContainsString('Nenhuma saída no período.', $html);
    }

    public function testRecrutamentoEFinanceiroVaziosNaoAparecem(): void
    {
        $html = RelatorioService::htmlDoRelatorio($this->base([
            'secoes'      => ['recrutamento', 'financeiro'],
            'indicadores' => $this->indicadores(['recrutamento' => [], 'financeiro' => []]),
        ]));

        $this->assertStringNotContainsString('<h2>Recrutamento</h2>', $html);
        $this->assertStringNotContainsString('<h2>Financeiro</h2>', $html);
    }

    public function testDescricaoDaAgenda(): void
    {
        $this->assertSame('Toda segunda-feira', RelatorioService::descreverAgenda('semanal', 1));
        $this->assertSame('Toda domingo', RelatorioService::descreverAgenda('semanal', 7));
        $this->assertSame('Todo dia 15 do mês', RelatorioService::descreverAgenda('mensal', 15));
    }

    public function testAgrupamentoContandoAvisaGruposCortadosEItensRepetidos(): void
    {
        $html = RelatorioService::htmlDoAgrupamento([
            'titulo' => 'Pedidos por cliente', 'empresa' => 'Acme', 'gerado_em' => 'g', 'periodo' => 'Todos os itens da tabela',
            'filtros' => ['Status é igual a “Aberto”', 'Valor maior que 10'], 'logica' => 'ou',
            'resultado' => [
                'tabela' => 'Pedidos', 'agrupado_por' => 'Cliente', 'operacao' => 'contar', 'coluna_valor' => null, 'tipo_grupo' => 'vinculo',
                'grupos' => [['rotulo' => 'Ana', 'valor' => 3, 'itens' => 3], ['rotulo' => 'Bia', 'valor' => 2, 'itens' => 2]],
                'total_itens' => 4, 'cortados' => 7,
            ],
        ]);

        $this->assertStringContainsString('Quantidade de itens', $html);
        $this->assertStringContainsString('Itens que atendem a qualquer um destes:', $html);
        $this->assertStringContainsString('Mostrando os 2 maiores grupos; 7 ficaram de fora.', $html);
        $this->assertStringContainsString('Itens ligados a mais de um grupo aparecem em cada um deles', $html);
        $this->assertStringContainsString('60,0%', $html);
        $this->assertStringContainsString('maior grupo: Ana', $html);
        $this->assertStringNotContainsString('<th class="num">Itens</th>', $html);
    }
}
