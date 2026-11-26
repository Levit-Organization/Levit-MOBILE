<?php

use App\Exceptions\AcessoNegadoException;
use App\Libraries\AuthenticatedUser;
use App\Services\LimiteDeAcesso;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class LimiteDeAcessoTest extends CIUnitTestCase
{
    private function usuario(array $permissoes, bool $acessoTotal = false): AuthenticatedUser
    {
        $u = new AuthenticatedUser();
        $u->preencher('u1', 'e1', 'cargo-meu', 'jti', time() + 60, $permissoes, $acessoTotal, []);

        return $u;
    }

    public function testNaoConcedePermissaoQueNaoTem(): void
    {
        $this->expectException(AcessoNegadoException::class);
        (new LimiteDeAcesso($this->usuario(['gerenciar_cargos'])))->exigirPodeConcederPermissoes(['gerenciar_cargos', 'exportar_dados']);
    }

    public function testConcedePermissoesQueJaTem(): void
    {
        (new LimiteDeAcesso($this->usuario(['gerenciar_cargos', 'criar_modulos'])))->exigirPodeConcederPermissoes(['criar_modulos']);
        $this->assertTrue(true);
    }

    public function testAcessoTotalConcedeQualquerCoisa(): void
    {
        (new LimiteDeAcesso($this->usuario([], true)))->exigirPodeConcederPermissoes(['exportar_dados']);
        $this->assertTrue(true);
    }

    public function testNaoAlteraOProprioCargo(): void
    {
        $this->expectException(AcessoNegadoException::class);
        (new LimiteDeAcesso($this->usuario(['gerenciar_cargos'])))->exigirQueNaoSejaOProprioCargo('cargo-meu');
    }

    public function testNaoMexeEmSiMesmoNemComAcessoTotal(): void
    {
        $this->expectException(\DomainException::class);
        (new LimiteDeAcesso($this->usuario([], true)))->exigirQueNaoSejaVoceMesmo('u1', 'não');
    }

    public function testForaDeRequisicaoNaoRestringe(): void
    {
        (new LimiteDeAcesso(new AuthenticatedUser()))->exigirPodeConcederPermissoes(['qualquer']);
        $this->assertTrue(true);
    }
}
