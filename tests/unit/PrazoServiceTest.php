<?php

use App\Services\PrazoService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class PrazoServiceTest extends CIUnitTestCase
{
    public function testOpcoesDeConclusaoIgnoramAcentoEMaiusculas(): void
    {
        foreach (['Concluído', 'ENTREGUE', 'Pago', ' finalizada ', 'Publicado', 'Cancelado'] as $opcao) {
            $this->assertTrue(PrazoService::opcaoDeConclusao($opcao), $opcao);
        }
    }

    public function testOpcoesEmAndamentoNaoConcluem(): void
    {
        foreach (['A fazer', 'Em andamento', 'Aguardando cliente', 'Aprovação do cliente', '', null] as $opcao) {
            $this->assertFalse(PrazoService::opcaoDeConclusao($opcao), (string) $opcao);
        }
    }
}
