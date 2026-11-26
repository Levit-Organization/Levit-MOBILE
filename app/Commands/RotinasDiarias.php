<?php

namespace App\Commands;

use App\Libraries\Relogio;
use App\Services\RotinaDiariaService;
use CodeIgniter\CLI\BaseCommand;
use CodeIgniter\CLI\CLI;

/**
 * Avisos de prazo (notificação + resumo por e-mail) e relatórios agendados.
 * Agendar uma vez por dia, de manhã, no horário de Brasília (cron):
 *   0 7 * * *  cd /caminho/api-backend && php spark rotinas:diarias
 * Rodar de novo no mesmo dia não repete nada.
 */
class RotinasDiarias extends BaseCommand
{
    protected $group       = 'Levit';
    protected $name        = 'rotinas:diarias';
    protected $description = 'Envia os avisos de prazo do dia e os relatórios agendados.';
    protected $usage       = 'rotinas:diarias [--data AAAA-MM-DD] [--forcar]';
    protected $options     = [
        '--data'   => 'Executa como se fosse este dia (padrão: hoje, horário de Brasília).',
        '--forcar' => 'Manda os avisos de prazo de novo, mesmo se já foram enviados hoje.',
    ];

    public function run(array $params)
    {
        $data = CLI::getOption('data') ?: Relogio::hoje();
        if (! preg_match('/^\d{4}-\d{2}-\d{2}$/', $data)) {
            CLI::error('Use a data no formato AAAA-MM-DD.');

            return EXIT_USER_INPUT;
        }

        $resultado = (new RotinaDiariaService())->executar($data, CLI::getOption('forcar') !== null);

        if ($resultado['avisos'] === null) {
            CLI::write("Avisos de prazo de {$data}: já tinham sido enviados.", 'yellow');
        } else {
            $a = $resultado['avisos'];
            CLI::write("Avisos de prazo de {$data}: {$a['pessoas']} pessoa(s) avisada(s), {$a['emails']} e-mail(s), {$a['empresas']} empresa(s).", 'green');
        }

        if ($resultado['relatorios'] === []) {
            CLI::write('Nenhum relatório agendado para hoje.');
        }
        foreach ($resultado['relatorios'] as $r) {
            CLI::write("Relatório “{$r['relatorio']}”: {$r['status']}.", $r['status'] === 'enviado' ? 'green' : 'red');
        }

        \App\Services\AtividadeService::gravar();
    }
}
