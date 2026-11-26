<?php

use App\Services\EquipeService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Formato em que a API devolve um convite (sem banco).
 *
 * @internal
 */
final class ConviteResumoTest extends CIUnitTestCase
{
    private function resumo(array $convite): array
    {
        $convites = (new ReflectionProperty(EquipeService::class, 'convites'))->getValue(new EquipeService());

        return (new ReflectionMethod($convites, 'resumoDoConvite'))->invoke($convites, $convite);
    }

    private function convite(array $extra = []): array
    {
        return $extra + [
            'id'                 => 'conv-1',
            'email_destinatario' => 'ana@exemplo.com',
            'cargo_id'           => 'cargo-1',
            'token_hash'         => str_repeat('a', 64),
            'expira_em'          => date('Y-m-d H:i:s', time() + 3600),
        ];
    }

    public function testNuncaDevolveOTokenNemOHash(): void
    {
        $resumo = $this->resumo($this->convite(['cargo_nome' => 'Vendas', 'convidado_por_nome' => 'Bia', 'mensagem' => 'Bem-vinda', 'criado_em' => '2026-01-01 10:00:00']));

        $this->assertSame([
            'id'                 => 'conv-1',
            'email'              => 'ana@exemplo.com',
            'cargo_id'           => 'cargo-1',
            'cargo_nome'         => 'Vendas',
            'convidado_por_nome' => 'Bia',
            'mensagem'           => 'Bem-vinda',
            'criado_em'          => '2026-01-01 10:00:00',
            'expira_em'          => $resumo['expira_em'],
            'expirado'           => false,
        ], $resumo);
        $this->assertArrayNotHasKey('token_hash', $resumo);
    }

    public function testCamposOpcionaisAusentesViramNulos(): void
    {
        $resumo = $this->resumo($this->convite());

        $this->assertNull($resumo['cargo_nome']);
        $this->assertNull($resumo['convidado_por_nome']);
        $this->assertNull($resumo['mensagem']);
        $this->assertNull($resumo['criado_em']);
    }

    public function testConviteVencidoFicaMarcado(): void
    {
        $this->assertTrue($this->resumo($this->convite(['expira_em' => date('Y-m-d H:i:s', time() - 60)]))['expirado']);
    }
}
