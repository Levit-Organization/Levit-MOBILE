<?php

namespace App\Commands;

use CodeIgniter\CLI\BaseCommand;
use CodeIgniter\CLI\CLI;
use Config\Services;
use App\Services\Email\BrevoEmailService;

class TestEmail extends BaseCommand
{
    protected $group       = 'Email';
    protected $name        = 'email:test';
    protected $description = 'Envia um email de teste usando o serviço Brevo.';
    protected $usage       = 'email:test [email_destino]';
    protected $arguments   = [
        'email_destino' => 'O endereço de email que receberá o teste',
    ];

    public function run(array $params)
    {
        $destino = $params[0] ?? CLI::prompt('Qual o email de destino para o teste?');

        if (!filter_var($destino, FILTER_VALIDATE_EMAIL)) {
            CLI::error('Endereço de email inválido.');
            return;
        }

        CLI::write('Iniciando envio de email para ' . $destino . '...', 'yellow');

        try {
            $emailService = new BrevoEmailService();
            $emailService->enviar(
                $destino,
                'Teste de Integração Brevo - Levit',
                '<h1>Teste de Email</h1><p>Se você está lendo isso, a integração com o Brevo está funcionando perfeitamente no backend do Levit!</p>'
            );
            
            CLI::write('Email enviado com sucesso!', 'green');
        } catch (\Exception $e) {
            CLI::error('Erro ao enviar o email:');
            CLI::error($e->getMessage());
        }
    }
}
