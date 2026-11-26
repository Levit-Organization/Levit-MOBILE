<?php

namespace App\Services\Relatorios;

use Dompdf\Dompdf;
use Dompdf\Options;

/** HTML → PDF e o que os PDFs de relatório têm em comum (estilo e números). */
final class Pdf
{
    /** HTML → PDF (A4), com "Página X de Y" no rodapé de todas as páginas. */
    public static function renderizar(string $html): string
    {
        $opcoes = new Options();
        $opcoes->set('defaultFont', 'DejaVu Sans');
        $opcoes->set('isRemoteEnabled', false);
        $opcoes->set('isPhpEnabled', false);
        if (defined('WRITEPATH')) {
            $opcoes->set('tempDir', WRITEPATH . 'cache');
            $opcoes->set('fontCache', WRITEPATH . 'cache');
        }

        $dompdf = new Dompdf($opcoes);
        $dompdf->loadHtml($html, 'UTF-8');
        $dompdf->setPaper('A4');
        $dompdf->render();

        $canvas = $dompdf->getCanvas();
        $fonte  = $dompdf->getFontMetrics()->getFont('DejaVu Sans');
        $canvas->page_text($canvas->get_width() - 105, $canvas->get_height() - 34, 'Página {PAGE_NUM} de {PAGE_COUNT}', $fonte, 7, [0.54, 0.53, 0.61]);

        return (string) $dompdf->output();
    }

    public static function numero(float|int $n, int $casas = 0): string
    {
        return number_format((float) $n, $casas, ',', '.');
    }

    public static function moeda(float|int $n): string
    {
        return ($n < 0 ? '-' : '') . 'R$ ' . number_format(abs((float) $n), 2, ',', '.');
    }

    /** CSS comum aos PDFs (atividades e relatório montado). */
    public static function estilo(): string
    {
        return '  @page { margin: 22mm 16mm 18mm; }
  body { font-family: "DejaVu Sans", sans-serif; font-size: 9.5pt; color: #24223A; }
  .rodape { position: fixed; bottom: -11mm; left: 0; right: 30mm; font-size: 7.5pt; color: #8A879C; }
  .marca { font-size: 10pt; font-weight: bold; color: #534BAF; letter-spacing: .5px; }
  h1 { font-size: 20pt; margin: 4px 0 2px; color: #15132A; }
  h2 { font-size: 12pt; margin: 22px 0 8px; color: #15132A; border-bottom: 1.5px solid #15132A; padding-bottom: 4px; }
  h3 { font-size: 10pt; margin: 16px 0 6px; color: #15132A; }
  h2, h3 { page-break-after: avoid; }
  .sub { color: #5D5A72; margin: 0; }
  .muted { color: #8A879C; font-weight: normal; }
  .numeros { width: 100%; border-collapse: separate; border-spacing: 8px 0; margin: 18px -8px 0; }
  .numeros td { background: #F3F2FA; border-radius: 6px; padding: 10px 12px; width: 25%; }
  .numeros .n { font-size: 18pt; font-weight: bold; color: #534BAF; display: block; }
  .numeros .r { font-size: 8pt; color: #5D5A72; }
  .colunas { width: 100%; border-collapse: collapse; }
  .colunas > tbody > tr > td { width: 50%; vertical-align: top; padding-right: 14px; }
  .rank { width: 100%; border-collapse: collapse; }
  .rank td { padding: 3px 0; font-size: 8.5pt; }
  .rank-nome { width: 42%; padding-right: 6px; }
  .rank-barra div { height: 7px; background: #8D86D9; border-radius: 3px; }
  .rank-n { width: 90px; text-align: right; color: #5D5A72; }
  .lista { width: 100%; border-collapse: collapse; }
  .lista th { text-align: left; font-size: 7.5pt; color: #8A879C; font-weight: normal; border-bottom: 1px solid #DDDBEA; padding: 4px 6px 4px 0; }
  .lista td { border-bottom: 1px solid #EEEDF5; padding: 5px 6px 5px 0; vertical-align: top; }
  .lista .hora { width: 38px; color: #5D5A72; }
  .lista .quem { width: 120px; font-weight: bold; }
  .onde { font-size: 7.5pt; color: #8A879C; margin-top: 1px; }
  .atraso { color: #B42318; font-weight: bold; width: 55px; }
  .aviso { background: #FFF6E5; color: #8A5A00; padding: 6px 8px; border-radius: 4px; }
  .lista .num { text-align: right; width: 95px; }
  .bom { color: #067647; font-weight: bold; }
  .ruim { color: #B42318; font-weight: bold; }
  .criterios { width: 100%; border-collapse: collapse; margin-top: 4px; }
  .criterios td { padding: 3px 0; vertical-align: top; }
  .criterios .rotulo { width: 120px; color: #8A879C; }
  .filtros { margin: 0; padding-left: 14px; }
  .filtros li { margin: 1px 0; }
  .grupos { width: 100%; border-collapse: collapse; }
  .grupos th { text-align: left; font-size: 7.5pt; color: #8A879C; font-weight: normal; border-bottom: 1px solid #DDDBEA; padding: 4px 6px 4px 0; }
  .grupos td { border-bottom: 1px solid #EEEDF5; padding: 5px 6px 5px 0; vertical-align: middle; }
  .grupos .barra { width: 34%; }
  .grupos .barra div { height: 8px; background: #8D86D9; border-radius: 3px; }
  .grupos .num { text-align: right; width: 80px; white-space: nowrap; }
  .grupos tfoot td { font-weight: bold; border-top: 1.5px solid #15132A; border-bottom: none; }
';
    }
}
