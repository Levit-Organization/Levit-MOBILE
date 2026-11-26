<?php

use App\Services\AtividadeService;
use App\Services\RelatorioService;
use App\Services\RotinaDiariaService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class RelatorioAgendaTest extends CIUnitTestCase
{
    // 2026-10-11 é um domingo.

    public function testUltimaDataAgendadaSemanal(): void
    {
        $this->assertSame('2026-10-05', RelatorioService::ultimaDataAgendada('semanal', 1, '2026-10-11')); // segunda passada
        $this->assertSame('2026-10-11', RelatorioService::ultimaDataAgendada('semanal', 7, '2026-10-11')); // hoje (domingo)
    }

    public function testUltimaDataAgendadaMensal(): void
    {
        $this->assertSame('2026-10-05', RelatorioService::ultimaDataAgendada('mensal', 5, '2026-10-11'));
        $this->assertSame('2026-09-20', RelatorioService::ultimaDataAgendada('mensal', 20, '2026-10-11'));
    }

    public function testDevidoNoDiaEUmaVezSo(): void
    {
        // Criado na semana passada, nunca enviado: domingo é o dia.
        $this->assertTrue(RelatorioService::estaDevido('semanal', 7, '2026-10-11', null, '2026-10-01 12:00:00'));
        // Já enviado hoje de manhã (10h UTC = 7h em Brasília): não manda de novo.
        $this->assertFalse(RelatorioService::estaDevido('semanal', 7, '2026-10-11', '2026-10-11 10:00:00', '2026-10-01 12:00:00'));
        // Criado hoje para hoje: o primeiro envio é na próxima semana.
        $this->assertFalse(RelatorioService::estaDevido('semanal', 7, '2026-10-11', null, '2026-10-11 12:00:00'));
    }

    public function testSeOCronFalharEnviaNoDiaSeguinte(): void
    {
        // Agendado para sábado (6); a rotina não rodou sábado; domingo ainda envia.
        $this->assertTrue(RelatorioService::estaDevido('semanal', 6, '2026-10-11', '2026-10-03 10:00:00', '2026-09-01 12:00:00'));
    }

    public function testProximaData(): void
    {
        $this->assertSame('2026-10-12', RelatorioService::proximaData('semanal', 1, '2026-10-11', null));
        $this->assertSame('2026-10-11', RelatorioService::proximaData('semanal', 7, '2026-10-11', null));
        $this->assertSame('2026-10-18', RelatorioService::proximaData('semanal', 7, '2026-10-11', '2026-10-11 10:00:00'));
        $this->assertSame('2026-11-05', RelatorioService::proximaData('mensal', 5, '2026-10-11', null));
    }

    public function testPeriodoCobreOsDiasAnteriores(): void
    {
        [$inicio, $fim] = RelatorioService::periodo('semanal', '2026-10-11');
        $this->assertSame('04/10/2026 a 10/10/2026', RelatorioService::textoDoPeriodo($inicio, $fim));
        [$inicio, $fim] = RelatorioService::periodo('mensal', '2026-10-05');
        $this->assertSame('05/09/2026 a 04/10/2026', RelatorioService::textoDoPeriodo($inicio, $fim));
    }

    public function testNomeDoArquivoSemAcento(): void
    {
        [, $fim] = RelatorioService::periodo('semanal', '2026-10-11');
        $this->assertSame('relatorio-semanal-da-equipe-2026-10-10.pdf', RelatorioService::nomeDoArquivo('Relatório semanal da equipe', $fim));
    }

    public function testHtmlDoRelatorioEscapaOQueVemDasTabelas(): void
    {
        $html = RelatorioService::htmlDoRelatorio([
            'titulo' => 'Semanal', 'empresa' => 'Acme', 'periodo' => 'x', 'gerado_em' => 'y', 'cortado' => false, 'hoje' => '2026-10-11',
            'atrasados' => [],
            'atividades' => [['usuario_nome' => '<b>Ana</b>', 'acao' => 'item_criado', 'modulo_nome' => 'Clientes', 'descricao' => 'Criou “<script>alert(1)</script>”', 'criado_em' => '2026-10-10 12:00:00']],
        ]);
        $this->assertStringNotContainsString('<script>', $html);
        $this->assertStringContainsString('&lt;b&gt;Ana&lt;/b&gt;', $html);
        $this->assertStringContainsString('09:00', $html); // 12h UTC = 9h em Brasília
    }

    public function testDescricaoDoItemMostraOQueMudou(): void
    {
        $campos = [
            'c1' => ['id' => 'c1', 'nome' => 'Cliente', 'tipo' => 'texto'],
            'c2' => ['id' => 'c2', 'nome' => 'Status', 'tipo' => 'selecao'],
            'c3' => ['id' => 'c3', 'nome' => 'Responsável', 'tipo' => 'responsavel'],
            'c4' => ['id' => 'c4', 'nome' => 'Vencimento', 'tipo' => 'prazo'],
        ];
        $uuid = '0190a000-0000-7000-8000-000000000001';
        $e    = [
            'acao'        => 'item_alterado',
            '_dados'      => ['c1' => 'Padaria', 'c2' => 'Entregue', 'c3' => $uuid, 'c4' => '2026-10-20'],
            '_anteriores' => ['c1' => 'Padaria', 'c2' => 'A fazer', 'c4' => '2026-10-15'],
        ];
        $this->assertSame(
            'Alterou “Padaria”: Status: A fazer → Entregue; Responsável: — → Ana Souza; Vencimento: 15/10/2026 → 20/10/2026',
            AtividadeService::descreverItem($e, $campos, [$uuid => 'Ana Souza'])
        );
        $this->assertSame('Criou “Padaria”', AtividadeService::descreverItem(['acao' => 'item_criado'] + $e, $campos, []));
    }

    public function testRotulosEAvisos(): void
    {
        $this->assertSame('Atrasado há 3 dias', RotinaDiariaService::rotulo('2026-10-08', '2026-10-11'));
        $this->assertSame('Atrasado desde ontem', RotinaDiariaService::rotulo('2026-10-10', '2026-10-11'));
        $this->assertSame('Vence hoje', RotinaDiariaService::rotulo('2026-10-11', '2026-10-11'));
        $this->assertSame('Vence amanhã', RotinaDiariaService::rotulo('2026-10-12', '2026-10-11'));
        $this->assertSame('Vence em 3 dias', RotinaDiariaService::rotulo('2026-10-14', '2026-10-11'));

        $grupos = ['atrasados' => [1, 2], 'hoje' => [1], 'proximos' => []];
        $this->assertSame('Seus prazos: 2 atrasados, 1 vence hoje', RotinaDiariaService::tituloDaNotificacao($grupos));
        $this->assertSame('1 item atrasado está sem responsável', RotinaDiariaService::tituloDaNotificacao(['atrasados' => [], 'hoje' => [], 'proximos' => []], 1));
    }
}
