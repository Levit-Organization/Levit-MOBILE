<?php

use App\Services\Automacao\ListagemDeAutomacoes;
use App\Services\Automacao\ValidadorDeAutomacao;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * Cadastro de automações nas partes que não consultam o banco: validação de
 * nome e de ações, e o formato que a listagem devolve para a tela.
 *
 * @internal
 */
final class AutomacaoValidacaoTest extends CIUnitTestCase
{
    /** Validador com o tipo da tabela fixo (sem consultar o banco). */
    private function validador(bool $ehVaga = false): ValidadorDeAutomacao
    {
        return new class ($ehVaga) extends ValidadorDeAutomacao {
            public function __construct(private bool $ehVaga)
            {
                parent::__construct();
            }

            public function tipoDoModulo(string $moduloId): ?string
            {
                return $this->ehVaga ? 'recrutamento' : 'dados';
            }
        };
    }

    private function validarAcao(array $acao, bool $ehVaga = false, array $gatilhos = ['criacao']): void
    {
        $definicao = $this->validador($ehVaga)->validarDefinicao('modulo', 'empresa', ['nome' => 'Teste', 'gatilhos' => $gatilhos, 'acoes' => [$acao]]);
        $this->assertSame([$acao], $definicao['acoes']);
    }

    private function assertAcaoRecusada(string $mensagem, array $acao, bool $ehVaga = false, array $gatilhos = ['criacao']): void
    {
        try {
            $this->validarAcao($acao, $ehVaga, $gatilhos);
            $this->fail('Deveria recusar: ' . json_encode($acao));
        } catch (DomainException $e) {
            $this->assertSame($mensagem, $e->getMessage());
        }
    }

    public function testNomeDaAutomacao(): void
    {
        foreach (['', ' a ', str_repeat('x', 101)] as $nome) {
            try {
                (new ValidadorDeAutomacao())->validarDefinicao('modulo', 'empresa', ['nome' => $nome]);
                $this->fail("Deveria recusar o nome '{$nome}'");
            } catch (DomainException $e) {
                $this->assertSame('O nome precisa ter entre 2 e 100 caracteres.', $e->getMessage());
            }
        }
    }

    public function testGatilhosConformeOTipoDaTabelaEAcoesObrigatorias(): void
    {
        $casos = [
            ['Gatilhos de candidato só existem em vagas de recrutamento.', false, ['criacao', 'candidato_criado'], [['tipo' => 'webhook']]],
            ['Em vagas, as automações disparam com candidatos (novo candidato ou mudança de etapa).', true, ['criacao'], [['tipo' => 'webhook']]],
            ['A automação precisa de pelo menos uma ação.', false, ['criacao'], []],
        ];
        foreach ($casos as [$mensagem, $ehVaga, $gatilhos, $acoes]) {
            try {
                $this->validador($ehVaga)->validarDefinicao('modulo', 'empresa', ['nome' => 'Teste', 'gatilhos' => $gatilhos, 'acoes' => $acoes]);
                $this->fail("Deveria recusar: {$mensagem}");
            } catch (DomainException $e) {
                $this->assertSame($mensagem, $e->getMessage());
            }
        }

        $definicao = $this->validador(true)->validarDefinicao('vaga', 'empresa', [
            'nome' => '  Avisar a equipe ', 'gatilho' => 'candidato_mudou_etapa',
            'acoes' => [['tipo' => 'notificacao', 'configuracao' => ['destinatarios' => 'todos', 'titulo' => 'Mudou']]],
        ]);
        $this->assertSame([
            'nome' => 'Avisar a equipe', 'gatilho' => 'candidato_mudou_etapa', 'gatilhos' => ['candidato_mudou_etapa'], 'condicoes' => null,
            'campo_condicao_id' => null, 'condicao_operador' => null, 'condicao_valor' => null, 'condicao_valor_de' => null,
        ], $definicao['campos']);
    }

    public function testAcoesValidasSemBanco(): void
    {
        $this->validarAcao(['tipo' => 'enviar_email', 'configuracao' => ['destinatario' => 'a@b.com', 'assunto' => 'Oi', 'corpo' => 'Texto']]);
        $this->validarAcao(['tipo' => 'enviar_email', 'configuracao' => ['destinatario_campo_id' => 'c1', 'assunto' => 'Oi', 'corpo' => 'Texto']]);
        $this->validarAcao(['tipo' => 'webhook', 'configuracao' => ['url' => 'https://exemplo.com.br/gancho']]);
        $this->validarAcao(['tipo' => 'notificacao', 'configuracao' => ['destinatarios' => 'todos', 'titulo' => 'Novo item']], true, ['candidato_criado']);
    }

    public function testAcoesRecusadas(): void
    {
        $this->assertAcaoRecusada("Tipo de ação inválido: 'sms'.", ['tipo' => 'sms']);
        $this->assertAcaoRecusada('Ação de e-mail precisa de destinatário, assunto e corpo.', ['tipo' => 'enviar_email', 'configuracao' => ['assunto' => 'Oi', 'corpo' => 'x']]);
        $this->assertAcaoRecusada('Ação de e-mail precisa de destinatário, assunto e corpo.', ['tipo' => 'enviar_email', 'configuracao' => ['destinatario' => 'a@b.com', 'corpo' => 'x']]);
        $this->assertAcaoRecusada('Informe o endereço do outro sistema.', ['tipo' => 'webhook', 'configuracao' => []]);
        $this->assertAcaoRecusada('Esse endereço aponta para uma rede interna e não pode ser usado.', ['tipo' => 'webhook', 'configuracao' => ['url' => 'http://localhost/x']]);
        $this->assertAcaoRecusada('Ações sobre itens não estão disponíveis em vagas.', ['tipo' => 'atualizar_campo', 'configuracao' => ['campo_id' => 'c1']], true, ['candidato_criado']);
        $this->assertAcaoRecusada('Ações sobre itens não estão disponíveis em vagas.', ['tipo' => 'criar_registro', 'configuracao' => []], true, ['candidato_criado']);
        $this->assertAcaoRecusada(
            '"Atualizar coluna" não funciona com o gatilho "Exclusão": o item já não existe.',
            ['tipo' => 'atualizar_campo', 'configuracao' => ['campo_id' => 'c1']],
            false,
            ['criacao', 'exclusao']
        );
    }

    public function testNotificacaoRecusada(): void
    {
        $this->assertAcaoRecusada('Escolha quem recebe a notificação.', ['tipo' => 'notificacao', 'configuracao' => ['destinatarios' => 'chefe', 'titulo' => 'x']]);
        $this->assertAcaoRecusada('A notificação precisa de um título.', ['tipo' => 'notificacao', 'configuracao' => ['destinatarios' => 'todos', 'titulo' => '  ']]);
        $this->assertAcaoRecusada(
            'O título da notificação pode ter no máximo 200 caracteres.',
            ['tipo' => 'notificacao', 'configuracao' => ['destinatarios' => 'todos', 'titulo' => str_repeat('á', 201)]]
        );
    }

    public function testListagemNoFormatoDaTela(): void
    {
        $lista = ListagemDeAutomacoes::completar([
            [
                'id' => 'a1', 'gatilho' => 'criacao', 'gatilhos' => null, 'condicoes' => null,
                'campo_condicao_id' => 'c1', 'condicao_operador' => 'igual', 'condicao_valor' => 'Feito',
                'acoes_json'   => '[{"tipo":"webhook","configuracao":"{\"url\":\"https://x.com\"}"},{"tipo":"notificacao","configuracao":{"titulo":"Oi"}}]',
                'execucoes_7d' => '5', 'falhas_7d' => '1', 'ultima_execucao_em' => '2026-10-10 10:00:00',
            ],
            ['id' => 'a2', 'gatilho' => 'criacao', 'gatilhos' => ['atualizacao'], 'condicoes' => ['logica' => 'ou', 'regras' => [['campo' => 'x']]], 'acoes_json' => null],
        ]);

        $this->assertSame(['criacao'], $lista[0]['gatilhos']);
        $this->assertSame(['logica' => 'e', 'regras' => [['campo' => 'c1', 'operador' => 'igual', 'valor' => 'Feito', 'valor_de' => null]]], $lista[0]['condicoes']);
        $this->assertSame([['tipo' => 'webhook', 'configuracao' => ['url' => 'https://x.com']], ['tipo' => 'notificacao', 'configuracao' => ['titulo' => 'Oi']]], $lista[0]['acoes']);
        $this->assertSame(['execucoes_7d' => 5, 'falhas_7d' => 1, 'ultima_execucao_em' => '2026-10-10 10:00:00'], $lista[0]['estatisticas']);
        $this->assertArrayNotHasKey('acoes_json', $lista[0]);
        $this->assertArrayNotHasKey('execucoes_7d', $lista[0]);

        $this->assertSame(['atualizacao'], $lista[1]['gatilhos']);
        $this->assertSame(['logica' => 'ou', 'regras' => [['campo' => 'x']]], $lista[1]['condicoes']);
        $this->assertSame([], $lista[1]['acoes']);
        $this->assertSame(['execucoes_7d' => 0, 'falhas_7d' => 0, 'ultima_execucao_em' => null], $lista[1]['estatisticas']);
    }
}
