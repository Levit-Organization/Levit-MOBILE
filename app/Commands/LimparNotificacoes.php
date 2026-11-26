<?php

namespace App\Commands;

use App\Services\NotificacaoService;
use CodeIgniter\CLI\BaseCommand;
use CodeIgniter\CLI\CLI;

/**
 * Retenção das notificações. Agendar uma vez por dia (cron):
 *   php spark notificacoes:limpar
 */
class LimparNotificacoes extends BaseCommand
{
    protected $group       = 'Levit';
    protected $name        = 'notificacoes:limpar';
    protected $description = 'Apaga notificações lidas há mais de N dias (padrão: 90).';
    protected $usage       = 'notificacoes:limpar [--dias 90]';
    protected $options     = ['--dias' => 'Idade mínima, em dias, das notificações lidas a apagar.'];

    public function run(array $params)
    {
        $dias     = max(1, (int) (CLI::getOption('dias') ?? 90));
        $apagadas = (new NotificacaoService())->limparLidasAntigas($dias);

        CLI::write("{$apagadas} notificação(ões) lida(s) há mais de {$dias} dias apagada(s).", 'green');
    }
}
