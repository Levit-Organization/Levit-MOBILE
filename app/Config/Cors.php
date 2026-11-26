<?php

namespace Config;

use CodeIgniter\Config\BaseConfig;

/**
 * Cross-Origin Resource Sharing (CORS) Configuration
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
 */
class Cors extends BaseConfig
{
    /**
     * The default CORS configuration.
     *
     * @var array{
     *      allowedOrigins: list<string>,
     *      allowedOriginsPatterns: list<string>,
     *      supportsCredentials: bool,
     *      allowedHeaders: list<string>,
     *      exposedHeaders: list<string>,
     *      allowedMethods: list<string>,
     *      maxAge: int,
     *  }
     */
    public array $default = [
        /**
         * Origins for the `Access-Control-Allow-Origin` header.
         *
         * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Allow-Origin
         *
         * E.g.:
         *   - ['http://localhost:8080']
         *   - ['https://www.example.com']
         */
        'allowedOrigins' => ['http://localhost:5173'],
        'allowedOriginsPatterns' => [],
        'supportsCredentials' => true,
        'allowedHeaders' => ['Content-Type', 'Authorization', 'X-Requested-With', 'Accept', 'Origin', 'X-CSRF-Token', 'X-Levit-Sessao'],
        'exposedHeaders' => [],
        'allowedMethods' => ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'PATCH'],
        'maxAge' => 7200,
    ];

    /**
     * A origem do front vem do .env (FRONTEND_URL, aceita várias separadas por
     * vírgula), para o mesmo código servir em produção sem editar este arquivo.
     * Em desenvolvimento, o Vite local continua liberado.
     */
    public function __construct()
    {
        parent::__construct();

        $origens = array_filter(array_map(
            static fn ($o) => rtrim(trim($o), '/'),
            explode(',', (string) env('FRONTEND_URL', ''))
        ));
        if (ENVIRONMENT !== 'production' || $origens === []) {
            $origens[] = 'http://localhost:5173';
        }

        $this->default['allowedOrigins'] = array_values(array_unique($origens));
    }
}
