<?php

namespace App\Filters;

use CodeIgniter\Filters\FilterInterface;
use CodeIgniter\HTTP\RequestInterface;
use CodeIgniter\HTTP\ResponseInterface;

/**
 * Comprime em gzip as respostas JSON maiores quando o cliente aceita.
 *
 * Listas de registros e o Kanban são JSON repetitivo: caem de 70% a 90%.
 * Feito no código porque não dependemos de configuração do servidor web; se
 * o servidor (ou o PHP, via zlib.output_compression) já comprimir, este
 * filtro não faz nada.
 */
class CompressaoFilter implements FilterInterface
{
    /** Abaixo disso o ganho não compensa o custo de comprimir. */
    private const TAMANHO_MINIMO = 1024;

    public function before(RequestInterface $request, $arguments = null)
    {
        return null;
    }

    public function after(RequestInterface $request, ResponseInterface $response, $arguments = null)
    {
        $corpo = $response->getBody();

        if (
            ! is_string($corpo)
            || strlen($corpo) < self::TAMANHO_MINIMO
            || ! function_exists('gzencode')
            || filter_var(ini_get('zlib.output_compression'), FILTER_VALIDATE_BOOLEAN)
            || $response->hasHeader('Content-Encoding')
            || ! str_contains($response->getHeaderLine('Content-Type'), 'json')
            || ! preg_match('/\bgzip\b/i', $request->getHeaderLine('Accept-Encoding'))
        ) {
            return $response;
        }

        $comprimido = gzencode($corpo, 5);
        if ($comprimido === false) {
            return $response;
        }

        return $response
            ->setBody($comprimido)
            ->setHeader('Content-Encoding', 'gzip')
            ->appendHeader('Vary', 'Accept-Encoding');
    }
}
