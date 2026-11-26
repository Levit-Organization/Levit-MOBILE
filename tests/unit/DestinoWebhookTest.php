<?php

use App\Exceptions\FalhaPermanenteException;
use App\Services\Automacao\DestinoWebhook;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class DestinoWebhookTest extends CIUnitTestCase
{
    private function destino(array $ips = ['93.184.216.34']): DestinoWebhook
    {
        return new DestinoWebhook(static fn () => $ips);
    }

    public function testAceitaEnderecoPublico(): void
    {
        $this->assertSame('exemplo.com:443:93.184.216.34', $this->destino()->resolver('https://exemplo.com/hook?t=1'));
        $this->assertSame('exemplo.com:8080:93.184.216.34', $this->destino()->resolver('http://exemplo.com:8080/x'));
    }

    /**
     * @dataProvider enderecosRecusadosAoSalvar
     */
    public function testRecusaAoSalvar(string $url): void
    {
        $this->expectException(\DomainException::class);
        $this->destino()->validarFormato($url);
    }

    public static function enderecosRecusadosAoSalvar(): array
    {
        return [
            'sem esquema'        => ['exemplo.com/hook'],
            'ftp'                => ['ftp://exemplo.com/a'],
            'file'               => ['file:///etc/passwd'],
            'localhost'          => ['http://localhost/admin'],
            'subdomínio local'   => ['http://api.localhost/'],
            'loopback'           => ['http://127.0.0.1/'],
            'rede privada'       => ['http://10.0.0.5/'],
            'metadados da nuvem' => ['http://169.254.169.254/latest/meta-data'],
            'IPv6 loopback'      => ['http://[::1]/'],
            'IPv4 em IPv6'       => ['http://[::ffff:127.0.0.1]/'],
            'porta do banco'     => ['http://exemplo.com:5432/'],
            'usuário na URL'     => ['https://admin:senha@exemplo.com/'],
            'CGNAT'              => ['http://100.64.1.1/'],
        ];
    }

    public function testRecusaQuandoODnsApontaParaRedeInterna(): void
    {
        $this->expectException(FalhaPermanenteException::class);
        $this->destino(['192.168.0.10'])->resolver('https://parece-publico.com/hook');
    }

    public function testRecusaSeQualquerIpResolvidoForInterno(): void
    {
        $this->expectException(FalhaPermanenteException::class);
        $this->destino(['93.184.216.34', '127.0.0.1'])->resolver('https://exemplo.com/');
    }

    public function testRecusaHostQueNaoResolve(): void
    {
        $this->expectException(FalhaPermanenteException::class);
        $this->destino([])->resolver('https://nao-existe.exemplo/');
    }

    public function testFixaIpv6EntreColchetes(): void
    {
        $this->assertSame(
            'exemplo.com:443:[2606:2800:220:1:248:1893:25c8:1946]',
            $this->destino(['2606:2800:220:1:248:1893:25c8:1946'])->resolver('https://exemplo.com/')
        );
    }
}
