<?php

use App\Services\Email\ModeloConvite;
use App\Services\EquipeService;
use CodeIgniter\Test\CIUnitTestCase;

/**
 * @internal
 */
final class ModeloConviteTest extends CIUnitTestCase
{
    private function dados(array $extra = []): array
    {
        return $extra + [
            'convidado_por' => 'Marina Costa',
            'empresa'       => 'Acme',
            'cargo'         => 'Comercial',
            'mensagem'      => null,
            'link'          => 'https://app.levit.com/convite/abc123',
            'dias_validade' => 7,
        ];
    }

    public function testAssuntoNomeiaQuemConvidouEAEquipe(): void
    {
        $this->assertSame('Marina Costa convidou você para a equipe Acme no Levit', ModeloConvite::assunto($this->dados()));
    }

    public function testHtmlTemBotaoComLinkCargoEValidade(): void
    {
        $html = ModeloConvite::html($this->dados());

        $this->assertStringContainsString('href="https://app.levit.com/convite/abc123"', $html);
        $this->assertStringContainsString('Aceitar convite', $html);
        $this->assertMatchesRegularExpression('#Seu cargo</td><td[^>]*>Comercial</td>#', $html);
        $this->assertStringContainsString('vale por 7 dias', $html);
        $this->assertStringContainsString('>MC<', $html, 'iniciais de quem convidou no avatar');
    }

    public function testSemMensagemNaoMostraOBlocoDeMensagem(): void
    {
        $this->assertStringNotContainsString('Mensagem de', ModeloConvite::html($this->dados(['mensagem' => '   '])));
        $this->assertStringContainsString('Mensagem de Marina Costa', ModeloConvite::html($this->dados(['mensagem' => 'Oi'])));
    }

    public function testMensagemEDadosDoUsuarioSaoEscapados(): void
    {
        $html = ModeloConvite::html($this->dados([
            'convidado_por' => '<b>Eve</b>',
            'empresa'       => 'A&B "Ltda"',
            'mensagem'      => "Oi!\n<script>alert(1)</script><a href=\"https://golpe.example\">clique</a>",
        ]));

        $this->assertStringNotContainsString('<script>', $html);
        $this->assertStringNotContainsString('<a href="https://golpe.example"', $html);
        $this->assertStringNotContainsString('<b>Eve</b>', $html);
        $this->assertStringContainsString('&lt;script&gt;', $html);
        $this->assertStringContainsString('A&amp;B &quot;Ltda&quot;', $html);
        $this->assertStringContainsString('Oi!<br />', $html, 'quebras de linha da mensagem viram <br>');
    }

    public function testLinkDoConviteUsaFrontendUrlSemBarraDupla(): void
    {
        putenv('FRONTEND_URL=https://app.levit.com/');
        $_ENV['FRONTEND_URL'] = 'https://app.levit.com/';

        try {
            $this->assertSame('https://app.levit.com/convite/tok', EquipeService::linkDoConvite('tok'));
        } finally {
            putenv('FRONTEND_URL');
            unset($_ENV['FRONTEND_URL']);
        }
    }
}
