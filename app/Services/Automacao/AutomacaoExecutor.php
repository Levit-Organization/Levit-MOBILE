<?php

namespace App\Services\Automacao;

use App\Exceptions\FalhaPermanenteException;
use App\Services\NotificacaoService;
use App\Services\RegistroService;
use GuzzleHttp\Client;

/**
 * Núcleo das automações, compartilhado entre a execução real (fila) e a
 * simulação (botão "Testar"): avalia as condições, monta cada ação com as
 * variáveis já substituídas e, na execução real, envia.
 *
 * A montagem das ações fica em PreparacaoDeAcoes, o envio em EnvioDeAcoes e
 * o que se sabe da tabela (colunas, pessoas, variáveis) em VariaveisDoModulo.
 */
class AutomacaoExecutor
{
    private VariaveisDoModulo $variaveis;
    private PreparacaoDeAcoes $preparacao;
    private EnvioDeAcoes $envio;

    public function __construct(
        ?Client $http = null,
        private ?NotificacaoService $notificacoes = null,
        private ?RegistroService $registros = null,
        ?DestinoWebhook $destinos = null,
    ) {
        $destinos ??= new DestinoWebhook();

        // Os serviços só são criados quando alguma ação precisa deles.
        $obterNotificacoes = fn (): NotificacaoService => $this->notificacoes ??= new NotificacaoService();
        $obterRegistros    = fn (): RegistroService => $this->registros ??= new RegistroService();

        $this->variaveis  = new VariaveisDoModulo($obterRegistros);
        $this->preparacao = new PreparacaoDeAcoes($this->variaveis, $destinos, $obterNotificacoes);
        $this->envio      = new EnvioDeAcoes($destinos, $obterNotificacoes, $obterRegistros, $http);
    }

    /**
     * `$anteriores`: dados antes da mudança (atualização de registro ou troca
     * de etapa). Os operadores de mudança só são atendidos quando eles existem.
     */
    public function condicaoAtendida(array $automacao, array $dados, ?array $anteriores = null): bool
    {
        return Condicoes::avaliar(Condicoes::daAutomacao($automacao), $dados, $anteriores);
    }

    /**
     * Resolve a ação para o que seria executado de fato.
     *
     * @throws FalhaPermanenteException quando a configuração não permite executar
     */
    public function preparar(array $acao, array $dados, string $moduloId): array
    {
        return $this->preparacao->preparar($acao, $dados, $moduloId);
    }

    /**
     * Executa as ações a partir de `$aPartirDe` e para na primeira falha.
     *
     * `$contexto`: `registro_id` que disparou (ações sobre dados precisam dele) e
     * `cadeia`, as automações que levaram até aqui (proteção contra loop).
     *
     * @return array{status: string, passos: list<array>, falhou_em: ?int, retentavel: bool, erro: ?string, duracao_ms: int}
     */
    public function executar(array $automacao, array $acoes, array $dados, string $gatilho, int $aPartirDe = 0, array $contexto = []): array
    {
        $inicio = hrtime(true);
        $passos = [];

        foreach ($acoes as $indice => $acao) {
            if ($indice < $aPartirDe) {
                continue;
            }

            $inicioPasso = hrtime(true);

            try {
                $preparada = $this->preparar($acao, $dados, $automacao['modulo_id']);
                $mensagem  = $this->envio->enviar($preparada, $gatilho, $automacao['modulo_id'], $contexto);
                $passos[]  = $this->passo($indice, $acao['tipo'], 'sucesso', $mensagem ?? $this->resumo($preparada), $inicioPasso);
            } catch (\Throwable $e) {
                $retentavel = ! $e instanceof FalhaPermanenteException;
                $passos[]   = $this->passo($indice, $acao['tipo'], 'erro', $e->getMessage(), $inicioPasso);

                return [
                    'status'     => 'erro',
                    'passos'     => $passos,
                    'falhou_em'  => $indice,
                    'retentavel' => $retentavel,
                    'erro'       => $e->getMessage(),
                    'duracao_ms' => $this->msDesde($inicio),
                ];
            }
        }

        return [
            'status'     => 'sucesso',
            'passos'     => $passos,
            'falhou_em'  => null,
            'retentavel' => false,
            'erro'       => null,
            'duracao_ms' => $this->msDesde($inicio),
        ];
    }

    /**
     * Mostra o que a automação faria com esses dados, sem enviar nada.
     */
    public function simular(array $automacao, array $acoes, array $dados, ?array $anteriores = null): array
    {
        $condicao = $this->condicaoAtendida($automacao, $dados, $anteriores);
        $passos   = [];

        foreach ($acoes as $indice => $acao) {
            try {
                // Ids internos não vão para a prévia: ela só mostra o que a pessoa precisa ver.
                $previa   = array_diff_key($this->preparar($acao, $dados, $automacao['modulo_id']), array_flip(['usuario_ids', 'empresa_id', 'campo_id', 'modulo_destino_id', 'valores', 'ligar_campo_id']));
                $passos[] = ['indice' => $indice, 'tipo' => $acao['tipo'], 'status' => 'ok', 'previa' => $previa];
            } catch (\Throwable $e) {
                $passos[] = ['indice' => $indice, 'tipo' => $acao['tipo'], 'status' => 'erro', 'mensagem' => $e->getMessage()];
            }
        }

        return ['condicao_atendida' => $condicao, 'passos' => $passos];
    }

    /** Ver EnvioDeAcoes::corpoDoEmail(). */
    public static function corpoDoEmail(string $texto): string
    {
        return EnvioDeAcoes::corpoDoEmail($texto);
    }

    /** Substitui {{Nome do campo}} pelo valor (sem diferenciar maiúsculas). */
    public function substituirVariaveis(string $texto, string $moduloId, array $dados): string
    {
        return $this->variaveis->substituir($texto, $moduloId, $dados);
    }

    private function resumo(array $preparada): string
    {
        return match ($preparada['tipo']) {
            'enviar_email'    => "E-mail enviado para {$preparada['destinatario']}.",
            'notificacao'     => $preparada['destinatarios'] === 1 ? 'Notificação enviada para 1 pessoa.' : "Notificação enviada para {$preparada['destinatarios']} pessoas.",
            'atualizar_campo' => $preparada['valor'] === '' ? "Coluna {$preparada['campo']} esvaziada." : "Coluna {$preparada['campo']} atualizada para \"{$preparada['valor']}\".",
            'criar_registro'  => "Item criado em {$preparada['modulo']}.",
            default           => "Outro sistema avisado: {$preparada['url']}.",
        };
    }

    private function passo(int $indice, string $tipo, string $status, string $mensagem, int $inicio): array
    {
        return ['indice' => $indice, 'tipo' => $tipo, 'status' => $status, 'mensagem' => $mensagem, 'duracao_ms' => $this->msDesde($inicio)];
    }

    private function msDesde(int $inicio): int
    {
        return (int) round((hrtime(true) - $inicio) / 1e6);
    }
}
