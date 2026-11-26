<?php

namespace App\Services\Automacao;

use App\Exceptions\FalhaPermanenteException;

/**
 * Proteção contra SSRF nos webhooks: a URL é configurada pelo cliente, mas a
 * chamada sai do nosso servidor. Sem isso, um webhook para
 * http://169.254.169.254/ ou http://localhost:5432 faria o servidor ler a rede
 * interna e devolver o resultado no histórico.
 *
 * Duas etapas:
 * - `validarFormato()` ao salvar a automação: esquema, porta, sem usuário/senha
 *   na URL e sem host interno escrito direto (localhost, IP privado).
 * - `resolver()` na hora de chamar: resolve o DNS e exige que TODOS os IPs sejam
 *   públicos. O IP aprovado é fixado na conexão (CURLOPT_RESOLVE), então o host
 *   não pode trocar de IP entre a checagem e a chamada (DNS rebinding).
 */
class DestinoWebhook
{
    private const PORTAS_PERMITIDAS = [80, 443, 8080, 8443];

    private const HOSTS_INTERNOS = ['localhost', 'localhost.localdomain', 'ip6-localhost', 'metadata.google.internal'];

    /** @var callable(string): string[] */
    private $resolverDns;

    public function __construct(?callable $resolverDns = null)
    {
        $this->resolverDns = $resolverDns ?? [self::class, 'consultarDns'];
    }

    /**
     * @return array{esquema: string, host: string, porta: int}
     *
     * @throws \DomainException
     */
    public function validarFormato(string $url): array
    {
        $partes = parse_url(trim($url));
        $esquema = strtolower($partes['scheme'] ?? '');
        $host    = strtolower(trim($partes['host'] ?? '', '[]'));

        if (! filter_var($url, FILTER_VALIDATE_URL) || ! in_array($esquema, ['http', 'https'], true) || $host === '') {
            throw new \DomainException('Informe um endereço completo, começando com https://.');
        }
        if (isset($partes['user']) || isset($partes['pass'])) {
            throw new \DomainException('O endereço não pode ter usuário e senha. Use um token no próprio endereço.');
        }

        $porta = (int) ($partes['port'] ?? ($esquema === 'https' ? 443 : 80));
        if (! in_array($porta, self::PORTAS_PERMITIDAS, true)) {
            throw new \DomainException('Use uma destas portas: 80, 443, 8080 ou 8443.');
        }

        if (in_array($host, self::HOSTS_INTERNOS, true) || str_ends_with($host, '.localhost')
            || str_ends_with($host, '.internal') || str_ends_with($host, '.local')
            || (filter_var($host, FILTER_VALIDATE_IP) && ! self::ipPublico($host))) {
            throw new \DomainException('Esse endereço aponta para uma rede interna e não pode ser usado.');
        }

        return ['esquema' => $esquema, 'host' => $host, 'porta' => $porta];
    }

    /**
     * Valida e resolve. Devolve a entrada para CURLOPT_RESOLVE ("host:porta:ip").
     *
     * @throws FalhaPermanenteException
     */
    public function resolver(string $url): string
    {
        try {
            ['host' => $host, 'porta' => $porta] = $this->validarFormato($url);
        } catch (\DomainException $e) {
            throw new FalhaPermanenteException($e->getMessage(), 0, $e);
        }

        $ips = filter_var($host, FILTER_VALIDATE_IP) ? [$host] : ($this->resolverDns)($host);

        if ($ips === []) {
            throw new FalhaPermanenteException("Não foi possível encontrar o endereço {$host}.");
        }
        foreach ($ips as $ip) {
            if (! self::ipPublico($ip)) {
                throw new FalhaPermanenteException('Esse endereço aponta para uma rede interna e não pode ser usado.');
            }
        }

        $ip = $ips[0];

        return "{$host}:{$porta}:" . (str_contains($ip, ':') ? "[{$ip}]" : $ip);
    }

    public static function ipPublico(string $ip): bool
    {
        // IPv4 mapeado em IPv6 (::ffff:10.0.0.1) é avaliado como o IPv4 que carrega.
        if (preg_match('/^::ffff:(\d+\.\d+\.\d+\.\d+)$/i', $ip, $m)) {
            $ip = $m[1];
        }

        if (! filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE)) {
            return false;
        }

        // Faixas que o filtro do PHP não cobre: CGNAT, benchmark e multicast.
        foreach (['100.64.0.0/10', '198.18.0.0/15', '224.0.0.0/4'] as $faixa) {
            if (self::naFaixa($ip, $faixa)) {
                return false;
            }
        }

        return true;
    }

    private static function naFaixa(string $ip, string $faixa): bool
    {
        if (! filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV4)) {
            return false;
        }
        [$rede, $bits] = explode('/', $faixa);
        $mascara       = -1 << (32 - (int) $bits);

        return (ip2long($ip) & $mascara) === (ip2long($rede) & $mascara);
    }

    /** @return string[] */
    private static function consultarDns(string $host): array
    {
        $registros = @dns_get_record($host, DNS_A | DNS_AAAA) ?: [];

        return array_values(array_filter(array_map(
            static fn (array $r) => $r['ip'] ?? $r['ipv6'] ?? null,
            $registros
        )));
    }
}
