<?php

namespace App\Commands;

use CodeIgniter\Commands\Server\Serve as ServeDoFramework;

/**
 * O `php spark serve` de sempre, com os limites de upload que o sistema usa.
 *
 * O PHP vem com upload_max_filesize = 2M, abaixo dos 10 MB aceitos pela tabela
 * de arquivos, e o servidor embutido não lê .user.ini. Sem isto, todo arquivo
 * acima de 2 MB falha em qualquer máquina com o php.ini padrão.
 * Por ter o mesmo nome, este comando substitui o do framework.
 */
class Serve extends ServeDoFramework
{
    private const LIMITES = [
        'upload_max_filesize' => '12M',
        'post_max_size'       => '13M',
    ];

    protected function buildServeCommand(string $php, string $host, int $port, string $docroot, string $rewrite): string
    {
        $comando = parent::buildServeCommand($php, $host, $port, $docroot, $rewrite);
        $opcoes  = '';

        foreach (self::LIMITES as $diretiva => $valor) {
            $opcoes .= ' -d ' . escapeshellarg("{$diretiva}={$valor}");
        }

        // Logo depois do binário do PHP, antes do -S.
        $binario = escapeshellarg($php);

        return $binario . $opcoes . substr($comando, strlen($binario));
    }
}
